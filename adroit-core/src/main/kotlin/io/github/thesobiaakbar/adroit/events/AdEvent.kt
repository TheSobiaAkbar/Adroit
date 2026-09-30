package io.github.thesobiaakbar.adroit.events

import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.AdFormat
import io.github.thesobiaakbar.adroit.AdsState
import io.github.thesobiaakbar.adroit.ConsentState
import io.github.thesobiaakbar.adroit.Reward

/**
 * Something that happened in Adroit, delivered through `AdsClient.events` and to configured
 * [AdEventListener]s, always on the main thread.
 *
 * Events are immutable snapshots with identity equality. [timestampMillis] is wall-clock time
 * (`System.currentTimeMillis()`), suitable for analytics.
 *
 * Stability: new event types may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public sealed interface AdEvent {
    /** The placement the event relates to, or null for library-wide events. */
    public val placementKey: String?

    /** The ad format the event relates to, or null for library-wide events. */
    public val format: AdFormat?

    /** Wall-clock time of the event, in milliseconds since the epoch. */
    public val timestampMillis: Long

    /** An event about a specific placement's ad. [adUnitId] is the effective ad unit ID used for the request. */
    public sealed interface PlacementEvent : AdEvent {
        override val placementKey: String
        override val format: AdFormat
        /** The effective ad unit ID used for the request. */
        public val adUnitId: String
    }

    /** An ad request was sent. */
    public class LoadRequested(
        override val placementKey: String,
        override val format: AdFormat,
        override val adUnitId: String,
        override val timestampMillis: Long,
    ) : PlacementEvent {
        override fun toString(): String = describe("LoadRequested")
    }

    /** An ad loaded after [latencyMillis]. [source] identifies the ad source (network), when known. */
    public class Loaded(
        override val placementKey: String,
        override val format: AdFormat,
        override val adUnitId: String,
        override val timestampMillis: Long,
        public val latencyMillis: Long,
        public val source: AdSourceInfo?,
    ) : PlacementEvent {
        override fun toString(): String = describe("Loaded", "latencyMillis=$latencyMillis", "source=$source")
    }

    /** An ad failed to load. [attempt] is 1-based; [willRetry] tells whether a retry is scheduled. */
    public class LoadFailed(
        override val placementKey: String,
        override val format: AdFormat,
        override val adUnitId: String,
        override val timestampMillis: Long,
        public val error: AdError,
        public val attempt: Int,
        public val willRetry: Boolean,
    ) : PlacementEvent {
        override fun toString(): String =
            describe("LoadFailed", "error=${error.code}", "attempt=$attempt", "willRetry=$willRetry")
    }

    /** The host asked to show an ad for the placement. */
    public class ShowRequested(
        override val placementKey: String,
        override val format: AdFormat,
        override val adUnitId: String,
        override val timestampMillis: Long,
    ) : PlacementEvent {
        override fun toString(): String = describe("ShowRequested")
    }

    /** A show request ended without showing an ad, because of [error]. */
    public class NotShown(
        override val placementKey: String,
        override val format: AdFormat,
        override val adUnitId: String,
        override val timestampMillis: Long,
        public val error: AdError,
    ) : PlacementEvent {
        override fun toString(): String = describe("NotShown", "error=${error.code}")
    }

    /** A full-screen ad was shown. */
    public class Shown(
        override val placementKey: String,
        override val format: AdFormat,
        override val adUnitId: String,
        override val timestampMillis: Long,
    ) : PlacementEvent {
        override fun toString(): String = describe("Shown")
    }

    /** The SDK recorded an impression. */
    public class Impression(
        override val placementKey: String,
        override val format: AdFormat,
        override val adUnitId: String,
        override val timestampMillis: Long,
    ) : PlacementEvent {
        override fun toString(): String = describe("Impression")
    }

    /** The user tapped the ad. */
    public class Clicked(
        override val placementKey: String,
        override val format: AdFormat,
        override val adUnitId: String,
        override val timestampMillis: Long,
    ) : PlacementEvent {
        override fun toString(): String = describe("Clicked")
    }

    /** A full-screen ad was dismissed. */
    public class Dismissed(
        override val placementKey: String,
        override val format: AdFormat,
        override val adUnitId: String,
        override val timestampMillis: Long,
    ) : PlacementEvent {
        override fun toString(): String = describe("Dismissed")
    }

    /** The user earned [reward]. [rewardId] matches the `RewardResult.Earned.rewardId` delivered for the same show. */
    public class RewardEarned(
        override val placementKey: String,
        override val format: AdFormat,
        override val adUnitId: String,
        override val timestampMillis: Long,
        public val reward: Reward,
        public val rewardId: String,
    ) : PlacementEvent {
        override fun toString(): String = describe("RewardEarned", "reward=$reward", "rewardId=$rewardId")
    }

    /**
     * Impression-level ad revenue ("paid event"). Requires impression-level ad revenue to be turned on in the
     * AdMob UI. [source] identifies the ad source that earned it, when known.
     */
    public class Paid(
        override val placementKey: String,
        override val format: AdFormat,
        override val adUnitId: String,
        override val timestampMillis: Long,
        public val revenue: AdRevenue,
        public val source: AdSourceInfo?,
    ) : PlacementEvent {
        override fun toString(): String = describe("Paid", "revenue=$revenue", "source=$source")
    }

    /** A cached ad expired before being shown and was discarded. */
    public class Expired(
        override val placementKey: String,
        override val format: AdFormat,
        override val adUnitId: String,
        override val timestampMillis: Long,
    ) : PlacementEvent {
        override fun toString(): String = describe("Expired")
    }

    /** A banner or native ad was destroyed and its resources released. */
    public class Destroyed(
        override val placementKey: String,
        override val format: AdFormat,
        override val adUnitId: String,
        override val timestampMillis: Long,
    ) : PlacementEvent {
        override fun toString(): String = describe("Destroyed")
    }

    /** The user's consent information changed. */
    public class ConsentUpdated(
        public val consent: ConsentState,
        override val timestampMillis: Long,
    ) : AdEvent {
        override val placementKey: String? get() = null
        override val format: AdFormat? get() = null
        override fun toString(): String = "ConsentUpdated(consent=$consent, timestampMillis=$timestampMillis)"
    }

    /** The library-wide [AdsState] changed. */
    public class StateChanged(
        public val state: AdsState,
        override val timestampMillis: Long,
    ) : AdEvent {
        override val placementKey: String? get() = null
        override val format: AdFormat? get() = null
        override fun toString(): String = "StateChanged(state=$state, timestampMillis=$timestampMillis)"
    }

    /** A configuration problem that doesn't stop ads, for example test devices configured in production. */
    public class ConfigurationWarning(
        public val message: String,
        override val timestampMillis: Long,
    ) : AdEvent {
        override val placementKey: String? get() = null
        override val format: AdFormat? get() = null
        override fun toString(): String = "ConfigurationWarning(message=$message, timestampMillis=$timestampMillis)"
    }
}

private fun AdEvent.PlacementEvent.describe(name: String, vararg extra: String): String =
    (listOf("placement=$placementKey", "format=$format", "adUnitId=$adUnitId") + extra +
        "timestampMillis=$timestampMillis").joinToString(prefix = "$name(", postfix = ")")

/**
 * Log-safe description of the event. With [redactAdUnitIds], ad unit IDs are shortened to their last 4 characters
 * (used in production).
 */
internal fun AdEvent.toLogString(redactAdUnitIds: Boolean): String {
    val text = toString()
    if (!redactAdUnitIds || this !is AdEvent.PlacementEvent) return text
    return text.replace("adUnitId=$adUnitId", "adUnitId=${redactAdUnitId(adUnitId)}")
}

/** Shortens an ad unit ID to its last 4 characters, for logs. */
internal fun redactAdUnitId(adUnitId: String): String =
    if (adUnitId.length <= 4) "…" else "…" + adUnitId.takeLast(4)

private val FULL_AD_UNIT_ID = Regex("""ca-app-pub-\d{16}/\d{10}""")

/** Shortens every full ad unit ID in [text] (for example in an error message or a retry key) with [redactAdUnitId]. */
internal fun redactAdUnitIdsIn(text: String): String = FULL_AD_UNIT_ID.replace(text) { redactAdUnitId(it.value) }
