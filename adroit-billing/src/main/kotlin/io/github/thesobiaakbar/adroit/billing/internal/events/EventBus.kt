package io.github.thesobiaakbar.adroit.billing.internal.events

import io.github.thesobiaakbar.adroit.billing.BillingEvent
import io.github.thesobiaakbar.adroit.billing.BillingEventListener
import io.github.thesobiaakbar.adroit.billing.BillingLogLevel
import io.github.thesobiaakbar.adroit.billing.internal.BillingLog

/** Hands each event to every listener; a listener that throws is logged and never breaks billing. B10 completes it. */
internal class EventBus(private val listeners: List<BillingEventListener>, private val log: BillingLog) {
    fun emit(event: BillingEvent) {
        for (listener in listeners) {
            try {
                listener.onEvent(event)
            } catch (e: Exception) {
                log.log(BillingLogLevel.ERROR, "Event listener ${listener::class.java.name} threw $e")
            }
        }
    }
}
