package com.company.admob.internal.events

import com.company.admob.events.AdEvent
import com.company.admob.events.AdEventListener
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.threading.isMainThread
import com.company.admob.internal.threading.postToMain
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Fans every [AdEvent] out to the public flow, the host's [AdEventListener]s, and a ring buffer for diagnostics.
 *
 * Emission never suspends or blocks: a slow flow collector loses the oldest events instead. Listeners run on the
 * main thread in registration order; one failing listener doesn't affect the others.
 */
internal class EventBus(
    private val log: AdLog,
    listeners: List<AdEventListener> = emptyList(),
    private val recentCapacity: Int = DEFAULT_RECENT_CAPACITY,
) {
    private val flow = MutableSharedFlow<AdEvent>(
        replay = 0,
        extraBufferCapacity = FLOW_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<AdEvent> = flow.asSharedFlow()

    @Volatile
    private var listeners: List<AdEventListener> = listeners.toList()

    /** Whether [recent] records events. Turned off in production so the library doesn't hold event data. */
    @Volatile
    var recordRecent: Boolean = true

    private val recent = ArrayDeque<AdEvent>(recentCapacity)

    fun setListeners(listeners: List<AdEventListener>) {
        this.listeners = listeners.toList()
    }

    /** Emits [event]. Delivery happens on the main thread; off-main calls are posted to keep ordering. */
    fun emit(event: AdEvent) {
        if (isMainThread()) deliver(event) else postToMain { deliver(event) }
    }

    /** The last [recentCapacity] recorded events, oldest first. Thread-safe. */
    fun recent(): List<AdEvent> = synchronized(recent) { recent.toList() }

    private fun deliver(event: AdEvent) {
        if (recordRecent) {
            synchronized(recent) {
                if (recent.size == recentCapacity) recent.removeFirst()
                recent.addLast(event)
            }
        }
        flow.tryEmit(event)
        for (listener in listeners) {
            try {
                listener.onEvent(event)
            } catch (t: Throwable) {
                log.e(t) { "AdEventListener ${listener::class.java.name} threw while handling $event" }
            }
        }
    }

    companion object {
        const val DEFAULT_RECENT_CAPACITY: Int = 100
        private const val FLOW_BUFFER = 64
    }
}
