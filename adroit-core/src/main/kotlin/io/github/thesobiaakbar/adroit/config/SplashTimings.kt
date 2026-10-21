package io.github.thesobiaakbar.adroit.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How long the splash screen waits (docs/SPLASH.md): part of the `AdSettings` Firebase parameter, so each value can
 * be changed without a release. A null field keeps the value from the app's settings (or the default). Immutable.
 *
 * @property splashSeconds the splash timer after consent, for a user who may see the splash ad. Default 11.
 * @property premiumSplashSeconds the splash timer for a premium (ad-free) user, who sees no consent form and no ad.
 *   Default 3.
 * @property offlineSplashSeconds the splash timer without internet. Default 3.
 * @property consentTimeoutSeconds how long the consent form may take to appear before the splash continues without
 *   it. Default 8.
 * @property splashAdMode whether the splash shows its ad, or only loads it or leaves it for the next screen. Default
 *   [SplashAdMode.SHOW].
 */
public class SplashTimings @JvmOverloads constructor(
    public val splashSeconds: Int? = null,
    public val premiumSplashSeconds: Int? = null,
    public val offlineSplashSeconds: Int? = null,
    public val consentTimeoutSeconds: Int? = null,
    public val splashAdMode: SplashAdMode? = null,
) {
    /** These values, with [fallback]'s where these are null. */
    public fun orElse(fallback: SplashTimings): SplashTimings = SplashTimings(
        splashSeconds ?: fallback.splashSeconds,
        premiumSplashSeconds ?: fallback.premiumSplashSeconds,
        offlineSplashSeconds ?: fallback.offlineSplashSeconds,
        consentTimeoutSeconds ?: fallback.consentTimeoutSeconds,
        splashAdMode ?: fallback.splashAdMode,
    )

    /** [splashSeconds] as a duration, the default when unset. */
    public val splash: Duration get() = (splashSeconds ?: DEFAULT_SPLASH_SECONDS).seconds

    /** [premiumSplashSeconds] as a duration, the default when unset. */
    public val premiumSplash: Duration get() = (premiumSplashSeconds ?: DEFAULT_SHORT_SPLASH_SECONDS).seconds

    /** [offlineSplashSeconds] as a duration, the default when unset. */
    public val offlineSplash: Duration get() = (offlineSplashSeconds ?: DEFAULT_SHORT_SPLASH_SECONDS).seconds

    /** [consentTimeoutSeconds] as a duration, the default when unset. */
    public val consentTimeout: Duration get() = (consentTimeoutSeconds ?: DEFAULT_CONSENT_TIMEOUT_SECONDS).seconds

    /** [splashAdMode], [SplashAdMode.SHOW] when unset. */
    public val adMode: SplashAdMode get() = splashAdMode ?: SplashAdMode.SHOW

    override fun equals(other: Any?): Boolean =
        other is SplashTimings && other.splashSeconds == splashSeconds &&
            other.premiumSplashSeconds == premiumSplashSeconds && other.offlineSplashSeconds == offlineSplashSeconds &&
            other.consentTimeoutSeconds == consentTimeoutSeconds && other.splashAdMode == splashAdMode

    override fun hashCode(): Int {
        var result = splashSeconds.hashCode()
        result = 31 * result + premiumSplashSeconds.hashCode()
        result = 31 * result + offlineSplashSeconds.hashCode()
        result = 31 * result + consentTimeoutSeconds.hashCode()
        return 31 * result + splashAdMode.hashCode()
    }

    override fun toString(): String =
        "SplashTimings(splashSeconds=$splashSeconds, premiumSplashSeconds=$premiumSplashSeconds, " +
            "offlineSplashSeconds=$offlineSplashSeconds, consentTimeoutSeconds=$consentTimeoutSeconds, " +
            "splashAdMode=$splashAdMode)"

    /** The defaults. */
    public companion object {
        /** The default splash timer, in seconds. */
        public const val DEFAULT_SPLASH_SECONDS: Int = 11

        /** The default premium and offline splash timer, in seconds. */
        public const val DEFAULT_SHORT_SPLASH_SECONDS: Int = 3

        /** The default consent form timeout, in seconds. */
        public const val DEFAULT_CONSENT_TIMEOUT_SECONDS: Int = 8
    }
}
