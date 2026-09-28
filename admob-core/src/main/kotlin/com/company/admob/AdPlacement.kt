package com.company.admob

/**
 * A logical place in the app where an ad is shown, such as `"level_complete"` or `"home_banner"`.
 *
 * Declare each placement once, typically in an `object Placements { ... }`, and map it to an ad unit in the
 * configuration. There is one final class per format, so a banner placement can't be passed where an
 * interstitial is expected.
 *
 * Keys must match `[A-Za-z0-9_]{1,40}`, so they can be used as Firebase Remote Config parameter names (for example
 * `"HistoryAd"`). Constructors throw [IllegalArgumentException] for an invalid key; that is a
 * programmer error at declaration time.
 *
 * Two placements are equal when they have the same format and key. Instances are immutable and thread-safe.
 *
 * Stability: a placement type may be added in a minor version, with a new [AdFormat]; include an `else` branch when
 * matching exhaustively.
 */
public sealed interface AdPlacement {
    /** Stable identifier used in configuration, remote overrides, events and analytics. */
    public val key: String

    /** The ad format this placement serves. */
    public val format: AdFormat
}

/** A banner placement. See [AdPlacement]. */
public class BannerPlacement(key: String) : AdPlacement {
    override val key: String = checkedPlacementKey(key)
    override val format: AdFormat get() = AdFormat.BANNER
    override fun equals(other: Any?): Boolean = placementEquals(other)
    override fun hashCode(): Int = placementHashCode()
    override fun toString(): String = placementToString()
}

/** An interstitial placement. See [AdPlacement]. */
public class InterstitialPlacement(key: String) : AdPlacement {
    override val key: String = checkedPlacementKey(key)
    override val format: AdFormat get() = AdFormat.INTERSTITIAL
    override fun equals(other: Any?): Boolean = placementEquals(other)
    override fun hashCode(): Int = placementHashCode()
    override fun toString(): String = placementToString()
}

/** A rewarded placement. See [AdPlacement]. */
public class RewardedPlacement(key: String) : AdPlacement {
    override val key: String = checkedPlacementKey(key)
    override val format: AdFormat get() = AdFormat.REWARDED
    override fun equals(other: Any?): Boolean = placementEquals(other)
    override fun hashCode(): Int = placementHashCode()
    override fun toString(): String = placementToString()
}

/** A rewarded interstitial placement. See [AdPlacement]. */
public class RewardedInterstitialPlacement(key: String) : AdPlacement {
    override val key: String = checkedPlacementKey(key)
    override val format: AdFormat get() = AdFormat.REWARDED_INTERSTITIAL
    override fun equals(other: Any?): Boolean = placementEquals(other)
    override fun hashCode(): Int = placementHashCode()
    override fun toString(): String = placementToString()
}

/** An app open placement. See [AdPlacement]. */
public class AppOpenPlacement(key: String) : AdPlacement {
    override val key: String = checkedPlacementKey(key)
    override val format: AdFormat get() = AdFormat.APP_OPEN
    override fun equals(other: Any?): Boolean = placementEquals(other)
    override fun hashCode(): Int = placementHashCode()
    override fun toString(): String = placementToString()
}

/** A native ad placement. See [AdPlacement]. */
public class NativePlacement(key: String) : AdPlacement {
    override val key: String = checkedPlacementKey(key)
    override val format: AdFormat get() = AdFormat.NATIVE
    override fun equals(other: Any?): Boolean = placementEquals(other)
    override fun hashCode(): Int = placementHashCode()
    override fun toString(): String = placementToString()
}

// The placement classes share these instead of a base class, which would have to be public API (PROMPT 27).
private val KEY_PATTERN = Regex("[A-Za-z0-9_]{1,40}")

private fun checkedPlacementKey(key: String): String {
    require(KEY_PATTERN.matches(key)) { "Invalid placement key '$key': keys must match ${KEY_PATTERN.pattern}" }
    return key
}

private fun AdPlacement.placementEquals(other: Any?): Boolean =
    other is AdPlacement && other.format == format && other.key == key

private fun AdPlacement.placementHashCode(): Int = 31 * format.hashCode() + key.hashCode()

// Spelled out rather than `this::class.simpleName`, which R8 renames in minified apps.
private fun AdPlacement.placementToString(): String = "${format.placementTypeName}($key)"

/** The public class name of this format's placement type, for `toString`s that must survive R8 renaming. */
internal val AdFormat.placementTypeName: String
    get() = when (this) {
        AdFormat.BANNER -> "BannerPlacement"
        AdFormat.INTERSTITIAL -> "InterstitialPlacement"
        AdFormat.REWARDED -> "RewardedPlacement"
        AdFormat.REWARDED_INTERSTITIAL -> "RewardedInterstitialPlacement"
        AdFormat.APP_OPEN -> "AppOpenPlacement"
        AdFormat.NATIVE -> "NativePlacement"
    }
