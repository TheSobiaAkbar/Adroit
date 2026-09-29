package com.company.admob.config

import android.app.Activity
import com.company.admob.AdPlacement
import com.company.admob.AppOpenPlacement
import com.company.admob.BannerPlacement
import com.company.admob.BannerSize
import com.company.admob.InterstitialPlacement
import com.company.admob.NativePlacement
import com.company.admob.NativeStyle
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
 * Google's demo ad units for the format, so real ads aren't requested during development. Every format also takes a
 * list of ad unit IDs, used in rotation: each load request uses the next one, and after the last one the first again.
 */
@AdMobConfigDsl
public class PlacementsBuilder internal constructor() {
    internal val configs: MutableList<PlacementConfig> = mutableListOf()

    /** Configures a banner placement. */
    public fun banner(
        placement: BannerPlacement,
        adUnitId: String,
        configure: BannerPlacementBuilder.() -> Unit = {},
    ) {
        banner(placement, listOf(adUnitId), configure)
    }

    /** Configures a banner placement whose loads rotate through [adUnitIds]. */
    public fun banner(
        placement: BannerPlacement,
        adUnitIds: List<String>,
        configure: BannerPlacementBuilder.() -> Unit = {},
    ) {
        configs += BannerPlacementBuilder(placement, adUnitIds).apply(configure).build()
    }

    /** Configures an interstitial placement. */
    public fun interstitial(
        placement: InterstitialPlacement,
        adUnitId: String,
        configure: InterstitialPlacementBuilder.() -> Unit = {},
    ) {
        interstitial(placement, listOf(adUnitId), configure)
    }

    /** Configures an interstitial placement whose loads rotate through [adUnitIds]. */
    public fun interstitial(
        placement: InterstitialPlacement,
        adUnitIds: List<String>,
        configure: InterstitialPlacementBuilder.() -> Unit = {},
    ) {
        configs += InterstitialPlacementBuilder(placement, adUnitIds).apply(configure).build()
    }

    /** Configures a rewarded placement. */
    public fun rewarded(
        placement: RewardedPlacement,
        adUnitId: String,
        configure: FullScreenPlacementBuilder.() -> Unit = {},
    ) {
        rewarded(placement, listOf(adUnitId), configure)
    }

    /** Configures a rewarded placement whose loads rotate through [adUnitIds]. */
    public fun rewarded(
        placement: RewardedPlacement,
        adUnitIds: List<String>,
        configure: FullScreenPlacementBuilder.() -> Unit = {},
    ) {
        configs += FullScreenPlacementBuilder(placement, adUnitIds).apply(configure).build(::RewardedPlacementConfig)
    }

    /** Configures a rewarded interstitial placement. You must show your own intro screen, announcing the reward
     * and letting the user opt out, before showing this format. */
    public fun rewardedInterstitial(
        placement: RewardedInterstitialPlacement,
        adUnitId: String,
        configure: FullScreenPlacementBuilder.() -> Unit = {},
    ) {
        rewardedInterstitial(placement, listOf(adUnitId), configure)
    }

    /** Configures a rewarded interstitial placement whose loads rotate through [adUnitIds]. See the overload. */
    public fun rewardedInterstitial(
        placement: RewardedInterstitialPlacement,
        adUnitIds: List<String>,
        configure: FullScreenPlacementBuilder.() -> Unit = {},
    ) {
        configs += FullScreenPlacementBuilder(placement, adUnitIds).apply(configure)
            .build(::RewardedInterstitialPlacementConfig)
    }

    /** Configures an app open placement. */
    public fun appOpen(
        placement: AppOpenPlacement,
        adUnitId: String,
        configure: AppOpenPlacementBuilder.() -> Unit = {},
    ) {
        appOpen(placement, listOf(adUnitId), configure)
    }

    /** Configures an app open placement whose loads rotate through [adUnitIds]. */
    public fun appOpen(
        placement: AppOpenPlacement,
        adUnitIds: List<String>,
        configure: AppOpenPlacementBuilder.() -> Unit = {},
    ) {
        configs += AppOpenPlacementBuilder(placement, adUnitIds).apply(configure).build()
    }

    /** Configures a native ad placement. */
    public fun native(
        placement: NativePlacement,
        adUnitId: String,
        configure: NativePlacementBuilder.() -> Unit = {},
    ) {
        native(placement, listOf(adUnitId), configure)
    }

    /** Configures a native ad placement whose loads rotate through [adUnitIds]. */
    public fun native(
        placement: NativePlacement,
        adUnitIds: List<String>,
        configure: NativePlacementBuilder.() -> Unit = {},
    ) {
        configs += NativePlacementBuilder(placement, adUnitIds).apply(configure).build()
    }
}

/** Settings shared by every placement format. Defaults: enabled, no frequency limit, [PreloadStrategy.AFTER_READY]. */
@AdMobConfigDsl
public sealed class PlacementConfigBuilder(internal val placement: AdPlacement, adUnitIds: List<String>) {
    // Copied, and never empty: an empty list becomes one blank ID, which validation reports as malformed.
    internal val adUnitIds: List<AdUnitId> = adUnitIds.ifEmpty { listOf("") }.map(::AdUnitId)

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

    /** See [FrequencyRule.counter]. */
    public var counter: Int = initial.counter

    internal fun build(): FrequencyRule = FrequencyRule(minInterval, maxPerSession, counter)
}

/** Settings for a rewarded or rewarded interstitial placement, and the base of [InterstitialPlacementBuilder]. */
public open class FullScreenPlacementBuilder internal constructor(placement: AdPlacement, adUnitIds: List<String>) :
    PlacementConfigBuilder(placement, adUnitIds) {
    internal fun <T : PlacementConfig> build(
        factory: (AdPlacement, List<AdUnitId>, Boolean, FrequencyRule, PreloadStrategy, RetryPolicy?, Duration?) -> T,
    ): T = factory(placement, adUnitIds, enabled, frequency, preload, retry, expiry)
}

/** Settings for an interstitial placement. See [InterstitialPlacementConfig] for [splash] and [splashTimeout]. */
public class InterstitialPlacementBuilder internal constructor(
    placement: InterstitialPlacement,
    adUnitIds: List<String>,
) : FullScreenPlacementBuilder(placement, adUnitIds) {
    /** See [InterstitialPlacementConfig.splash]. */
    public var splash: Boolean = false

    /** See [InterstitialPlacementConfig.splashTimeout]. */
    public var splashTimeout: Duration = FormatDefaults.SPLASH_TIMEOUT

    internal fun build(): InterstitialPlacementConfig = InterstitialPlacementConfig(
        placement, adUnitIds, enabled, frequency, preload, retry, expiry, splash, splashTimeout,
    )
}

/** Settings for a banner placement. */
public class BannerPlacementBuilder internal constructor(placement: BannerPlacement, adUnitIds: List<String>) :
    PlacementConfigBuilder(placement, adUnitIds) {
    /** The size of this placement's banners, unless the code passes one in `BannerOptions`. */
    public var size: BannerSize = BannerSize.AnchoredAdaptive

    internal fun build(): BannerPlacementConfig =
        BannerPlacementConfig(placement, adUnitIds, enabled, frequency, preload, retry, expiry, size)
}

/** Settings for an app open placement. See [AppOpenPlacementConfig] for their meaning. */
public class AppOpenPlacementBuilder internal constructor(placement: AppOpenPlacement, adUnitIds: List<String>) :
    PlacementConfigBuilder(placement, adUnitIds) {
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

    /** [excludeActivities] by fully qualified class name, for the ads file. */
    internal fun excludeActivityClassNames(names: Collection<String>) {
        excludedActivityClassNames += names
    }

    internal fun build(): AppOpenPlacementConfig = AppOpenPlacementConfig(
        placement, adUnitIds, enabled, frequency, preload, retry, expiry,
        cooldown = cooldown,
        minLaunchesBeforeFirstShow = minLaunchesBeforeFirstShow,
        excludedActivityClassNames = excludedActivityClassNames.toReadOnlySet(),
        showOnColdStartAllowed = showOnColdStartAllowed,
        suppressAfterFullScreenAd = suppressAfterFullScreenAd,
    )
}

/** Settings for a native ad placement. */
public class NativePlacementBuilder internal constructor(placement: NativePlacement, adUnitIds: List<String>) :
    PlacementConfigBuilder(placement, adUnitIds) {
    /** How many ads to keep loaded, in 1..3. */
    public var poolSize: Int = FormatDefaults.NATIVE_POOL_SIZE

    /** See [NativePlacementConfig.style]. */
    public var style: NativeStyle = NativeStyle.DEFAULT

    /** An ad profile's registered app layout, shown instead of [style]; see `AdProfile.Native.layoutName`. */
    internal var layoutName: String? = null

    internal fun build(): NativePlacementConfig = NativePlacementConfig(
        placement, adUnitIds, enabled, frequency, preload, retry, expiry, poolSize, style, layoutName,
    )
}
