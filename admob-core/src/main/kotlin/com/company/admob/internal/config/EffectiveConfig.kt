package com.company.admob.internal.config

import com.company.admob.AdFormat
import com.company.admob.AdPlacement
import com.company.admob.BannerSize
import com.company.admob.UnavailableReason
import com.company.admob.config.AdEnvironment
import com.company.admob.config.AdMobConfig
import com.company.admob.config.AppOpenPlacementConfig
import com.company.admob.config.BannerPlacementConfig
import com.company.admob.config.FrequencyRule
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

    /** False when the remote configuration turned every ad off (`AdOverrides.adsEnabled = false`). */
    val adsEnabled: Boolean = overrides.adsEnabled != false

    /** The global minimum interval between full-screen ads, remote override first. */
    val minIntervalBetweenFullScreenAds: Duration =
        overrides.minIntervalBetweenFullScreenAds ?: source.frequency.minIntervalBetweenFullScreenAds

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
            val adUnitId = if (demo) {
                // Development and demo staging keep substituting demo IDs, whatever the remote ad unit.
                GoogleDemoAdUnits.forFormat(config.format, (config as? BannerPlacementConfig)?.size)
            } else {
                override?.adUnitId ?: config.adUnitId.value
            }
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
            )
            return EffectivePlacement(
                config = config,
                adUnitId = adUnitId,
                retry = config.retry ?: FormatDefaults.retry(config.format),
                expiry = config.expiry ?: FormatDefaults.expiry(config.format),
                disabledReason = disabledReason,
                placementRule = rule,
                preload = override?.preload ?: config.preload,
            )
        }
    }
}

/**
 * The resolved settings for one placement, overrides included.
 *
 * @property adUnitId the ad unit to request, after the remote override and environment substitution.
 * @property expiry the library TTL, or null for banners (the SDK refreshes them).
 * @property disabledReason why the placement is off ([UnavailableReason.DISABLED_BY_CONFIG] or
 *   [UnavailableReason.DISABLED_BY_REMOTE]), or null when it's enabled.
 */
internal class EffectivePlacement(
    val config: PlacementConfig,
    val adUnitId: String,
    val retry: RetryPolicy,
    val expiry: Duration?,
    val disabledReason: UnavailableReason? = if (config.enabled) null else UnavailableReason.DISABLED_BY_CONFIG,
    placementRule: FrequencyRule = config.frequency,
    val preload: PreloadStrategy = config.preload,
) {
    val placement: AdPlacement get() = config.placement
    val format: AdFormat get() = config.format
    val enabled: Boolean get() = disabledReason == null

    /** The placement rule; for app open, [AppOpenPlacementConfig.cooldown] raises its minimum interval (§20.6). */
    val frequency: FrequencyRule =
        if (config is AppOpenPlacementConfig && config.cooldown > placementRule.minInterval) {
            placementRule.copy(minInterval = config.cooldown)
        } else {
            placementRule
        }

    override fun toString(): String =
        "EffectivePlacement(${placement.key}, format=$format, adUnitId=$adUnitId, enabled=$enabled, " +
            "retry=$retry, expiry=$expiry)"
}

/**
 * The ad unit a banner of [size] requests for [placement]. Demo ad units depend on the size actually requested:
 * Google's fixed-size demo unit for Standard and MediumRectangle, the adaptive one otherwise.
 */
internal fun EffectiveConfig.bannerAdUnitId(placement: EffectivePlacement, size: BannerSize): String =
    if (usesDemoAdUnits) GoogleDemoAdUnits.forFormat(AdFormat.BANNER, size) else placement.adUnitId
