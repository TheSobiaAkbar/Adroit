package io.github.thesobiaakbar.adroit.billing.internal.events

import android.os.Handler
import android.os.Looper
import io.github.thesobiaakbar.adroit.billing.BillingEvent
import io.github.thesobiaakbar.adroit.billing.BillingEventListener
import io.github.thesobiaakbar.adroit.billing.BillingLogLevel
import io.github.thesobiaakbar.adroit.billing.internal.BillingLog
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Hands each event to every listener on the main thread, in emission order. A listener that throws is logged once per
 * listener class and never breaks billing. Listeners can be added and removed at any time, from any thread.
 */
internal class EventBus(listeners: List<BillingEventListener>, private val log: BillingLog) {
    private val listeners = CopyOnWriteArrayList(listeners)
    private val reported = ConcurrentHashMap.newKeySet<String>()
    private val main by lazy { Handler(Looper.getMainLooper()) }

    fun add(listener: BillingEventListener) {
        listeners.addIfAbsent(listener)
    }

    fun remove(listener: BillingEventListener) {
        listeners.remove(listener)
    }

    fun clear() {
        listeners.clear()
        reported.clear()
    }

    fun emit(event: BillingEvent) {
        if (listeners.isEmpty()) return
        if (Looper.myLooper() == Looper.getMainLooper()) deliver(event) else main.post { deliver(event) }
    }

    private fun deliver(event: BillingEvent) {
        for (listener in listeners) {
            try {
                listener.onEvent(event)
            } catch (e: Exception) {
                val name = listener::class.java.name
                if (reported.add(name)) log.log(BillingLogLevel.ERROR, "Event listener $name threw $e; later throws aren't logged")
            }
        }
    }
}
