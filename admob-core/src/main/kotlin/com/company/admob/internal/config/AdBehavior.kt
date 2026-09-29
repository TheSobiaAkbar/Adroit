package com.company.admob.internal.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How an ad profile loads and looks in front of the user (PROMPT 29): each field comes from the ad's Firebase value,
 * then the ad's definition, then the Firebase `AdSettings`, then the app's `AdSettings`, then [DEFAULTS]. Null means
 * "not set here". Immutable.
 *
 * @property loadTimeout how long a show waits for a full-screen ad that isn't loaded yet; zero doesn't wait.
 * @property showLoading a loading screen before a full-screen ad, a shimmer while a banner or native ad loads.
 * @property loadingTime how long the loading screen stays up at least, when the ad is already loaded.
 * @property ctaColors a native ad's call-to-action background: one color, or two for a gradient from start to end.
 *   Null keeps the layout's own (the `company_admob_native_cta` colors).
 * @property ctaTextColor a native ad's call-to-action text color.
 * @property attributionColor a native ad's "Ad" badge background.
 * @property attributionTextColor a native ad's "Ad" badge text color.
 */
internal data class AdBehavior(
    val loadTimeout: Duration? = null,
    val showLoading: Boolean? = null,
    val loadingTime: Duration? = null,
    val ctaColors: List<Int>? = null,
    val ctaTextColor: Int? = null,
    val attributionColor: Int? = null,
    val attributionTextColor: Int? = null,
) {
    /** This, with each unset field taken from [fallback]. */
    fun orElse(fallback: AdBehavior?): AdBehavior = if (fallback == null) this else AdBehavior(
        loadTimeout ?: fallback.loadTimeout,
        showLoading ?: fallback.showLoading,
        loadingTime ?: fallback.loadingTime,
        ctaColors ?: fallback.ctaColors,
        ctaTextColor ?: fallback.ctaTextColor,
        attributionColor ?: fallback.attributionColor,
        attributionTextColor ?: fallback.attributionTextColor,
    )

    /** Null when nothing is set, so an empty JSON value changes nothing. */
    fun takeIfSet(): AdBehavior? = takeUnless { it == AdBehavior() }

    /** Whether it sets any of a native ad's colors. */
    val setsNativeColors: Boolean
        get() = ctaColors != null || ctaTextColor != null || attributionColor != null || attributionTextColor != null

    companion object {
        const val DEFAULT_LOAD_TIMEOUT_SECONDS: Int = 8
        const val DEFAULT_LOADING_SECONDS: Int = 1

        val DEFAULTS: AdBehavior = AdBehavior(DEFAULT_LOAD_TIMEOUT_SECONDS.seconds, true, DEFAULT_LOADING_SECONDS.seconds)
    }
}
