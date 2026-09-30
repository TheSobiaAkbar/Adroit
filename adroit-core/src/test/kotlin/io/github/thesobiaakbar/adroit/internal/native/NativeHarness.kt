package io.github.thesobiaakbar.adroit.internal.native

import android.app.Activity
import android.os.Looper
import android.widget.FrameLayout
import io.github.thesobiaakbar.adroit.AdsState
import io.github.thesobiaakbar.adroit.NativeAdLayout
import io.github.thesobiaakbar.adroit.NativeAdResult
import io.github.thesobiaakbar.adroit.NativePlacement
import io.github.thesobiaakbar.adroit.config.AdEnvironment
import io.github.thesobiaakbar.adroit.config.AdMobConfig
import io.github.thesobiaakbar.adroit.config.RetryPolicy
import io.github.thesobiaakbar.adroit.config.TestIds
import io.github.thesobiaakbar.adroit.config.adMobConfig
import io.github.thesobiaakbar.adroit.fakes.FakeAdsSdk
import io.github.thesobiaakbar.adroit.fakes.FakeAppForegroundSource
import io.github.thesobiaakbar.adroit.fakes.FakeLoadedNativeAd
import io.github.thesobiaakbar.adroit.fakes.FakeNativeRenderPort
import io.github.thesobiaakbar.adroit.fakes.FakeReadiness
import io.github.thesobiaakbar.adroit.fakes.TestClock
import io.github.thesobiaakbar.adroit.internal.HardwareAccelerationCheck
import io.github.thesobiaakbar.adroit.internal.config.AdBehavior
import io.github.thesobiaakbar.adroit.internal.config.EffectiveConfig
import io.github.thesobiaakbar.adroit.internal.config.LiveConfig
import io.github.thesobiaakbar.adroit.internal.events.EventBus
import io.github.thesobiaakbar.adroit.internal.logging.AdLog
import io.github.thesobiaakbar.adroit.internal.reliability.RetryScheduler
import io.github.thesobiaakbar.adroit.logging.LogLevel
import io.github.thesobiaakbar.adroit.test.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

internal val FEED = NativePlacement("feed")

/** The native defaults (10 s, ×2, 3 attempts) without jitter, so retry delays are exact. */
internal val EXACT_NATIVE_RETRY = RetryPolicy(10.seconds, 2.0, 120.seconds, maxAttempts = 3, jitterRatio = 0.0)

internal fun nativeConfig(extra: AdMobConfig.Builder.() -> Unit = {}): AdMobConfig = adMobConfig {
    environment = AdEnvironment.STAGING
    testing {
        useDemoAdUnits = false
        testDeviceIds += "TEST-DEVICE"
    }
    extra()
}

/** Every asset view in the test layout. */
internal val FULL_LAYOUT = NativeAdLayout(
    layoutRes = R.layout.adroit_test_native_full,
    headlineId = R.id.adroit_test_headline,
    bodyId = R.id.adroit_test_body,
    callToActionId = R.id.adroit_test_cta,
    iconId = R.id.adroit_test_icon,
    mediaContainerId = R.id.adroit_test_media,
    advertiserId = R.id.adroit_test_advertiser,
    starRatingId = R.id.adroit_test_rating,
    priceId = R.id.adroit_test_price,
    storeId = R.id.adroit_test_store,
    attributionId = R.id.adroit_test_attribution,
)

/** [NativeAdLoaderEngine], [NativeAdBinder] and [NativeApiImpl] over fakes, on virtual time and the main looper. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class NativeHarness(
    val scope: TestScope,
    config: AdMobConfig = nativeConfig { placements { native(FEED, TestIds.unit(9)) { retry = EXACT_NATIVE_RETRY } } },
) {
    val live = LiveConfig(EffectiveConfig.from(config))
    val effective: EffectiveConfig get() = live.current
    val adsSdk = FakeAdsSdk(scope)
    val readiness = FakeReadiness()
    val foreground = FakeAppForegroundSource(isInForeground = true)
    val clock = TestClock(scope.testScheduler)
    val infoLogs = mutableListOf<String>()

    /** Every message, whatever its level. */
    val allLogs = mutableListOf<String>()
    val log = AdLog(
        { level, _, message, _ ->
            allLogs += message
            if (level.ordinal >= LogLevel.INFO.ordinal) infoLogs += message
        },
        LogLevel.VERBOSE,
    )
    val events = EventBus(AdLog({ _, _, _, _ -> }, LogLevel.NONE))
    val engine = NativeAdLoaderEngine(
        live, adsSdk, readiness, foreground, RetryScheduler(scope.backgroundScope, foreground, log, Random(0)),
        events, clock, scope.backgroundScope, log,
    ).also { it.start() }
    val port = FakeNativeRenderPort()
    /** The look the binder applies: an ad's colors from its profile, Firebase or settings. */
    var look: AdBehavior? = null
    val binder = NativeAdBinder(port, log) { look }
    val api = NativeApiImpl(engine, binder, HardwareAccelerationCheck(log)) { effective.placement(it)?.nativeStyle }
    val results = mutableListOf<NativeAdResult>()

    val activity: Activity by lazy { Robolectric.buildActivity(Activity::class.java).setup().get() }

    fun container(): FrameLayout = FrameLayout(activity)

    init {
        run()
    }

    fun ready() {
        readiness.state.value = AdsState.Ready
        run()
    }

    fun load(placement: NativePlacement = FEED) {
        engine.load(placement) { results += it }
        run()
    }

    fun handles() = results.filterIsInstance<NativeAdResult.Loaded>().map { it.handle as NativeAdHandleImpl }

    val ads: List<FakeLoadedNativeAd> get() = adsSdk.nativeAds

    fun loads(): Int = adsSdk.loadCalls.size

    /** Advances virtual time by [millis], then runs everything due, including posted main-thread callbacks. */
    fun run(millis: Long = 0) {
        if (millis > 0) scope.advanceTimeBy(millis)
        repeat(3) {
            scope.runCurrent()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
