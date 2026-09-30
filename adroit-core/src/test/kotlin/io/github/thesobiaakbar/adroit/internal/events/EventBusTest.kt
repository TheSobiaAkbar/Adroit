package io.github.thesobiaakbar.adroit.internal.events

import android.os.Looper
import app.cash.turbine.test
import io.github.thesobiaakbar.adroit.AdsState
import io.github.thesobiaakbar.adroit.events.AdEvent
import io.github.thesobiaakbar.adroit.events.AdEventListener
import io.github.thesobiaakbar.adroit.internal.logging.AdLog
import io.github.thesobiaakbar.adroit.logging.LogLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.concurrent.thread

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EventBusTest {
    private val errors = mutableListOf<String>()
    private val log = AdLog({ level, _, message, _ -> if (level == LogLevel.ERROR) errors += message }, LogLevel.WARN)

    private fun event(i: Int) = AdEvent.ConfigurationWarning("warning $i", timestampMillis = i.toLong())

    @Test
    fun `a failing listener does not affect the others`() {
        val received = mutableListOf<AdEvent>()
        val bus = EventBus(
            log,
            listOf(
                AdEventListener { throw IllegalStateException("boom") },
                AdEventListener { received += it },
            ),
        )
        val e = event(1)
        bus.emit(e)
        assertEquals(listOf(e), received)
        assertEquals(1, errors.size)
    }

    @Test
    fun `listeners are called in registration order`() {
        val order = mutableListOf<String>()
        val bus = EventBus(log, listOf(AdEventListener { order += "a" }, AdEventListener { order += "b" }))
        bus.emit(event(1))
        assertEquals(listOf("a", "b"), order)
    }

    @Test
    fun `recent events are capped at the capacity, oldest dropped`() {
        val bus = EventBus(log)
        repeat(150) { bus.emit(event(it)) }
        val recent = bus.recent()
        assertEquals(100, recent.size)
        assertEquals(50L, recent.first().timestampMillis)
        assertEquals(149L, recent.last().timestampMillis)
    }

    @Test
    fun `recent events are not recorded when disabled`() {
        val bus = EventBus(log).apply { recordRecent = false }
        bus.emit(event(1))
        assertTrue(bus.recent().isEmpty())
    }

    @Test
    fun `flow collectors receive events in order`() = runTest {
        val bus = EventBus(log)
        bus.events.test {
            bus.emit(event(1))
            bus.emit(event(2))
            assertEquals(1L, awaitItem().timestampMillis)
            assertEquals(2L, awaitItem().timestampMillis)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emit never blocks on a slow collector and drops the oldest events`() = runTest {
        val bus = EventBus(log)
        val received = mutableListOf<AdEvent>()
        val collector = launch {
            bus.events.collect {
                received += it
                delay(1_000) // A slow collector.
            }
        }
        runCurrent() // Subscribe.

        // All 200 emits return immediately even though the collector is stuck.
        repeat(200) { bus.emit(event(it)) }
        advanceUntilIdle()

        // Buffer (64) plus the one value handed to the suspended collector; the rest were dropped oldest-first.
        assertTrue("received ${received.size}", received.size in 2..65)
        assertEquals(199L, received.last().timestampMillis)
        collector.cancel()
    }

    @Test
    fun `off-main emits are delivered on the main thread`() {
        var deliveredOn: Thread? = null
        val bus = EventBus(log, listOf(AdEventListener { deliveredOn = Thread.currentThread() }))
        val e = AdEvent.StateChanged(AdsState.Ready, 1)
        thread { bus.emit(e) }.join()
        shadowOf(Looper.getMainLooper()).idle()
        assertSame(Looper.getMainLooper().thread, deliveredOn)
    }
}
