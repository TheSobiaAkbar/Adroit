package com.company.admob

/**
 * Library-wide readiness state, observed through [AdsClient.state].
 *
 * Ads load only in [Ready]. Every other state means no ad request is made, and every show call returns a
 * deterministic "not shown" result.
 *
 * Stability: new states may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public sealed interface AdsState {
    /** `CompanyAdMob.initialize()` has not been called. */
    public data object NotInitialized : AdsState

    /** Configuration is valid; waiting for `start(activity)` to gather consent. */
    public data object AwaitingStart : AdsState

    /** The consent flow (UMP) is running. */
    public data object GatheringConsent : AdsState

    /** Consent allows ads; the Google Mobile Ads SDK is initializing. */
    public data object InitializingSdk : AdsState

    /** Ads may be requested and shown. */
    public data object Ready : AdsState

    /** Ads are intentionally off, for the given [reason]. The app keeps working normally. */
    public data class Unavailable(public val reason: UnavailableReason) : AdsState

    /** Configuration or SDK initialization failed. Ads stay off; the app keeps working normally. */
    public data class Failed(public val error: AdError) : AdsState
}

/** Why ads are [AdsState.Unavailable]. New values may be added in minor versions. */
public enum class UnavailableReason {
    /** The user has not given the consent required to request ads (UMP `canRequestAds()` is false). */
    CONSENT_NOT_OBTAINED,

    /** Ads, a format, or a placement are disabled in the local configuration. */
    DISABLED_BY_CONFIG,

    /** Ads, a format, or a placement are disabled by remote configuration overrides. */
    DISABLED_BY_REMOTE,

    /** The host app declared the user ad-free, for example after a purchase. */
    AD_FREE_USER,
}
