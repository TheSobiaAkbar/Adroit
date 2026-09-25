package com.company.admob.internal.reliability

import com.company.admob.internal.callbacks.OnceCallback
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ShowSessionTest {
    private val results: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private var timeouts = 0

    // Direct dispatch: this test is about exactly-once delivery, not about posting to the main thread.
    private fun TestScope.session() = ShowSession(
        onResult = OnceCallback<String>({ results += it }) { it() },
        scope = backgroundScope,
        watchdogTimeout = 5.seconds,
        timeoutResult = { "timeout" },
        onTimeout = { timeouts++ },
    )

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `the first resolve wins, later ones are ignored`() = runTest {
        val session = session()
        assertFalse(session.isResolved)
        assertTrue(session.resolve("dismissed"))
        assertFalse(session.resolve("failed"))
        assertTrue(session.isResolved)
        assertEquals(listOf("dismissed"), results)
    }

    @Test
    fun `resolve races from many threads deliver exactly once`() = runTest {
        val session = session()
        val go = CountDownLatch(1)
        val done = CountDownLatch(8)
        val wins = AtomicInteger()
        repeat(8) { i ->
            Thread {
                go.await()
                if (session.resolve("r$i")) wins.incrementAndGet()
                done.countDown()
            }.start()
        }
        go.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertEquals(1, wins.get())
        assertEquals(1, results.size)
    }

    @Test
    fun `the watchdog resolves with the timeout result and runs cleanup once`() = runTest {
        val session = session()
        advance(4_999)
        assertEquals(emptyList<String>(), results)

        advance(1)
        assertEquals(listOf("timeout"), results)
        assertEquals(1, timeouts)
        assertFalse("a late callback is ignored", session.resolve("dismissed"))
        assertEquals(listOf("timeout"), results)
    }

    @Test
    fun `markShown cancels the watchdog, and the result comes on dismiss`() = runTest {
        val session = session()
        advance(1_000)
        session.markShown()
        assertTrue(session.isShown)

        advance(60_000)
        assertEquals(emptyList<String>(), results)
        assertTrue(session.resolve("dismissed"))
        assertEquals(listOf("dismissed"), results)
        assertEquals(0, timeouts)
    }

    @Test
    fun `resolving before the timeout means no timeout cleanup`() = runTest {
        val session = session()
        assertFalse(session.isShown)
        session.resolve("failed")
        advance(10_000)
        assertEquals(listOf("failed"), results)
        assertEquals(0, timeouts)
    }

    @Test
    fun `the watchdog skips cleanup when the result was delivered through the callback directly`() = runTest {
        val callback = OnceCallback<String>({ results += it }) { it() }
        ShowSession(callback, backgroundScope, 5.seconds, timeoutResult = { "timeout" })
        callback("dismissed")
        advance(10_000)
        assertEquals(listOf("dismissed"), results)
        assertEquals(0, timeouts)
    }
}
