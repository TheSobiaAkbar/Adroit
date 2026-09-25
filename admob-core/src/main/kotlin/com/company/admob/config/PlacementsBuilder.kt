package com.company.admob.config

import android.app.Activity
import com.company.admob.AdPlacement
import com.company.admob.AppOpenPlacement
import com.company.admob.BannerPlacement
import com.company.admob.BannerSize
import com.company.admob.InterstitialPlacement
import com.company.admob.NativePlacement
import com.company.admob.RewardedInterstitialPlacement
import com.company.admob.RewardedPlacement
import com.company.admob.internal.config.FormatDefaults
import com.company.admob.internal.config.toReadOnlySet
import kotlin.time.Duration

/** Marks the configuration DSL, so an inner block can't accidentally call an outer block's functions. */
@DslMarker
public annotation class AdMobConfigDsl

/**
 * Declares placements and their ad units, in the `placements { }` block of [adMobConfig]. Each placement key may
 * be configured once; validation reports duplicates.
 *
 * Pass your real ad unit IDs. In [AdEnvironment.DEVELOPMENT] (and STAGING by default) the library substitutes
 * Google's demo ad units for the format, so real ads aren't requested during development.
 */
@AdMobConfigDsl
public class PlacementsBuilder internal constructor() {
    internal val configs: MutableList<PlacementConfig> = mutableListOf()

    /** Configures a banner placement. */
    public fun banner(placement: BannerPlacement, adUnitId: String, configure: BannerPlacementBuilder.() -> Unit = {}) {
        configs += BannerPlacementBuilder(placement, adUnitId).apply(configure).build()
    }

    /** Configures an interstitial placement. */
    public fun interstitial(
        placement: InterstitialPlacement,
        adUnitId: String,
        configure: FullScreenPlacementBuilder.() -> Unit = {},
    ) {
        configs += FullScreenPlacementBuilder(placement, adUnitId).apply(configure).build(::InterstitialPlacementConfig)
    }

    /** Configures a rewarded placement. */
    public fun rewarded(
        placement: RewardedPlacement,
        adUnitId: String,
        configure: FullScreenPlacementBuilder.() -> Unit = {},
    ) {
        configs += FullScreenPlacementBuilder(placement, adUnitId).apply(configure).build(::RewardedPlacementConfig)
    }

    /**
     * Configures a rewarded interstitial placement. You must show your own intro screen, announcing the reward
     * and letting the user opt out, before showing this format.
     */
    public fun rewardedInterstitial(
        placement: RewardedInterstitialPlacement,
        adUnitId: String,
        configure: FullScreenPlacementBuilder.() -> Unit = {},
    ) {
        configs += FullScreenPlacementBuilder(placement, adUnitId).apply(configure)
            .build(::RewardedInterstitialPlacementConfig)
    }

    /** Configures an app open placement. */
    public fun appOpen(placement: AppOpenPlacement, adUnitId: String, configure: AppOpenPlacementBuilder.() -> Unit = {}) {
        configs += AppOpenPlacementBuilder(placement, adUnitId).apply(configure).build()
    }

    /** Configures a native ad placement. */
    public fun native(placement: NativePlacement, adUnitId: String, configure: NativePlacementBuilder.() -> Unit = {}) {
        configs += NativePlacementBuilder(placement, adUnitId).apply(configure).build()
    }
}

/** Settings shared by every placement format. Defaults: enabled, no frequency limit, [PreloadStrategy.AFTER_READY]. */
@AdMobConfigDsl
public sealed class PlacementConfigBuilder(internal val placement: AdPlacement, internal val adUnitId: String) {
    /** False turns the placement off without removing it. */
    public var enabled: Boolean = true

    /** Per-placement frequency limits. */
    public var frequency: FrequencyRule = FrequencyRule()

    /** When the library loads ads for this placement. */
    public var preload: PreloadStrategy = PreloadStrategy.AFTER_READY

    /** Backoff for failed loads; null uses the format default. */
    public var retry: RetryPolicy? = null

    /** How long a loaded ad stays usable; null uses the format default. */
    public var expiry: Duration? = null

    /** Changes [frequency], starting from its current value. */
    public fun frequency(block: FrequencyRuleBuilder.() -> Unit) {
        frequency = FrequencyRuleBuilder(frequency).apply(block).build()
    }
}

/** Builds a [FrequencyRule] inside `frequency { }`. */
@AdMobConfigDsl
public class FrequencyRuleBuilder internal constructor(initial: FrequencyRule) {
    /** See [FrequencyRule.minInterval]. */
    public var minInterval: Duration = initial.minInterval

    /** See [FrequencyRule.maxPerSession]. */
    public var maxPerSession: Int = initial.maxPerSession

    internal fun build(): FrequencyRule = FrequencyRule(minInterval, maxPerSession)
}

/** Settings for an interstitial, rewarded, or rewarded interstitial placement. */
public class FullScreenPlacementBuilder internal constructor(placement: AdPlacement, adUnitId: String) :
    PlacementConfigBuilder(placement, adUnitId) {
    internal fun <T : PlacementConfig> build(
        factory: (AdPlacement, AdUnitId, Boolean, FrequencyRule, PreloadStrategy, RetryPolicy?, Duration?) -> T,
    ): T = factory(placement, AdUnitId(adUnitId), enabled, frequency, preload, retry, expiry)
}

/** Settings for a banner placement. */
public class BannerPlacementBuilder internal constructor(placement: BannerPlacement, adUnitId: String) :
    PlacementConfigBuilder(placement, adUnitId) {
    /** The default size for this placement's banners. */
    public var size: BannerSize = BannerSize.AnchoredAdaptive

    internal fun build(): BannerPlacementConfig =
        BannerPlacementConfig(placement, AdUnitId(adUnitId), enabled, frequency, preload, retry, expiry, size)
}

/** Settings for an app open placement. See [AppOpenPlacementConfig] for their meaning. */
public class AppOpenPlacementBuilder internal constructor(placement: AppOpenPlacement, adUnitId: String) :
    PlacementConfigBuilder(placement, adUnitId) {
    /** See [AppOpenPlacementConfig.cooldown]. */
    public var cooldown: Duration = FormatDefaults.APP_OPEN_COOLDOWN

    /** See [AppOpenPlacementConfig.minLaunchesBeforeFirstShow]. */
    public var minLaunchesBeforeFirstShow: Int = FormatDefaults.APP_OPEN_MIN_LAUNCHES_BEFORE_FIRST_SHOW

    /** See [AppOpenPlacementConfig.showOnColdStartAllowed]. */
    public var showOnColdStartAllowed: Boolean = true

    /** See [AppOpenPlacementConfig.suppressAfterFullScreenAd]. */
    public var suppressAfterFullScreenAd: Duration = FormatDefaults.SUPPRESS_APP_OPEN_AFTER_FULL_SCREEN_AD

    private val excludedActivityClassNames = linkedSetOf<String>()

    /**
     * Never shows app open ads over these Activities, for example a splash or onboarding screen. Only class names
     * are stored, so no class loader is retained.
     */
    public fun excludeActivities(vararg activities: Class<out Activity>) {
        activities.mapTo(excludedActivityClassNames) { it.name }
    }

    internal fun build(): AppOpenPlacementConfig = AppOpenPlacementConfig(
        placement, AdUnitId(adUnitId), enabled, frequency, preload, retry, expiry,
        cooldown = cooldown,
        minLaunchesBeforeFirstShow = minLaunchesBeforeFirstShow,
        excludedActivityClassNames = excludedActivityClassNames.toReadOnlySet(),
        showOnColdStartAllowed = showOnColdStartAllowed,
        suppressAfterFullScreenAd = suppressAfterFullScreenAd,
    )
}

/** Settings for a native ad placement. */
public class NativePlacementBuilder internal constructor(placement: NativePlacement, adUnitId: String) :
    PlacementConfigBuilder(placement, adUnitId) {
    /** How many ads to keep loaded, in 1..3. */
    public var poolSize: Int = FormatDefaults.NATIVE_POOL_SIZE

    internal fun build(): NativePlacementConfig =
        NativePlacementConfig(placement, AdUnitId(adUnitId), enabled, frequency, preload, retry, expiry, poolSize)
}
