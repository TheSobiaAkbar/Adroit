package io.github.thesobiaakbar.adroit.internal.config

import io.github.thesobiaakbar.adroit.internal.logging.AdLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Told when the effective configuration changes, after [LiveConfig.current] already returns the new one. */
internal fun interface ConfigChangeListener {
    fun onConfigChanged(previous: EffectiveConfig, current: EffectiveConfig)
}

/**
 * The effective configuration in force now: the local configuration with the latest valid remote overrides. Engines
 * read [current] at every decision point instead of keeping a copy, and react to changes as
 * [ConfigChangeListener]s (release caches, hide ads). Updated only by the overrides applier. Main thread only, except
 * [current] and [flow], which are safe to read anywhere.
 */
internal class LiveConfig(initial: EffectiveConfig, private val log: AdLog? = null) {
    private val mutable = MutableStateFlow(initial)
    private val listeners = mutableListOf<ConfigChangeListener>()

    val flow: StateFlow<EffectiveConfig> = mutable.asStateFlow()

    val current: EffectiveConfig get() = mutable.value

    fun addListener(listener: ConfigChangeListener) {
        listeners += listener
    }

    /** Publishes [next] and tells every listener, in registration order. A failing listener doesn't stop the rest. */
    fun update(next: EffectiveConfig) {
        val previous = mutable.value
        if (previous === next) return
        mutable.value = next
        for (listener in listeners.toList()) {
            try {
                listener.onConfigChanged(previous, next)
            } catch (e: Exception) {
                log?.e(e) { "A configuration listener threw" }
            }
        }
    }
}
