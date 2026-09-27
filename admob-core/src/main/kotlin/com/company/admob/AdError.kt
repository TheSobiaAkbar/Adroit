package com.company.admob

import kotlin.time.Duration

/**
 * A stable, Google-SDK-independent description of why an ad was not loaded or shown.
 *
 * Every subtype has a stable [code] suitable for analytics. Where the Google Mobile Ads SDK reported the failure,
 * [sdkDetails] carries its raw information for debugging. It never contains personal data.
 *
 * Stability: new subtypes may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public sealed interface AdError {
    /** Stable, snake_case identifier for analytics and logs, for example `"no_fill"`. Never changes for a subtype. */
    public val code: String

    /** Human-readable description for logs. Not localized; don't show it to users. */
    public val message: String

    /** Raw details from the Google Mobile Ads SDK, when the SDK reported this error. */
    public val sdkDetails: SdkErrorDetails? get() = null

    /** `CompanyAdMob.initialize()` has not been called, or `start()` has not completed yet. */
    public data object NotInitialized : AdError {
        override val code: String get() = "not_initialized"
        override val message: String get() = "CompanyAdMob is not initialized or not ready yet"
    }

    /** The user has not given the consent required to request ads. */
    public data object ConsentRequired : AdError {
        override val code: String get() = "consent_required"
        override val message: String get() = "User consent does not allow ad requests"
    }

    /** The configuration is invalid for this operation, for example an unknown placement. */
    public data class InvalidConfiguration(public val reason: String) : AdError {
        override val code: String get() = "invalid_configuration"
        override val message: String get() = "Invalid configuration: $reason"
    }

    /** Ads are turned off for [reason]. */
    public data class Disabled(public val reason: UnavailableReason) : AdError {
        override val code: String get() = "disabled"
        override val message: String get() = "Ads are disabled: $reason"
    }

    /** The request succeeded but no ad was available. Common and expected; retried with backoff. */
    public data class NoFill(override val sdkDetails: SdkErrorDetails? = null) : AdError {
        override val code: String get() = "no_fill"
        override val message: String get() = "No ad available (no fill)"
    }

    /** The request failed because of network connectivity. */
    public data class NetworkError(override val sdkDetails: SdkErrorDetails? = null) : AdError {
        override val code: String get() = "network_error"
        override val message: String get() = "Network error while requesting an ad"
    }

    /** The operation timed out (ad request, show handshake, or a bounded wait such as cold-start app open). */
    public data class Timeout(override val sdkDetails: SdkErrorDetails? = null) : AdError {
        override val code: String get() = "timeout"
        override val message: String get() = "Timed out"
    }

    /** The SDK failed to load an ad for another reason; see [sdkDetails]. */
    public data class LoadFailed(override val sdkDetails: SdkErrorDetails? = null) : AdError {
        override val code: String get() = "load_failed"
        override val message: String get() = "Ad failed to load" + (sdkDetails?.let { ": ${it.message}" } ?: "")
    }

    /** No ad is loaded for the placement yet. A load has been triggered for next time. */
    public data object NotReady : AdError {
        override val code: String get() = "not_ready"
        override val message: String get() = "No ad is loaded yet"
    }

    /** The cached ad expired before it was shown. It was discarded and a new one is loading. */
    public data object Expired : AdError {
        override val code: String get() = "expired"
        override val message: String get() = "The cached ad expired"
    }

    /** The SDK failed to show a loaded ad; see [sdkDetails]. */
    public data class ShowFailed(override val sdkDetails: SdkErrorDetails? = null) : AdError {
        override val code: String get() = "show_failed"
        override val message: String get() = "Ad failed to show" + (sdkDetails?.let { ": ${it.message}" } ?: "")
    }

    /** The Activity is finishing, destroyed, or not resumed, so a full-screen ad can't be shown safely. */
    public data object ActivityUnavailable : AdError {
        override val code: String get() = "activity_unavailable"
        override val message: String get() = "The Activity can't show an ad right now"
    }

    /** Another full-screen ad is already showing. */
    public data object AlreadyShowing : AdError {
        override val code: String get() = "already_showing"
        override val message: String get() = "Another full-screen ad is already showing"
    }

    /** A frequency rule blocked the show. [retryAfter] is the earliest time it could be allowed again. */
    public data class FrequencyCapped(public val retryAfter: Duration) : AdError {
        override val code: String get() = "frequency_capped"
        override val message: String get() = "Blocked by frequency rules; retry after $retryAfter"
    }

    /**
     * The placement's counter (`FrequencyRule.counter`) isn't due yet: [remaining] more `show` calls are needed.
     * Expected and frequent: continue the app flow.
     */
    public data class CounterNotReached(public val remaining: Int) : AdError {
        override val code: String get() = "counter_not_reached"
        override val message: String get() = "The placement's counter needs $remaining more show calls"
    }

    /** The Google Mobile Ads SDK failed to initialize; see [sdkDetails]. */
    public data class SdkInitFailed(override val sdkDetails: SdkErrorDetails? = null) : AdError {
        override val code: String get() = "sdk_init_failed"
        override val message: String get() = "Google Mobile Ads SDK failed to initialize"
    }

    /** An unexpected internal failure. Please report it with [detail] and logs. */
    public data class Internal(public val detail: String, public val cause: Throwable? = null) : AdError {
        override val code: String get() = "internal"
        override val message: String get() = "Internal error: $detail"
    }
}

/**
 * Raw error information reported by the Google Mobile Ads or UMP SDK, kept Google-type-free.
 *
 * @property domain the SDK error domain, when available.
 * @property code the SDK's numeric error code.
 * @property codeName the SDK's error code name, for example `"NO_FILL"`, when available.
 * @property message the SDK's error message.
 * @property responseId the ad response ID, useful when contacting Google support.
 */
public data class SdkErrorDetails(
    public val domain: String?,
    public val code: Int,
    public val codeName: String?,
    public val message: String,
    public val responseId: String?,
)
