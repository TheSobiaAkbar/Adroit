package com.company.admob.config

import com.company.admob.AdFormat
import com.company.admob.AdPlacement
import com.company.admob.BannerSize
import com.company.admob.NativeStyle
import com.company.admob.placementTypeName
import kotlin.time.Duration

/**
 * The configuration of one placement, created in the `placements { }` block of [adMobConfig]. Immutable.
 *
 * @property placement the placement this configures.
 * @property adUnitIds the ad units used in [AdEnvironment.PRODUCTION] (and in STAGING without demo ad units), in
 *   rotation order: each load request uses the next one, and after the last one the first again. Never empty.
 * @property enabled false turns the placement off without removing it.
 * @property frequency per-placement frequency limits.
 * @property preload when the library loads ads for this placement.
 * @property retry the backoff for failed loads, or null for the format default.
 * @property expiry how long a loaded ad stays usable, or null for the format default.
 */
public sealed class PlacementConfig(
    public val placement: AdPlacement,
    public val adUnitIds: List<AdUnitId>,
    public val enabled: Boolean,
    public val frequency: FrequencyRule,
    public val preload: PreloadStrategy,
    public val retry: RetryPolicy?,
    public val expiry: Duration?,
) {
    /** The format this configuration is for. It equals `placement.format` in a valid configuration. */
    public abstract val format: AdFormat

    /** The first of [adUnitIds]: the only one when the placement doesn't rotate. */
    public val adUnitId: AdUnitId get() = adUnitIds.first()

    override fun equals(other: Any?): Boolean =
        other is PlacementConfig && other::class == this::class && other.placement == placement &&
            other.adUnitIds == adUnitIds && other.enabled == enabled && other.frequency == frequency &&
            other.preload == preload && other.retry == retry && other.expiry == expiry && extrasEqual(other)

    override fun hashCode(): Int {
        var result = placement.hashCode()
        result = 31 * result + adUnitIds.hashCode()
        result = 31 * result + enabled.hashCode()
        result = 31 * result + frequency.hashCode()
        result = 31 * result + preload.hashCode()
        result = 31 * result + retry.hashCode()
        result = 31 * result + expiry.hashCode()
        return 31 * result + extrasHashCode()
    }

    override fun toString(): String =
        // Not `this::class.simpleName`, which R8 renames in minified apps.
        "${format.placementTypeName}Config(placement=${placement.key}, adUnitIds=$adUnitIds, enabled=$enabled, " +
            "frequency=$frequency, preload=$preload, retry=$retry, expiry=$expiry${extrasToString()})"

    internal open fun extrasEqual(other: PlacementConfig): Boolean = true

    internal open fun extrasHashCode(): Int = 0

    internal open fun extrasToString(): String = ""
}

/**
 * Banner placement configuration.
 *
 * @property size the default banner size for this placement.
 */
public class BannerPlacementConfig internal constructor(
    placement: AdPlacement,
    adUnitIds: List<AdUnitId>,
    enabled: Boolean,
    frequency: FrequencyRule,
    preload: PreloadStrategy,
    retry: RetryPolicy?,
    expiry: Duration?,
    public val size: BannerSize,
) : PlacementConfig(placement, adUnitIds, enabled, frequency, preload, retry, expiry) {
    override val format: AdFormat get() = AdFormat.BANNER

    override fun extrasEqual(other: PlacementConfig): Boolean = other is BannerPlacementConfig && other.size == size

    override fun extrasHashCode(): Int = size.hashCode()

    override fun extrasToString(): String = ", size=$size"
}

/**
 * Interstitial placement configuration.
 *
 * @property splash marks the interstitial shown on the splash screen, with `showOnSplash`. It isn't counted in
 *   [FrequencyOptions.maxInterstitialsPerSession] and ignores [FrequencyRule.counter].
 * @property splashTimeout how long `showOnSplash` waits for the library and an ad before giving up.
 */
public class InterstitialPlacementConfig internal constructor(
    placement: AdPlacement,
    adUnitIds: List<AdUnitId>,
    enabled: Boolean,
    frequency: FrequencyRule,
    preload: PreloadStrategy,
    retry: RetryPolicy?,
    expiry: Duration?,
    public val splash: Boolean,
    public val splashTimeout: Duration,
) : PlacementConfig(placement, adUnitIds, enabled, frequency, preload, retry, expiry) {
    override val format: AdFormat get() = AdFormat.INTERSTITIAL

    override fun extrasEqual(other: PlacementConfig): Boolean =
        other is InterstitialPlacementConfig && other.splash == splash && other.splashTimeout == splashTimeout

    override fun extrasHashCode(): Int = 31 * splash.hashCode() + splashTimeout.hashCode()

    override fun extrasToString(): String = ", splash=$splash, splashTimeout=$splashTimeout"
}

/** Rewarded placement configuration. */
public class RewardedPlacementConfig internal constructor(
    placement: AdPlacement,
    adUnitIds: List<AdUnitId>,
    enabled: Boolean,
    frequency: FrequencyRule,
    preload: PreloadStrategy,
    retry: RetryPolicy?,
    expiry: Duration?,
) : PlacementConfig(placement, adUnitIds, enabled, frequency, preload, retry, expiry) {
    override val format: AdFormat get() = AdFormat.REWARDED
}

/** Rewarded interstitial placement configuration. */
public class RewardedInterstitialPlacementConfig internal constructor(
    placement: AdPlacement,
    adUnitIds: List<AdUnitId>,
    enabled: Boolean,
    frequency: FrequencyRule,
    preload: PreloadStrategy,
    retry: RetryPolicy?,
    expiry: Duration?,
) : PlacementConfig(placement, adUnitIds, enabled, frequency, preload, retry, expiry) {
    override val format: AdFormat get() = AdFormat.REWARDED_INTERSTITIAL
}

/**
 * App open placement configuration. See docs/architecture/02_ARCHITECTURE.md §15 for how these gates combine.
 *
 * @property cooldown minimum time between two app open ads.
 * @property minLaunchesBeforeFirstShow app open ads show only after more than this many launches. Google advises
 *   against showing one on the first start.
 * @property excludedActivityClassNames fully qualified names of Activities over which app open ads never show.
 * @property showOnColdStartAllowed false makes `showOnColdStart` complete without showing an ad.
 * @property suppressAfterFullScreenAd no app open ad within this time after another full-screen ad was dismissed
 *   or clicked.
 */
public class AppOpenPlacementConfig internal constructor(
    placement: AdPlacement,
    adUnitIds: List<AdUnitId>,
    enabled: Boolean,
    frequency: FrequencyRule,
    preload: PreloadStrategy,
    retry: RetryPolicy?,
    expiry: Duration?,
    public val cooldown: Duration,
    public val minLaunchesBeforeFirstShow: Int,
    public val excludedActivityClassNames: Set<String>,
    public val showOnColdStartAllowed: Boolean,
    public val suppressAfterFullScreenAd: Duration,
) : PlacementConfig(placement, adUnitIds, enabled, frequency, preload, retry, expiry) {
    override val format: AdFormat get() = AdFormat.APP_OPEN

    override fun extrasEqual(other: PlacementConfig): Boolean =
        other is AppOpenPlacementConfig && other.cooldown == cooldown &&
            other.minLaunchesBeforeFirstShow == minLaunchesBeforeFirstShow &&
            other.excludedActivityClassNames == excludedActivityClassNames &&
            other.showOnColdStartAllowed == showOnColdStartAllowed &&
            other.suppressAfterFullScreenAd == suppressAfterFullScreenAd

    override fun extrasHashCode(): Int {
        var result = cooldown.hashCode()
        result = 31 * result + minLaunchesBeforeFirstShow
        result = 31 * result + excludedActivityClassNames.hashCode()
        result = 31 * result + showOnColdStartAllowed.hashCode()
        return 31 * result + suppressAfterFullScreenAd.hashCode()
    }

    override fun extrasToString(): String =
        ", cooldown=$cooldown, minLaunchesBeforeFirstShow=$minLaunchesBeforeFirstShow, " +
            "excludedActivityClassNames=$excludedActivityClassNames, " +
            "showOnColdStartAllowed=$showOnColdStartAllowed, suppressAfterFullScreenAd=$suppressAfterFullScreenAd"
}

/**
 * Native placement configuration.
 *
 * @property poolSize how many ads to keep loaded, in 1..3. Google advises caching only ads about to be shown.
 * @property style the look the views and Compose helpers use when the code passes no layout.
 */
public class NativePlacementConfig internal constructor(
    placement: AdPlacement,
    adUnitIds: List<AdUnitId>,
    enabled: Boolean,
    frequency: FrequencyRule,
    preload: PreloadStrategy,
    retry: RetryPolicy?,
    expiry: Duration?,
    public val poolSize: Int,
    public val style: NativeStyle,
) : PlacementConfig(placement, adUnitIds, enabled, frequency, preload, retry, expiry) {
    override val format: AdFormat get() = AdFormat.NATIVE

    override fun extrasEqual(other: PlacementConfig): Boolean =
        other is NativePlacementConfig && other.poolSize == poolSize && other.style == style

    override fun extrasHashCode(): Int = 31 * poolSize + style.hashCode()

    override fun extrasToString(): String = ", poolSize=$poolSize, style=$style"
}
