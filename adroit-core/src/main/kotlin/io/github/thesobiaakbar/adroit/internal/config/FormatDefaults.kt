package io.github.thesobiaakbar.adroit.internal.config

import io.github.thesobiaakbar.adroit.AdFormat
import io.github.thesobiaakbar.adroit.config.RetryPolicy
import io.github.thesobiaakbar.adroit.logging.AndroidAdLogger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Every configuration default in one place. Values come from docs/architecture/02_ARCHITECTURE.md §12 (expiry),
 * §15 (app open), §20.6 (frequency) and §25 (retry).
 */
internal object FormatDefaults {
    val FULL_SCREEN_RETRY = RetryPolicy(5.seconds, 2.0, 120.seconds, maxAttempts = 5, jitterRatio = 0.2)
    val APP_OPEN_RETRY = RetryPolicy(10.seconds, 2.0, 300.seconds, maxAttempts = 3, jitterRatio = 0.2)
    val NATIVE_RETRY = RetryPolicy(10.seconds, 2.0, 120.seconds, maxAttempts = 3, jitterRatio = 0.2)
    val BANNER_RETRY = RetryPolicy(15.seconds, 2.0, 120.seconds, maxAttempts = 4, jitterRatio = 0.2)

    /** A no-fill is retried, but never sooner than this. */
    val NO_FILL_MIN_RETRY_DELAY = 30.seconds

    /** Library TTLs, a few minutes shorter than Google's figures (1 h, and 4 h for app open). */
    val FULL_SCREEN_TTL = 55.minutes
    val APP_OPEN_TTL = 3.hours + 55.minutes
    val NATIVE_TTL = 55.minutes

    // No timer unless the app sets one (timerSeconds, minSecondsBetweenFullScreenAds, frequency {}, cooldown).
    val MIN_INTERVAL_BETWEEN_FULL_SCREEN_ADS = Duration.ZERO
    val APP_OPEN_COOLDOWN = Duration.ZERO
    const val APP_OPEN_MIN_LAUNCHES_BEFORE_FIRST_SHOW = 0
    val SUPPRESS_APP_OPEN_AFTER_FULL_SCREEN_AD = 5.seconds
    val SHOW_TIMEOUT = 5.seconds
    val SPLASH_TIMEOUT = 8.seconds

    const val NATIVE_POOL_SIZE = 1
    val NATIVE_POOL_SIZE_RANGE = 1..3

    /** Shared so that configs built separately with the default logger compare equal. */
    val LOGGER = AndroidAdLogger()

    fun retry(format: AdFormat): RetryPolicy = when (format) {
        AdFormat.INTERSTITIAL, AdFormat.REWARDED, AdFormat.REWARDED_INTERSTITIAL -> FULL_SCREEN_RETRY
        AdFormat.APP_OPEN -> APP_OPEN_RETRY
        AdFormat.NATIVE -> NATIVE_RETRY
        AdFormat.BANNER -> BANNER_RETRY
    }

    /** The library TTL for [format], or null for banners, which the SDK refreshes. */
    fun expiry(format: AdFormat): Duration? = when (format) {
        AdFormat.INTERSTITIAL, AdFormat.REWARDED, AdFormat.REWARDED_INTERSTITIAL -> FULL_SCREEN_TTL
        AdFormat.APP_OPEN -> APP_OPEN_TTL
        AdFormat.NATIVE -> NATIVE_TTL
        AdFormat.BANNER -> null
    }
}
