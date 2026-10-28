package io.github.thesobiaakbar.adroit.internal.runtime

import android.app.Application
import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.AdFormat
import io.github.thesobiaakbar.adroit.AdsClient
import io.github.thesobiaakbar.adroit.AdsState
import io.github.thesobiaakbar.adroit.AdroitVersion
import io.github.thesobiaakbar.adroit.config.AdEnvironment
import io.github.thesobiaakbar.adroit.config.AdMobConfig
import io.github.thesobiaakbar.adroit.config.AppOpenPlacementConfig
import io.github.thesobiaakbar.adroit.config.FrequencyRule
import io.github.thesobiaakbar.adroit.events.AdEvent
import io.github.thesobiaakbar.adroit.internal.AdFreeController
import io.github.thesobiaakbar.adroit.internal.HardwareAccelerationCheck
import io.github.thesobiaakbar.adroit.internal.appopen.AppOpenApiImpl
import io.github.thesobiaakbar.adroit.internal.appopen.AppOpenController
import io.github.thesobiaakbar.adroit.internal.appopen.AppOpenSuppression
import io.github.thesobiaakbar.adroit.internal.banner.BannerApiImpl
import io.github.thesobiaakbar.adroit.internal.config.ConfigIssueCode
import io.github.thesobiaakbar.adroit.internal.config.ConfigValidator
import io.github.thesobiaakbar.adroit.internal.config.EffectiveConfig
import io.github.thesobiaakbar.adroit.internal.config.LiveConfig
import io.github.thesobiaakbar.adroit.internal.config.OverridesApplier
import io.github.thesobiaakbar.adroit.internal.consent.PrivacyApiImpl
import io.github.thesobiaakbar.adroit.internal.diagnostics.DiagnosticsApiImpl
import io.github.thesobiaakbar.adroit.internal.diagnostics.DiagnosticsReportBuilder
import io.github.thesobiaakbar.adroit.internal.consent.RdpWriter
import io.github.thesobiaakbar.adroit.internal.events.EventBus
import io.github.thesobiaakbar.adroit.internal.fullscreen.DialogLoadingScreens
import io.github.thesobiaakbar.adroit.internal.banner.BannerPreloader
import io.github.thesobiaakbar.adroit.internal.fullscreen.FullScreenAdEngine
import io.github.thesobiaakbar.adroit.internal.fullscreen.FullScreenFormatStrategy
import io.github.thesobiaakbar.adroit.internal.fullscreen.InterstitialApiImpl
import io.github.thesobiaakbar.adroit.internal.fullscreen.RewardedApiImpl
import io.github.thesobiaakbar.adroit.internal.fullscreen.RewardedInterstitialApiImpl
import io.github.thesobiaakbar.adroit.internal.fullscreen.UnclaimedRewards
import io.github.thesobiaakbar.adroit.internal.init.InitCoordinator
import io.github.thesobiaakbar.adroit.internal.logging.AdLog
import io.github.thesobiaakbar.adroit.internal.native.NativeAdBinder
import io.github.thesobiaakbar.adroit.internal.native.NativeAdLoaderEngine
import io.github.thesobiaakbar.adroit.internal.native.NativeApiImpl
import io.github.thesobiaakbar.adroit.internal.reliability.AdUnitRotation
import io.github.thesobiaakbar.adroit.internal.reliability.FrequencyGate
import io.github.thesobiaakbar.adroit.internal.reliability.FullScreenCoordinator
import io.github.thesobiaakbar.adroit.internal.reliability.RetryScheduler
import io.github.thesobiaakbar.adroit.internal.storage.SharedPrefsKeyValueStore
import io.github.thesobiaakbar.adroit.internal.threading.libraryScope
import io.github.thesobiaakbar.adroit.internal.threading.runOnMain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
                "Invalid Adroit configuration:\n" + validation.errors.joinToString("\n") { "- ${it.message}" },
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
        isDebuggable = deps.isDebuggable,
    )

    private val retryScheduler = RetryScheduler(scope, deps.foregroundSource, log, clock = deps.clock, network = deps.network)

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

    /** Banners loaded ahead of their view by `AdSlots.load`. */
    val bannerPreloader: BannerPreloader = BannerPreloader(
        app.applicationContext, liveConfig, deps.adsSdk, init, events, deps.clock, log, adUnitRotation, scope,
    )

    /**
     * Startup load order (§15): app open first when it may show on cold start, since a loading screen is waiting for
     * it; otherwise after the other formats.
     */
    private val appOpenFirst: Boolean = effectiveConfig.allPlacements
        .any { it.format == AdFormat.APP_OPEN && it.enabled && (it.config as AppOpenPlacementConfig).showOnColdStartAllowed }
    private val hasAppOpen: Boolean = effectiveConfig.allPlacements.any { it.format == AdFormat.APP_OPEN && it.enabled }

    private val loadingScreens = DialogLoadingScreens(log)

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
        loadingScreens = loadingScreens,
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

    /**
     * This process's launch number, counting from 1. It's 0 until the stored count has been read, which happens off the
     * main thread during `initialize`, and stays 0 when the configuration is invalid.
     */
    @Volatile
    var launchCount: Int = 0
        private set

    val appOpenController: AppOpenController = AppOpenController(
        engine = appOpenEngine,
        interstitialEngine = interstitialEngine,
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
            adUnitRotation, bannerPreloader, adFree.flow,
        ),
        native = NativeApiImpl(
            nativeEngine,
            NativeAdBinder(deps.nativeRenderPort, log) { key -> liveConfig.current.active(key)?.behavior },
            hardwareAcceleration,
        ) {
            liveConfig.current.placement(it)?.nativeStyle
        },
        diagnostics = DiagnosticsApiImpl(
            builder = DiagnosticsReportBuilder(
                libraryVersion = AdroitVersion.NAME,
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
        if (config.environment != AdEnvironment.PRODUCTION) {
            // Enum definitions: each ad's JSON, ready to paste as the value of its Firebase parameter.
            config.definitionJson.forEach { (key, json) -> log.d { "Firebase parameter $key: $json" } }
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
            // Off the main thread: UMP's stored consent state and the launch count both come from SharedPreferences,
            // whose first read loads the file from disk. start() needs the consent state first. App open reads the
            // count only after start() reaches Ready, long after this finishes.
            scope.launch(deps.ioDispatcher) {
                deps.consentSdk.warmUp()
                val store = deps.keyValueStore
                val count = store.getInt(SharedPrefsKeyValueStore.KEY_LAUNCH_COUNT, 0) + 1
                store.putInt(SharedPrefsKeyValueStore.KEY_LAUNCH_COUNT, count)
                launchCount = count
            }
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
