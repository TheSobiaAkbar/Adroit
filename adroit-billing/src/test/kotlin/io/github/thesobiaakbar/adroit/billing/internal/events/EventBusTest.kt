package io.github.thesobiaakbar.adroit.billing.internal.events

import android.os.Looper
import io.github.thesobiaakbar.adroit.billing.BillingEvent
import io.github.thesobiaakbar.adroit.billing.BillingEventListener
import io.github.thesobiaakbar.adroit.billing.BillingLogLevel
import io.github.thesobiaakbar.adroit.billing.internal.BillingLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
class EventBusTest {
    private val logged = mutableListOf<String>()
    private val log = BillingLog(BillingLogLevel.VERBOSE) { _, message -> logged += message }
    private val received = mutableListOf<Pair<BillingEvent, Thread>>()
    private val recorder = BillingEventListener { received += it to Thread.currentThread() }

    private class Thrower : BillingEventListener {
        override fun onEvent(event: BillingEvent): Unit = error("listener bug")
    }

    @Test
    fun `a throwing listener is logged once per class and the others still get every event`() {
        val bus = EventBus(listOf(Thrower(), recorder, Thrower()), log)
        repeat(3) { bus.emit(BillingEvent.RestoreInitiated(it.toLong())) }
        assertEquals(listOf(0L, 1L, 2L), received.map { it.first.timestampMillis })
        assertEquals(1, logged.size)
        assertTrue(logged.single(), logged.single().contains(Thrower::class.java.name))
    }

    @Test
    fun `events from another thread reach listeners on the main thread, in order`() {
        val bus = EventBus(listOf(recorder), log)
        thread { repeat(3) { bus.emit(BillingEvent.RestoreInitiated(it.toLong())) } }.join()
        assertEquals(emptyList<Any>(), received)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(0L, 1L, 2L), received.map { it.first.timestampMillis })
        assertTrue(received.all { it.second == Looper.getMainLooper().thread })
    }

    @Test
    fun `listeners can be added and removed at any time, once each`() {
        val bus = EventBus(emptyList(), log)
        bus.add(recorder)
        bus.add(recorder)
        bus.emit(BillingEvent.RestoreInitiated(1))
        bus.remove(recorder)
        bus.emit(BillingEvent.RestoreInitiated(2))
        assertEquals(listOf(1L), received.map { it.first.timestampMillis })
    }
}
