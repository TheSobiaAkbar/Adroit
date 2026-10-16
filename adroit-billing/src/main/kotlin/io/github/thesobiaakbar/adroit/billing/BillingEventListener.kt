package io.github.thesobiaakbar.adroit.billing

/**
 * Receives every [BillingEvent] on the main thread. A listener that throws is logged (once per listener class) and
 * never breaks billing.
 */
public fun interface BillingEventListener {
    /** Called for each event. */
    public fun onEvent(event: BillingEvent)
}
