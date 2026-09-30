package io.github.thesobiaakbar.adroit.firebase.internal

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tells every `FirebaseRemoteConfigProvider` that the app's `RemoteConfigSession` activated new values, so they are
 * re-read at once: an activation outside the provider (unlike a real-time update) raises no Firebase callback.
 */
internal object ActivationSignal {
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    /** Adds [listener] (called on any thread). Returns the removal function. */
    fun add(listener: () -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    fun notifyActivated() {
        listeners.forEach { it() }
    }
}
