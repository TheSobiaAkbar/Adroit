package com.company.admob.internal.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How an ad profile loads in front of the user (PROMPT 29): each field comes from the ad's Firebase value, then the
 * ad's definition, then the Firebase `AdSettings`, then the app's `AdSettings`, then [DEFAULTS]. Null means "not set
 * here". Immutable.
 *
 * @property loadTimeout how long a show waits for a full-screen ad that isn't loaded yet; zero doesn't wait.
 * @property showLoading a loading screen before a full-screen ad, a shimmer while a banner or native ad loads.
 * @property loadingTime how long the loading screen stays up at least, when the ad is already loaded.
 */
internal data class AdBehavior(
    val loadTimeout: Duration? = null,
    val showLoading: Boolean? = null,
    val loadingTime: Duration? = null,
) {
    /** This, with each unset field taken from [fallback]. */
    fun orElse(fallback: AdBehavior?): AdBehavior = if (fallback == null) this else AdBehavior(
        loadTimeout ?: fallback.loadTimeout,
        showLoading ?: fallback.showLoading,
        loadingTime ?: fallback.loadingTime,
    )

    /** Null when nothing is set, so an empty JSON value changes nothing. */
    fun takeIfSet(): AdBehavior? = takeUnless { it == AdBehavior() }

    companion object {
        const val DEFAULT_LOAD_TIMEOUT_SECONDS: Int = 8
        const val DEFAULT_LOADING_SECONDS: Int = 1

        val DEFAULTS: AdBehavior = AdBehavior(DEFAULT_LOAD_TIMEOUT_SECONDS.seconds, true, DEFAULT_LOADING_SECONDS.seconds)
    }
}
