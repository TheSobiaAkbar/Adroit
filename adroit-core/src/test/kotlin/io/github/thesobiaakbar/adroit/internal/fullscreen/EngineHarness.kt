package io.github.thesobiaakbar.adroit.internal.fullscreen

import android.app.Activity
import android.os.Looper
import io.github.thesobiaakbar.adroit.AdPlacement
import io.github.thesobiaakbar.adroit.AdsState
import io.github.thesobiaakbar.adroit.FullScreenResult
import io.github.thesobiaakbar.adroit.InterstitialPlacement
import io.github.thesobiaakbar.adroit.RewardResult
import io.github.thesobiaakbar.adroit.RewardedInterstitialPlacement
import io.github.thesobiaakbar.adroit.RewardedPlacement
import io.github.thesobiaakbar.adroit.config.AdEnvironment
import io.github.thesobiaakbar.adroit.config.AdMobConfig
import io.github.thesobiaakbar.adroit.config.FrequencyRule
import io.github.thesobiaakbar.adroit.config.RetryPolicy
import io.github.thesobiaakbar.adroit.config.TestIds
import io.github.thesobiaakbar.adroit.config.adMobConfig
import io.github.thesobiaakbar.adroit.fakes.FakeActivityTracker
import io.github.thesobiaakbar.adroit.fakes.FakeAdsSdk
import io.github.thesobiaakbar.adroit.fakes.FakeAppForegroundSource
import io.github.thesobiaakbar.adroit.fakes.FakeLoadedFullScreenAd
import io.github.thesobiaakbar.adroit.fakes.FakeReadiness
import io.github.thesobiaakbar.adroit.fakes.TestClock
import io.github.thesobiaakbar.adroit.internal.config.EffectiveConfig
import io.github.thesobiaakbar.adroit.internal.config.LiveConfig
import io.github.thesobiaakbar.adroit.internal.events.EventBus
import io.github.thesobiaakbar.adroit.internal.logging.AdLog
import io.github.thesobiaakbar.adroit.internal.reliability.FrequencyGate
import io.github.thesobiaakbar.adroit.internal.reliability.FullScreenCoordinator
import io.github.thesobiaakbar.adroit.internal.reliability.RetryScheduler
import io.github.thesobiaakbar.adroit.logging.LogLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

internal val LEVEL_END = InterstitialPlacement("level_end")
internal val MENU = InterstitialPlacement("menu")
internal val EXTRA_LIFE = RewardedPlacement("extra_life")
internal val BONUS = RewardedInterstitialPlacement("bonus")

/** Jitter-free full-screen retry defaults, so delays are exact. */
internal val EXACT_RETRY = RetryPolicy(5.seconds, 2.0, 120.seconds, maxAttempts = 5, jitterRatio = 0.0)

/** STAGING with real (made-up) ad units, so each placement keeps its own unit unless a test shares one. */
internal fun engineConfig(extra: AdMobConfig.Builder.() -> Unit = {}): AdMobConfig = adMobConfig {
    environment = AdEnvironment.STAGING
    testing {
        useDemoAdUnits = false
        testDeviceIds += "TEST-DEVICE"
    }
    // The library has no interval by default; these tests exercise one.
    frequency { minIntervalBetweenFullScreenAds = 30.seconds }
    extra()
}

/** A [FullScreenAdEngine] for interstitials over fakes, on [scope]'s virtual time and Robolectric's main looper. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class EngineHarness(
    val scope: TestScope,
    config: AdMobConfig = engineConfig {
        placements {
            interstitial(LEVEL_END, TestIds.unit(1)) { retry = EXACT_RETRY }
            interstitial(MENU, TestIds.unit(2)) { retry = EXACT_RETRY }
        }
    },
    strategy: FullScreenFormatStrategy = FullScreenFormatStrategy.Interstitial,
    /** Loading screens for ad profiles (PROMPT 29B); null, the default, keeps shows immediate. */
    val loadingScreens: FakeLoadingScreens? = null,
) {
    val live = LiveConfig(EffectiveConfig.from(config))
    val effective: EffectiveConfig get() = live.current
    val adsSdk = FakeAdsSdk(scope)
    val readiness = FakeReadiness()
    val tracker = FakeActivityTracker()
    val foreground = FakeAppForegroundSource(isInForeground = true)
    val clock = TestClock(scope.testScheduler)
    val infoLogs = mutableListOf<String>()
    val log = AdLog(
        { level, _, message, _ -> if (level.ordinal >= LogLevel.INFO.ordinal) infoLogs += message },
        LogLevel.VERBOSE,
    )
    val events = EventBus(AdLog({ _, _, _, _ -> }, LogLevel.NONE))
    val coordinator = FullScreenCoordinator(clock)
    val frequency = FrequencyGate(
        clock,
        { effective.placement(it)?.frequency ?: FrequencyRule() },
        { effective.minIntervalBetweenFullScreenAds },
        isSplash = { effective.placement(it)?.isSplash == true },
        maxInterstitials = { effective.maxInterstitialsPerSession },
    )
    val engine = engine(strategy)

    /** An engine for [strategy] sharing this harness's readiness, frequency gate and full-screen coordinator. */
    fun engine(strategy: FullScreenFormatStrategy): FullScreenAdEngine = FullScreenAdEngine(
        strategy = strategy,
        config = live,
        adsSdk = adsSdk,
        readiness = readiness,
        activityTracker = tracker,
        foreground = foreground,
        retry = RetryScheduler(scope.backgroundScope, foreground, log, Random(0), clock = clock),
        frequency = frequency,
        coordinator = coordinator,
        events = events,
        clock = clock,
        scope = scope.backgroundScope,
        log = log,
        loadingScreens = loadingScreens,
    ).also {
        it.start()
        run()
    }

    val results = mutableListOf<FullScreenResult>()
    val rewardResults = mutableListOf<RewardResult>()

    fun activity(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    /** Advances virtual time by [millis], then runs everything due, including posted main-thread callbacks. */
    fun run(millis: Long = 0) {
        if (millis > 0) scope.advanceTimeBy(millis)
        repeat(3) {
            scope.runCurrent()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    fun ready() {
        readiness.state.value = AdsState.Ready
        run()
    }

    fun show(placement: AdPlacement = LEVEL_END, activity: Activity = activity()) =
        engine.show(activity, placement) { results += it }

    fun showRewarded(placement: AdPlacement, customData: String? = null, activity: Activity = activity()) =
        engine.showRewarded(activity, placement, customData) { rewardResults += it }

    fun adUnit(placement: AdPlacement): String = effective.placement(placement)!!.adUnitId

    /** The most recent fake ad loaded. */
    fun lastAd(): FakeLoadedFullScreenAd = adsSdk.fullScreenAds.last()
}

/** Records loading screens: when each appeared and whether it's gone. */
internal class FakeLoadingScreens(private val clock: TestClock) : LoadingScreens {
    inner class Screen(val shownAt: Long) : LoadingScreen {
        var dismissedAt: Long? = null
            private set

        override fun dismiss() {
            if (dismissedAt == null) dismissedAt = clock.elapsedRealtimeMillis()
        }
    }

    val screens = mutableListOf<Screen>()

    /** Whether a screen is on display now. */
    val showing: Boolean get() = screens.any { it.dismissedAt == null }

    override fun show(activity: Activity): LoadingScreen = Screen(clock.elapsedRealtimeMillis()).also { screens += it }
}
