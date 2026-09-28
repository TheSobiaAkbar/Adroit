package com.company.admob.internal.config

import com.company.admob.AdFormat
import com.company.admob.AdPlacement
import com.company.admob.AppOpenPlacement
import com.company.admob.BannerPlacement
import com.company.admob.BannerSize
import com.company.admob.InterstitialPlacement
import com.company.admob.NativePlacement
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
import com.company.admob.config.PlacementsBuilder
import com.company.admob.config.PreloadStrategy
import com.company.admob.config.RetryPolicy
import com.company.admob.config.SplashTimings
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

    /** Every placement, the swap alternates included (off unless a remote value swaps to them). */
    val allPlacements: Collection<EffectivePlacement> get() = placements.values

    /** The splash timings in force: remote values first, then the app's settings, then the defaults. */
    val splashTimings: SplashTimings =
        overrides.splash?.orElse(source.splashTimings) ?: source.splashTimings

    /** The placements the app configured, without the swap alternates the library adds. */
    val configuredPlacements: List<EffectivePlacement> get() = placements.values.filter { !it.isAlternate }

    /** The resolved settings for [placement], or null if it isn't configured. */
    fun placement(placement: AdPlacement): EffectivePlacement? = placements[placement]

    /**
     * The placement that serves [key] now: its configured placement, or, when a remote value swapped its format, the
     * partner placement standing in for it. Null if [key] isn't configured.
     */
    fun active(key: String): EffectivePlacement? {
        val primary = placements.values.firstOrNull { it.placement.key == key && !it.isAlternate } ?: return null
        val swappedTo = primary.swappedTo ?: return primary
        return placements.values.firstOrNull { it.placement.key == key && it.isAlternate && it.format == swappedTo }
            ?: primary
    }

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
                val key = placementConfig.placement.key
                placements.getOrPut(placementConfig.placement) {
                    resolve(placementConfig, demo, overrides, key in config.loadAgainPlacementKeys, behaviorOf(key, config, overrides))
                }
            }
            // Every swappable ad also gets its partner-format placement, off unless a remote value swaps to it. The
            // set of placements is then fixed for the whole run, which the engines rely on (their availability flows).
            for (primary in placements.values.toList()) {
                val alternate = alternateOf(primary, demo, overrides) ?: continue
                placements.putIfAbsent(alternate.placement, alternate)
            }
            return EffectiveConfig(config, overrides, placements)
        }

        /** An ad profile's loading behaviour, every field resolved; null for placements not defined as profiles. */
        private fun behaviorOf(key: String, config: AdMobConfig, overrides: AdOverrides): AdBehavior? {
            if (key !in config.profilePlacementKeys) return null
            return (overrides.placements[key]?.behavior ?: AdBehavior())
                .orElse(config.profileBehaviors[key])
                .orElse(overrides.behavior)
                .orElse(config.settingsBehavior)
                .orElse(AdBehavior.DEFAULTS)
        }

        private fun usesDemoAdUnits(config: AdMobConfig): Boolean = when (config.environment) {
            AdEnvironment.DEVELOPMENT -> true
            AdEnvironment.STAGING -> config.testing.useDemoAdUnits ?: true
            AdEnvironment.PRODUCTION -> false
        }

        /** Precedence (§21): a remotely disabled format, then the remote placement override, then the local value. */
        private fun resolve(
            config: PlacementConfig,
            demo: Boolean,
            overrides: AdOverrides,
            loadAgain: Boolean,
            behavior: AdBehavior?,
        ): EffectivePlacement {
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
            // Swapped away: the partner placement serves this key, so this one releases its ad and stays quiet.
            val swappedTo = override?.format?.takeIf { it != config.format && it == FormatSwap.partnerOf(config.format) }
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
                disabledReason = if (swappedTo != null && disabledReason == null) UnavailableReason.DISABLED_BY_REMOTE else disabledReason,
                swappedTo = swappedTo,
                keyDisabledReason = disabledReason,
                placementRule = rule,
                preload = override?.preload ?: config.preload,
                bannerSize = bannerSize,
                nativeStyle = override?.nativeStyle ?: (config as? NativePlacementConfig)?.style,
                splashTimeout = override?.splashTimeout ?: (config as? InterstitialPlacementConfig)?.splashTimeout,
                loadAgain = config.format != AdFormat.BANNER && (override?.loadAgain ?: loadAgain),
                behavior = behavior,
            )
        }

        /**
         * [primary]'s partner-format placement (same key), or null when its format doesn't swap. It's enabled only
         * while a remote value swaps [primary] to it and [primary]'s key is otherwise on. It keeps [primary]'s
         * frequency rules and splash role, and counts its shows as [primary]'s, so a swapped ad has one counter,
         * one timer and one set of caps whatever its format.
         */
        private fun alternateOf(primary: EffectivePlacement, demo: Boolean, overrides: AdOverrides): EffectivePlacement? {
            val format = FormatSwap.partnerOf(primary.format) ?: return null
            val key = primary.placement.key
            val override = overrides.placements[key]
            val active = primary.swappedTo == format
            val bannerSize = override?.bannerSize ?: BannerSize.AnchoredAdaptive
            // Only the swap's own ad units fit the new format; without them, Google's test ad unit (outside
            // PRODUCTION: validation drops a swap without ad units there).
            val configured = override?.effectiveAdUnitIds?.takeIf { active }
            val adUnitIds = when {
                configured == null -> listOf(GoogleDemoAdUnits.forFormat(format, bannerSize))
                demo -> configured.map { GoogleDemoAdUnits.forFormat(format, bannerSize) }
                else -> configured
            }
            val builder = PlacementsBuilder()
            when (format) {
                AdFormat.INTERSTITIAL -> builder.interstitial(InterstitialPlacement(key), adUnitIds)
                AdFormat.APP_OPEN -> builder.appOpen(AppOpenPlacement(key), adUnitIds) { cooldown = Duration.ZERO }
                AdFormat.BANNER -> builder.banner(BannerPlacement(key), adUnitIds) { size = bannerSize }
                AdFormat.NATIVE -> builder.native(NativePlacement(key), adUnitIds) {
                    override?.nativeStyle?.let { style = it }
                }
                else -> return null
            }
            val config = builder.configs.single()
            return EffectivePlacement(
                config = config,
                adUnitIds = adUnitIds,
                retry = FormatDefaults.retry(format),
                expiry = FormatDefaults.expiry(format),
                disabledReason = if (active) primary.keyDisabledReason else UnavailableReason.DISABLED_BY_CONFIG,
                placementRule = primary.frequency,
                preload = primary.preload,
                bannerSize = if (format == AdFormat.BANNER) bannerSize else null,
                nativeStyle = (config as? NativePlacementConfig)?.style,
                splashTimeout = primary.splashTimeout ?: primary.takeIf { it.isSplash }?.let { FormatDefaults.SPLASH_TIMEOUT },
                isAlternate = true,
                frequencyPlacement = primary.placement,
                splash = primary.isSplash,
                loadAgain = format != AdFormat.BANNER && primary.loadAgain,
                behavior = primary.behavior,
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
    /** Set on a configured placement a remote value swapped to this partner format; see [EffectiveConfig.active]. */
    val swappedTo: AdFormat? = null,
    /** Why the key itself is off (config, remote, or format disabled), ignoring a swap; null when it's on. */
    val keyDisabledReason: UnavailableReason? = disabledReason,
    /** True for the partner-format placement the library adds to a swappable ad; it's never configured by the app. */
    val isAlternate: Boolean = false,
    /** Where shows are counted and frequency rules looked up: the configured placement, also for its alternate. */
    val frequencyPlacement: AdPlacement = config.placement,
    splash: Boolean = (config as? InterstitialPlacementConfig)?.splash == true,
    /** An ad profile's `loadAgain`: load the next ad right after each show, whatever [preload] and [retry] say. */
    val loadAgain: Boolean = false,
    /** An ad profile's loading behaviour (load timeout, loading screen, shimmer); null for other placements. */
    val behavior: AdBehavior? = null,
) {
    val placement: AdPlacement get() = config.placement
    val format: AdFormat get() = config.format
    val enabled: Boolean get() = disabledReason == null

    /** The first ad unit: the one to name in diagnostics and in events not tied to a request. */
    val adUnitId: String get() = adUnitIds.first()

    /**
     * A splash ad: not counted in the overall interstitial limit, and not subject to the counter. An alternate of the
     * splash interstitial (an app open ad on the splash screen) is one too.
     */
    val isSplash: Boolean = splash

    /** The placement rule; for app open, [AppOpenPlacementConfig.cooldown] raises its minimum interval (§20.6). */
    val frequency: FrequencyRule =
        if (config is AppOpenPlacementConfig && config.cooldown > placementRule.minInterval) {
            FrequencyRule(config.cooldown, placementRule.maxPerSession, placementRule.counter)
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
