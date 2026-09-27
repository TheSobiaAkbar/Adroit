package com.company.admob.internal.config

import com.company.admob.AdFormat
import com.company.admob.AdPlacement
import com.company.admob.BannerSize
import com.company.admob.NativeStyle
import com.company.admob.UnavailableReason
import com.company.admob.config.AdEnvironment
import com.company.admob.config.AdMobConfig
import com.company.admob.config.AppOpenPlacementConfig
import com.company.admob.config.BannerPlacementConfig
import com.company.admob.config.FrequencyRule
import com.company.admob.config.InterstitialPlacementConfig
import com.company.admob.config.NativePlacementConfig
import com.company.admob.config.PlacementConfig
import com.company.admob.config.PreloadStrategy
import com.company.admob.config.RetryPolicy
import com.company.admob.config.remote.AdOverrides
import com.company.admob.events.redactAdUnitId
import com.company.admob.logging.LogLevel
import kotlin.time.Duration

/**
 * The resolved configuration the engines use: environment substitution of ad unit IDs (02_ARCHITECTURE.md §20.3),
 * format defaults, and the remote [overrides] (§21) are applied, so engines never read [AdMobConfig] defaults or
 * overrides themselves. Immutable: a new set of overrides produces a new instance ([withOverrides]).
 *
 * Build it only from a configuration that passed [ConfigValidator], and only with overrides that passed
 * [ConfigValidator.validateOverrides];duplicate placements keep the first entry.
 */
internal class EffectiveConfig private constructor(
    val source: AdMobConfig,
    val overrides: AdOverrides,
    private val placements: Map<AdPlacement, EffectivePlacement>,
) {
    val environment: AdEnvironment get() = source.environment

    /** True when every ad request uses Google's demo ad units instead of the configured ones. */
    val usesDemoAdUnits: Boolean = usesDemoAdUnits(source)

    /** Test devices to register with the SDK. Always empty in PRODUCTION. */
    val testDeviceIds: Set<String> =
        if (source.environment == AdEnvironment.PRODUCTION) emptySet() else source.testing.testDeviceIds

    /** Whether an invalid configuration throws from `initialize`. */
    val failFast: Boolean = source.advanced.failFast ?: (source.environment != AdEnvironment.PRODUCTION)

    val logLevel: LogLevel =
        source.logging.level ?: if (source.environment == AdEnvironment.PRODUCTION) LogLevel.WARN else LogLevel.DEBUG

    /** False when every ad is off: `AdOverrides.adsEnabled = false`, or the ads file's setting without an override. */
    val adsEnabled: Boolean = overrides.adsEnabled ?: source.adsEnabledByDefault

    /** The global minimum interval between full-screen ads, remote override first. */
    val minIntervalBetweenFullScreenAds: Duration =
        overrides.minIntervalBetweenFullScreenAds ?: source.frequency.minIntervalBetweenFullScreenAds

    /** Interstitials allowed per session, splash ones not counted; remote override first. */
    val maxInterstitialsPerSession: Int =
        overrides.maxInterstitialsPerSession ?: source.frequency.maxInterstitialsPerSession

    val allPlacements: Collection<EffectivePlacement> get() = placements.values

    /** The resolved settings for [placement], or null if it isn't configured. */
    fun placement(placement: AdPlacement): EffectivePlacement? = placements[placement]

    /** [adUnitId] as logs and diagnostics may show it: shortened to its last digits in PRODUCTION. */
    fun displayAdUnitId(adUnitId: String): String =
        if (environment == AdEnvironment.PRODUCTION) redactAdUnitId(adUnitId) else adUnitId

    /** This configuration's local settings with [overrides] applied instead of the current ones. */
    fun withOverrides(overrides: AdOverrides): EffectiveConfig = build(source, overrides)

    override fun toString(): String =
        "EffectiveConfig(environment=$environment, usesDemoAdUnits=$usesDemoAdUnits, adsEnabled=$adsEnabled, " +
            "placements=${placements.values})"

    companion object {
        fun from(config: AdMobConfig): EffectiveConfig = build(config, AdOverrides())

        private fun build(config: AdMobConfig, overrides: AdOverrides): EffectiveConfig {
            val demo = usesDemoAdUnits(config)
            val placements = LinkedHashMap<AdPlacement, EffectivePlacement>()
            for (placementConfig in config.placements) {
                placements.getOrPut(placementConfig.placement) { resolve(placementConfig, demo, overrides) }
            }
            return EffectiveConfig(config, overrides, placements)
        }

        private fun usesDemoAdUnits(config: AdMobConfig): Boolean = when (config.environment) {
            AdEnvironment.DEVELOPMENT -> true
            AdEnvironment.STAGING -> config.testing.useDemoAdUnits ?: true
            AdEnvironment.PRODUCTION -> false
        }

        /** Precedence (§21): a remotely disabled format, then the remote placement override, then the local value. */
        private fun resolve(config: PlacementConfig, demo: Boolean, overrides: AdOverrides): EffectivePlacement {
            val override = overrides.placements[config.placement.key]
            val configured = override?.effectiveAdUnitIds ?: config.adUnitIds.map { it.value }
            val bannerSize = override?.bannerSize ?: (config as? BannerPlacementConfig)?.size
            // Development and demo staging keep substituting demo IDs, whatever the remote ad units. The list keeps
            // its length, so the rotation still runs (and logs its position) with test ads.
            val adUnitIds = if (demo) configured.map { GoogleDemoAdUnits.forFormat(config.format, bannerSize) } else configured
            val disabledReason = when {
                config.format in overrides.disabledFormats -> UnavailableReason.DISABLED_BY_REMOTE
                override?.enabled == false -> UnavailableReason.DISABLED_BY_REMOTE
                override?.enabled == true -> null
                !config.enabled -> UnavailableReason.DISABLED_BY_CONFIG
                else -> null
            }
            val rule = FrequencyRule(
                minInterval = override?.minInterval ?: config.frequency.minInterval,
                maxPerSession = override?.maxPerSession ?: config.frequency.maxPerSession,
                counter = override?.counter ?: config.frequency.counter,
            )
            return EffectivePlacement(
                config = config,
                adUnitIds = adUnitIds,
                retry = config.retry ?: FormatDefaults.retry(config.format),
                expiry = config.expiry ?: FormatDefaults.expiry(config.format),
                disabledReason = disabledReason,
                placementRule = rule,
                preload = override?.preload ?: config.preload,
                bannerSize = bannerSize,
                nativeStyle = override?.nativeStyle ?: (config as? NativePlacementConfig)?.style,
                splashTimeout = override?.splashTimeout ?: (config as? InterstitialPlacementConfig)?.splashTimeout,
            )
        }
    }
}

/**
 * The resolved settings for one placement, overrides included.
 *
 * @property adUnitIds the ad units to request in rotation, after the remote override and environment substitution.
 *   Never empty.
 * @property bannerSize a banner placement's size (remote override first); null for other formats.
 * @property nativeStyle a native placement's style (remote override first); null for other formats.
 * @property splashTimeout an interstitial's splash timeout (remote override first); null for other formats.
 * @property expiry the library TTL, or null for banners (the SDK refreshes them).
 * @property disabledReason why the placement is off ([UnavailableReason.DISABLED_BY_CONFIG] or
 *   [UnavailableReason.DISABLED_BY_REMOTE]), or null when it's enabled.
 */
internal class EffectivePlacement(
    val config: PlacementConfig,
    val adUnitIds: List<String>,
    val retry: RetryPolicy,
    val expiry: Duration?,
    val disabledReason: UnavailableReason? = if (config.enabled) null else UnavailableReason.DISABLED_BY_CONFIG,
    placementRule: FrequencyRule = config.frequency,
    val preload: PreloadStrategy = config.preload,
    val bannerSize: BannerSize? = (config as? BannerPlacementConfig)?.size,
    val nativeStyle: NativeStyle? = (config as? NativePlacementConfig)?.style,
    val splashTimeout: Duration? = (config as? InterstitialPlacementConfig)?.splashTimeout,
) {
    val placement: AdPlacement get() = config.placement
    val format: AdFormat get() = config.format
    val enabled: Boolean get() = disabledReason == null

    /** The first ad unit: the one to name in diagnostics and in events not tied to a request. */
    val adUnitId: String get() = adUnitIds.first()

    /** A splash interstitial: not counted in the overall interstitial limit, and not subject to the counter. */
    val isSplash: Boolean get() = (config as? InterstitialPlacementConfig)?.splash == true

    /** The placement rule; for app open, [AppOpenPlacementConfig.cooldown] raises its minimum interval (§20.6). */
    val frequency: FrequencyRule =
        if (config is AppOpenPlacementConfig && config.cooldown > placementRule.minInterval) {
            placementRule.copy(minInterval = config.cooldown)
        } else {
            placementRule
        }

    override fun toString(): String =
        "EffectivePlacement(${placement.key}, format=$format, adUnitIds=$adUnitIds, enabled=$enabled, " +
            "retry=$retry, expiry=$expiry)"
}

/**
 * The ad units, in rotation order, a banner of [size] requests for [placement]. Demo ad units depend on the size
 * actually requested: Google's fixed-size demo unit for Standard and MediumRectangle, the adaptive one otherwise.
 */
internal fun EffectiveConfig.bannerAdUnitIds(placement: EffectivePlacement, size: BannerSize): List<String> =
    if (usesDemoAdUnits) placement.adUnitIds.map { GoogleDemoAdUnits.forFormat(AdFormat.BANNER, size) } else placement.adUnitIds

/** The size a banner for [placement] uses: [requested] when the code passed one, else the placement's size. */
internal fun EffectivePlacement.bannerSizeFor(requested: BannerSize?): BannerSize =
    requested ?: bannerSize ?: BannerSize.AnchoredAdaptive
