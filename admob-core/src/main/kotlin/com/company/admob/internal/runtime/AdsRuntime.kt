package com.company.admob.internal.runtime

import android.app.Application
import com.company.admob.AdError
import com.company.admob.AdFormat
import com.company.admob.AdsClient
import com.company.admob.AdsState
import com.company.admob.CompanyAdMobVersion
import com.company.admob.config.AdEnvironment
import com.company.admob.config.AdMobConfig
import com.company.admob.config.AppOpenPlacementConfig
import com.company.admob.config.FrequencyRule
import com.company.admob.events.AdEvent
import com.company.admob.internal.AdFreeController
import com.company.admob.internal.HardwareAccelerationCheck
import com.company.admob.internal.appopen.AppOpenApiImpl
import com.company.admob.internal.appopen.AppOpenController
import com.company.admob.internal.appopen.AppOpenSuppression
import com.company.admob.internal.banner.BannerApiImpl
import com.company.admob.internal.config.ConfigIssueCode
import com.company.admob.internal.config.ConfigValidator
import com.company.admob.internal.config.EffectiveConfig
import com.company.admob.internal.config.LiveConfig
import com.company.admob.internal.config.OverridesApplier
import com.company.admob.internal.consent.PrivacyApiImpl
import com.company.admob.internal.diagnostics.DiagnosticsApiImpl
import com.company.admob.internal.diagnostics.DiagnosticsReportBuilder
import com.company.admob.internal.consent.RdpWriter
import com.company.admob.internal.events.EventBus
import com.company.admob.internal.fullscreen.FullScreenAdEngine
import com.company.admob.internal.fullscreen.FullScreenFormatStrategy
import com.company.admob.internal.fullscreen.InterstitialApiImpl
import com.company.admob.internal.fullscreen.RewardedApiImpl
import com.company.admob.internal.fullscreen.RewardedInterstitialApiImpl
import com.company.admob.internal.fullscreen.UnclaimedRewards
import com.company.admob.internal.init.InitCoordinator
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.native.NativeAdBinder
import com.company.admob.internal.native.NativeAdLoaderEngine
import com.company.admob.internal.native.NativeApiImpl
import com.company.admob.internal.reliability.AdUnitRotation
import com.company.admob.internal.reliability.FrequencyGate
import com.company.admob.internal.reliability.FullScreenCoordinator
import com.company.admob.internal.reliability.RetryScheduler
import com.company.admob.internal.storage.SharedPrefsKeyValueStore
import com.company.admob.internal.threading.libraryScope
import com.company.admob.internal.threading.runOnMain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The composition root (architecture §9): builds the object graph by hand from [config] and the ports made by
 * [depsFactory]. Construction is Phase A of initialization (§11): cheap, no network, no Google calls.
 *
 * [state] and [events] belong to the facade, so they stay the same instances before and after initialization.
 *
 * @throws IllegalStateException if [config] is invalid and `failFast` is in effect.
 */
internal class AdsRuntime(
    app: Application,
    val config: AdMobConfig,
    state: MutableStateFlow<AdsState>,
    private val events: EventBus,
    depsFactory: (AdLog, CoroutineScope) -> RuntimeDeps,
) {
    val effectiveConfig: EffectiveConfig = EffectiveConfig.from(config)
    val log: AdLog = AdLog(
        config.logging.logger,
        effectiveConfig.logLevel,
        redactAdUnitIds = effectiveConfig.environment == AdEnvironment.PRODUCTION,
    )
    private val scope: CoroutineScope = libraryScope(log)
    val deps: RuntimeDeps = depsFactory(log, scope)
    private val validation = ConfigValidator.validate(config, deps.manifestAppId, deps.isDebuggable)

    init {
        if (!validation.isValid && effectiveConfig.failFast) {
            scope.cancel()
            throw IllegalStateException(
                "Invalid CompanyAdMob configuration:\n" + validation.errors.joinToString("\n") { "- ${it.message}" },
            )
        }
    }

    val adFree: AdFreeController = AdFreeController(log)

    /** The effective configuration in force: [effectiveConfig] plus the latest valid remote overrides. */
    val liveConfig: LiveConfig = LiveConfig(effectiveConfig, log)

    val init: InitCoordinator = InitCoordinator(
        mutableState = state,
        appContext = app.applicationContext,
        appId = config.appId?.value ?: deps.manifestAppId,
        config = effectiveConfig,
        adsSdk = deps.adsSdk,
        consentSdk = deps.consentSdk,
        rdpWriter = RdpWriter(app.applicationContext, log),
        adFree = adFree,
        clock = deps.clock,
        events = events,
        log = log,
    )

    private val retryScheduler = RetryScheduler(scope, deps.foregroundSource, log, clock = deps.clock)

    val frequency: FrequencyGate = FrequencyGate(
        clock = deps.clock,
        ruleFor = { liveConfig.current.placement(it)?.frequency ?: FrequencyRule() },
        globalInterval = { liveConfig.current.minIntervalBetweenFullScreenAds },
        globalFormats = FrequencyGate.globalFormats(config.frequency.includeRewardedInGlobalInterval),
        isSplash = { liveConfig.current.placement(it)?.isSplash == true },
        maxInterstitials = { liveConfig.current.maxInterstitialsPerSession },
    )

    /** Ad unit rotation positions, shared by every engine and banner. */
    val adUnitRotation: AdUnitRotation = AdUnitRotation()

    val fullScreenCoordinator: FullScreenCoordinator = FullScreenCoordinator(deps.clock)

    /**
     * Startup load order (§15): app open first when it may show on cold start, since a loading screen is waiting for
     * it; otherwise after the other formats.
     */
    private val appOpenFirst: Boolean = effectiveConfig.allPlacements
        .any { it.format == AdFormat.APP_OPEN && it.enabled && (it.config as AppOpenPlacementConfig).showOnColdStartAllowed }
    private val hasAppOpen: Boolean = effectiveConfig.allPlacements.any { it.format == AdFormat.APP_OPEN && it.enabled }

    private fun fullScreenEngine(strategy: FullScreenFormatStrategy) = FullScreenAdEngine(
        strategy = strategy,
        config = liveConfig,
        adsSdk = deps.adsSdk,
        readiness = init,
        activityTracker = deps.activityTracker,
        foreground = deps.foregroundSource,
        retry = retryScheduler,
        frequency = frequency,
        coordinator = fullScreenCoordinator,
        events = events,
        clock = deps.clock,
        scope = scope,
        log = log,
        readyDelay = when {
            !hasAppOpen -> Duration.ZERO
            strategy == FullScreenFormatStrategy.AppOpen -> if (appOpenFirst) Duration.ZERO else APP_OPEN_AFTER_OTHERS
            appOpenFirst -> FullScreenAdEngine.STAGGER
            else -> Duration.ZERO
        },
        rotation = adUnitRotation,
    )

    val interstitialEngine: FullScreenAdEngine = fullScreenEngine(FullScreenFormatStrategy.Interstitial)
    val rewardedEngine: FullScreenAdEngine = fullScreenEngine(FullScreenFormatStrategy.Rewarded())
    val rewardedInterstitialEngine: FullScreenAdEngine =
        fullScreenEngine(FullScreenFormatStrategy.RewardedInterstitial())
    val appOpenEngine: FullScreenAdEngine = fullScreenEngine(FullScreenFormatStrategy.AppOpen)
    private val fullScreenEngines = listOf(interstitialEngine, rewardedEngine, rewardedInterstitialEngine, appOpenEngine)

    /** This process's launch number, counting from 1; 0 when the configuration is invalid. */
    var launchCount: Int = 0
        private set

    val appOpenController: AppOpenController = AppOpenController(
        engine = appOpenEngine,
        config = liveConfig,
        readiness = init,
        activityTracker = deps.activityTracker,
        foreground = deps.foregroundSource,
        frequency = frequency,
        coordinator = fullScreenCoordinator,
        suppression = AppOpenSuppression(deps.clock),
        launchCount = { launchCount },
        clock = deps.clock,
        scope = scope,
        log = log,
    )

    private val hardwareAcceleration = HardwareAccelerationCheck(log)

    val nativeEngine: NativeAdLoaderEngine = NativeAdLoaderEngine(
        config = liveConfig,
        adsSdk = deps.adsSdk,
        readiness = init,
        foreground = deps.foregroundSource,
        retry = retryScheduler,
        events = events,
        clock = deps.clock,
        scope = scope,
        log = log,
        rotation = adUnitRotation,
    )

    val client: AdsClient = AdsClientImpl(
        state = init.state,
        eventBus = events,
        init = init,
        privacy = PrivacyApiImpl(init.consent, config.environment, log),
        interstitial = InterstitialApiImpl(interstitialEngine, log),
        rewarded = RewardedApiImpl(rewardedEngine, UnclaimedRewards(log)),
        rewardedInterstitial = RewardedInterstitialApiImpl(rewardedInterstitialEngine, UnclaimedRewards(log)),
        appOpen = AppOpenApiImpl(appOpenController),
        banner = BannerApiImpl(
            liveConfig, deps.adsSdk, init, retryScheduler, events, deps.clock, scope, log, hardwareAcceleration,
            adUnitRotation,
        ),
        native = NativeApiImpl(nativeEngine, NativeAdBinder(deps.nativeRenderPort, log), hardwareAcceleration) {
            liveConfig.current.placement(it)?.nativeStyle
        },
        diagnostics = DiagnosticsApiImpl(
            builder = DiagnosticsReportBuilder(
                libraryVersion = CompanyAdMobVersion.NAME,
                googleSdkVersion = { deps.adsSdk.sdkVersion },
                config = liveConfig,
                state = { init.state.value },
                consent = { init.consent.state.value },
                fullScreenEngines = fullScreenEngines.associateBy { it.format },
                nativeEngine = nativeEngine,
                frequency = frequency,
                coordinator = fullScreenCoordinator,
                activityTracker = deps.activityTracker,
                events = events,
            ),
            adsSdk = deps.adsSdk,
            events = events,
            environment = config.environment,
            log = log,
        ),
        runtime = this,
    )

    private val overrides = OverridesApplier(liveConfig, config.remoteConfig, events, deps.clock, scope, log)

    init {
        events.setListeners(config.eventListeners)
        events.recordRecent = config.environment != AdEnvironment.PRODUCTION
        for (warning in validation.warnings) {
            if (warning.code == ConfigIssueCode.NON_PRODUCTION_IN_RELEASE_BUILD) {
                log.e { warning.message }
            } else {
                log.w { warning.message }
            }
            events.emit(AdEvent.ConfigurationWarning(warning.message, deps.clock.currentTimeMillis()))
        }
        if (validation.isValid) {
            deps.register()
            fullScreenEngines.forEach {
                adFree.addListener(it)
                it.start()
            }
            adFree.addListener(appOpenController)
            adFree.addListener(nativeEngine)
            nativeEngine.start()
            appOpenController.start()
            val store = deps.keyValueStore
            launchCount = store.getInt(SharedPrefsKeyValueStore.KEY_LAUNCH_COUNT, 0) + 1
            store.putInt(SharedPrefsKeyValueStore.KEY_LAUNCH_COUNT, launchCount)
            liveConfig.addListener(init)
            runOnMain {
                init.onConfigValid()
                overrides.start()
            }
        } else {
            val reason = validation.errors.joinToString("; ") { it.message }
            log.e { "Invalid configuration; ads stay off. $reason" }
            runOnMain { init.onConfigInvalid(AdError.InvalidConfiguration(reason)) }
        }
    }

    /** `initialize()` was called again. The first configuration is kept. */
    fun onInitializeAgain(other: AdMobConfig) {
        if (other == config) {
            log.d { "initialize() called again with the same configuration; ignored" }
            return
        }
        val message = "initialize() called again with a different configuration; the first one is kept"
        log.e { message }
        events.emit(AdEvent.ConfigurationWarning(message, deps.clock.currentTimeMillis()))
    }

    /** Tears down for tests: cancels the library scope and detaches the observers. */
    fun close() {
        scope.cancel()
        deps.unregister()
        events.setListeners(emptyList())
        events.recordRecent = true
    }

    private companion object {
        /** When app open loads after the other formats: once their first loads have started. */
        val APP_OPEN_AFTER_OTHERS: Duration = 1.seconds
    }
}
