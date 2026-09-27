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
 */
public sealed interface AdPlacement {
    /** Stable identifier used in configuration, remote overrides, events and analytics. */
    public val key: String

    /** The ad format this placement serves. */
    public val format: AdFormat
}

/** A banner placement. See [AdPlacement]. */
public class BannerPlacement(key: String) : AdPlacementBase(key, AdFormat.BANNER)

/** An interstitial placement. See [AdPlacement]. */
public class InterstitialPlacement(key: String) : AdPlacementBase(key, AdFormat.INTERSTITIAL)

/** A rewarded placement. See [AdPlacement]. */
public class RewardedPlacement(key: String) : AdPlacementBase(key, AdFormat.REWARDED)

/** A rewarded interstitial placement. See [AdPlacement]. */
public class RewardedInterstitialPlacement(key: String) : AdPlacementBase(key, AdFormat.REWARDED_INTERSTITIAL)

/** An app open placement. See [AdPlacement]. */
public class AppOpenPlacement(key: String) : AdPlacementBase(key, AdFormat.APP_OPEN)

/** A native ad placement. See [AdPlacement]. */
public class NativePlacement(key: String) : AdPlacementBase(key, AdFormat.NATIVE)

/**
 * Shared implementation of [AdPlacement]. Not intended for direct use; subclasses are the six placement types.
 */
public sealed class AdPlacementBase(
    final override val key: String,
    final override val format: AdFormat,
) : AdPlacement {
    init {
        require(KEY_PATTERN.matches(key)) {
            "Invalid placement key '$key': keys must match ${KEY_PATTERN.pattern}"
        }
    }

    final override fun equals(other: Any?): Boolean =
        other is AdPlacement && other.format == format && other.key == key

    final override fun hashCode(): Int = 31 * format.hashCode() + key.hashCode()

    // Spelled out rather than `this::class.simpleName`, which R8 renames in minified apps.
    final override fun toString(): String = "${format.placementTypeName}($key)"

    private companion object {
        val KEY_PATTERN = Regex("[A-Za-z0-9_]{1,40}")
    }
}

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
