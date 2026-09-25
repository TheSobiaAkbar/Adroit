package com.company.admob.internal.native

import android.app.Activity
import android.os.Looper
import android.widget.FrameLayout
import com.company.admob.AdsState
import com.company.admob.NativeAdLayout
import com.company.admob.NativeAdResult
import com.company.admob.NativePlacement
import com.company.admob.config.AdEnvironment
import com.company.admob.config.AdMobConfig
import com.company.admob.config.RetryPolicy
import com.company.admob.config.TestIds
import com.company.admob.config.adMobConfig
import com.company.admob.fakes.FakeAdsSdk
import com.company.admob.fakes.FakeAppForegroundSource
import com.company.admob.fakes.FakeLoadedNativeAd
import com.company.admob.fakes.FakeNativeRenderPort
import com.company.admob.fakes.FakeReadiness
import com.company.admob.fakes.TestClock
import com.company.admob.internal.HardwareAccelerationCheck
import com.company.admob.internal.config.EffectiveConfig
import com.company.admob.internal.events.EventBus
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.reliability.RetryScheduler
import com.company.admob.logging.LogLevel
import com.company.admob.test.R
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
    layoutRes = R.layout.company_admob_test_native_full,
    headlineId = R.id.company_admob_test_headline,
    bodyId = R.id.company_admob_test_body,
    callToActionId = R.id.company_admob_test_cta,
    iconId = R.id.company_admob_test_icon,
    mediaContainerId = R.id.company_admob_test_media,
    advertiserId = R.id.company_admob_test_advertiser,
    starRatingId = R.id.company_admob_test_rating,
    priceId = R.id.company_admob_test_price,
    storeId = R.id.company_admob_test_store,
    attributionId = R.id.company_admob_test_attribution,
)

/** [NativeAdLoaderEngine], [NativeAdBinder] and [NativeApiImpl] over fakes, on virtual time and the main looper. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class NativeHarness(
    val scope: TestScope,
    config: AdMobConfig = nativeConfig { placements { native(FEED, TestIds.unit(9)) { retry = EXACT_NATIVE_RETRY } } },
) {
    val effective = EffectiveConfig.from(config)
    val adsSdk = FakeAdsSdk(scope)
    val readiness = FakeReadiness()
    val foreground = FakeAppForegroundSource(isInForeground = true)
    val clock = TestClock(scope.testScheduler)
    val infoLogs = mutableListOf<String>()
    val log = AdLog({ level, _, message, _ -> if (level.ordinal >= LogLevel.INFO.ordinal) infoLogs += message }, LogLevel.VERBOSE)
    val events = EventBus(AdLog({ _, _, _, _ -> }, LogLevel.NONE))
    val engine = NativeAdLoaderEngine(
        effective, adsSdk, readiness, foreground, RetryScheduler(scope.backgroundScope, foreground, log, Random(0)),
        events, clock, scope.backgroundScope, log,
    ).also { it.start() }
    val port = FakeNativeRenderPort()
    val binder = NativeAdBinder(port, log)
    val api = NativeApiImpl(engine, binder, HardwareAccelerationCheck(log))
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
