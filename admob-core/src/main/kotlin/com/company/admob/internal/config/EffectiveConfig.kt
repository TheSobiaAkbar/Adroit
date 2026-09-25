package com.company.admob.internal.config

import com.company.admob.AdFormat
import com.company.admob.AdPlacement
import com.company.admob.config.AdEnvironment
import com.company.admob.config.AdMobConfig
import com.company.admob.config.BannerPlacementConfig
import com.company.admob.config.FrequencyRule
import com.company.admob.config.PlacementConfig
import com.company.admob.config.PreloadStrategy
import com.company.admob.config.RetryPolicy
import com.company.admob.logging.LogLevel
import kotlin.time.Duration

/**
 * The resolved configuration the engines use: environment substitution of ad unit IDs (02_ARCHITECTURE.md §20.3)
 * and format defaults are applied, so engines never read [AdMobConfig] defaults themselves. Remote overrides are
 * layered on in PROMPT 15. Immutable.
 *
 * Build it only from a configuration that passed [ConfigValidator]; duplicate placements keep the first entry.
 */
internal class EffectiveConfig private constructor(
    val source: AdMobConfig,
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

    val allPlacements: Collection<EffectivePlacement> get() = placements.values

    /** The resolved settings for [placement], or null if it isn't configured. */
    fun placement(placement: AdPlacement): EffectivePlacement? = placements[placement]

    override fun toString(): String =
        "EffectiveConfig(environment=$environment, usesDemoAdUnits=$usesDemoAdUnits, placements=${placements.values})"

    companion object {
        fun from(config: AdMobConfig): EffectiveConfig {
            val demo = usesDemoAdUnits(config)
            val placements = LinkedHashMap<AdPlacement, EffectivePlacement>()
            for (placementConfig in config.placements) {
                placements.getOrPut(placementConfig.placement) { resolve(placementConfig, demo) }
            }
            return EffectiveConfig(config, placements)
        }

        private fun usesDemoAdUnits(config: AdMobConfig): Boolean = when (config.environment) {
            AdEnvironment.DEVELOPMENT -> true
            AdEnvironment.STAGING -> config.testing.useDemoAdUnits ?: true
            AdEnvironment.PRODUCTION -> false
        }

        private fun resolve(config: PlacementConfig, demo: Boolean): EffectivePlacement {
            val adUnitId = if (demo) {
                GoogleDemoAdUnits.forFormat(config.format, (config as? BannerPlacementConfig)?.size)
            } else {
                config.adUnitId.value
            }
            return EffectivePlacement(
                config = config,
                adUnitId = adUnitId,
                retry = config.retry ?: FormatDefaults.retry(config.format),
                expiry = config.expiry ?: FormatDefaults.expiry(config.format),
            )
        }
    }
}

/**
 * The resolved settings for one placement.
 *
 * @property adUnitId the ad unit to request, after environment substitution.
 * @property expiry the library TTL, or null for banners (the SDK refreshes them).
 */
internal class EffectivePlacement(
    val config: PlacementConfig,
    val adUnitId: String,
    val retry: RetryPolicy,
    val expiry: Duration?,
) {
    val placement: AdPlacement get() = config.placement
    val format: AdFormat get() = config.format
    val enabled: Boolean get() = config.enabled
    val frequency: FrequencyRule get() = config.frequency
    val preload: PreloadStrategy get() = config.preload

    override fun toString(): String =
        "EffectivePlacement(${placement.key}, format=$format, adUnitId=$adUnitId, enabled=$enabled, " +
            "retry=$retry, expiry=$expiry)"
}
