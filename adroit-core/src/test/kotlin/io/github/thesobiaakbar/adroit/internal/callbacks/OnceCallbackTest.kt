package io.github.thesobiaakbar.adroit.internal.callbacks

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
class OnceCallbackTest {

    @Test
    fun `concurrent invokes deliver exactly once`() {
        repeat(10_000) { iteration ->
            val deliveries = AtomicInteger()
            val once = OnceCallback<Int>(callback = { deliveries.incrementAndGet() }, dispatch = { it() })
            val start = CountDownLatch(1)
            val wins = AtomicInteger()
            val threads = List(2) { value ->
                thread {
                    start.await()
                    if (once.invoke(value)) wins.incrementAndGet()
                }
            }
            start.countDown()
            threads.forEach { it.join() }
            assertEquals("iteration $iteration", 1, wins.get())
            assertEquals("iteration $iteration", 1, deliveries.get())
        }
    }

    @Test
    fun `default dispatch posts to the main thread and never runs synchronously`() {
        var receivedOn: Thread? = null
        var received: String? = null
        val once = OnceCallback<String>({ receivedOn = Thread.currentThread(); received = it })

        assertTrue(once.invoke("first"))
        assertEquals("callback must not run synchronously", null, received)

        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("first", received)
        assertEquals(Looper.getMainLooper().thread, receivedOn)
    }

    @Test
    fun `later invokes are ignored`() {
        val values = mutableListOf<String>()
        val once = OnceCallback<String>(callback = { values += it }, dispatch = { it() })
        assertTrue(once.invoke("a"))
        assertFalse(once.invoke("b"))
        assertTrue(once.isInvoked)
        assertEquals(listOf("a"), values)
    }
}
