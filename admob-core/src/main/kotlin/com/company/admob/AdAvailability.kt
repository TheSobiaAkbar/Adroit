package com.company.admob

/**
 * Whether an ad for a placement can be shown right now. Use it to enable or hide UI such as a "Watch ad" button.
 *
 * Stability: new states may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public sealed interface AdAvailability {
    /** No ad is loaded and none is loading, for example before the library is ready. */
    public data object Unavailable : AdAvailability

    /** An ad request is in flight. */
    public data object Loading : AdAvailability

    /** A fresh ad is cached and can be shown. */
    public data object Ready : AdAvailability

    /** The last load failed with [error]; a retry may be scheduled. */
    public data class Failed(public val error: AdError) : AdAvailability

    /** The placement is disabled (configuration, remote override, consent, or ad-free user). */
    public data object Disabled : AdAvailability
}
