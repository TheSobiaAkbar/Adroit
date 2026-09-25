package com.company.admob.events

/**
 * Receives every [AdEvent], for example to forward them to an analytics tool.
 *
 * Called synchronously on the main thread, in registration order. Keep it fast; hand heavy work off to another
 * thread. Exceptions thrown here are caught and logged, and don't affect other listeners or the ads.
 */
public fun interface AdEventListener {
    /** Called for each event. */
    public fun onEvent(event: AdEvent)
}
