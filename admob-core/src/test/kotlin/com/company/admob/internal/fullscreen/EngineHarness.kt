package com.company.admob.internal.fullscreen

import android.app.Activity
import android.os.Looper
import com.company.admob.AdPlacement
import com.company.admob.AdsState
import com.company.admob.FullScreenResult
import com.company.admob.InterstitialPlacement
import com.company.admob.RewardResult
import com.company.admob.RewardedInterstitialPlacement
import com.company.admob.RewardedPlacement
import com.company.admob.config.AdEnvironment
import com.company.admob.config.AdMobConfig
import com.company.admob.config.FrequencyRule
import com.company.admob.config.RetryPolicy
import com.company.admob.config.TestIds
import com.company.admob.config.adMobConfig
import com.company.admob.fakes.FakeActivityTracker
import com.company.admob.fakes.FakeAdsSdk
import com.company.admob.fakes.FakeAppForegroundSource
import com.company.admob.fakes.FakeLoadedFullScreenAd
import com.company.admob.fakes.FakeReadiness
import com.company.admob.fakes.TestClock
import com.company.admob.internal.config.EffectiveConfig
import com.company.admob.internal.config.LiveConfig
import com.company.admob.internal.events.EventBus
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.reliability.FrequencyGate
import com.company.admob.internal.reliability.FullScreenCoordinator
import com.company.admob.internal.reliability.RetryScheduler
import com.company.admob.logging.LogLevel
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
