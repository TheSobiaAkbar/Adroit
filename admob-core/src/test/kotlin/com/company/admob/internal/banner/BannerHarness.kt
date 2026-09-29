package com.company.admob.internal.banner

import android.app.Activity
import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout
import com.company.admob.AdsState
import com.company.admob.BannerOptions
import com.company.admob.BannerPlacement
import com.company.admob.config.AdEnvironment
import com.company.admob.config.AdMobConfig
import com.company.admob.config.RetryPolicy
import com.company.admob.config.TestIds
import com.company.admob.config.adMobConfig
import com.company.admob.fakes.FakeAdsSdk
import com.company.admob.fakes.FakeAppForegroundSource
import com.company.admob.fakes.FakeReadiness
import com.company.admob.fakes.FakeSdkBannerView
import com.company.admob.fakes.TestClock
import com.company.admob.internal.config.EffectiveConfig
import com.company.admob.internal.config.LiveConfig
import com.company.admob.internal.events.EventBus
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.reliability.AdUnitRotation
import com.company.admob.internal.reliability.RetryScheduler
import com.company.admob.logging.LogLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

internal val HOME = BannerPlacement("home")

/** The banner defaults (15 s, ×2, 4 attempts) without jitter, so retry delays are exact. */
internal val EXACT_BANNER_RETRY = RetryPolicy(15.seconds, 2.0, 120.seconds, maxAttempts = 4, jitterRatio = 0.0)

internal fun bannerConfig(extra: AdMobConfig.Builder.() -> Unit = {}): AdMobConfig = adMobConfig {
    environment = AdEnvironment.STAGING
    testing {
        useDemoAdUnits = false
        testDeviceIds += "TEST-DEVICE"
    }
    placements { banner(HOME, TestIds.unit(7)) { retry = EXACT_BANNER_RETRY } }
    extra()
}

/**
 * [BannerApiImpl] over fakes, on [scope]'s virtual time and Robolectric's main looper. [host] is a view inside a
 * resumed Activity, so a banner added to it is attached and shown. Robolectric's default density is 1, so px = dp.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class BannerHarness(val scope: TestScope, config: AdMobConfig = bannerConfig()) {
    val live = LiveConfig(EffectiveConfig.from(config))
    val effective: EffectiveConfig get() = live.current
    val adsSdk = FakeAdsSdk(scope)
    val readiness = FakeReadiness()
    val foreground = FakeAppForegroundSource(isInForeground = true)
    val clock = TestClock(scope.testScheduler)
    val log = AdLog({ _, _, _, _ -> }, LogLevel.NONE)
    val events = EventBus(AdLog({ _, _, _, _ -> }, LogLevel.NONE))
    val rotation = AdUnitRotation()

    /** Banners loaded ahead of their view (`AdSlots.load`); [api]'s banners claim them. */
    val preloader = BannerPreloader(
        RuntimeEnvironment.getApplication(), live, adsSdk, readiness, events, clock, log, rotation, scope.backgroundScope,
    )
    val api = BannerApiImpl(
        live,
        adsSdk,
        readiness,
        RetryScheduler(scope.backgroundScope, foreground, log, Random(0)),
        events,
        clock,
        scope.backgroundScope,
        log,
        rotation = rotation,
        preloader = preloader,
    )
    val activityController: ActivityController<Activity> = Robolectric.buildActivity(Activity::class.java).setup()
    val activity: Activity get() = activityController.get()
    val root = FrameLayout(activity).also { activity.setContentView(it) }

    /** Where banners go. Its width is set by [layout], and a real layout pass sizes the banner. */
    val host = FrameLayout(activity).also {
        root.addView(it, FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** Creates a banner and adds its view to [host], without a layout pass yet. */
    fun create(options: BannerOptions = BannerOptions(), placement: BannerPlacement = HOME) =
        api.create(activity, placement, options).also {
            host.addView(it.view)
            scope.runCurrent()
        }

    /** Makes [host] [widthPx] wide and runs a real layout pass, which sizes its banners. */
    fun layout(widthPx: Int) {
        host.layoutParams = host.layoutParams.apply { width = widthPx }
        host.requestLayout()
        run()
    }

    fun ready() {
        readiness.state.value = AdsState.Ready
        run()
    }

    /** Advances virtual time by [millis], then runs everything due, including posted main-thread callbacks. */
    fun run(millis: Long = 0) {
        if (millis > 0) scope.advanceTimeBy(millis)
        repeat(3) {
            scope.runCurrent()
            // Past the next frame, so Choreographer runs pending layout passes.
            shadowOf(Looper.getMainLooper()).idleFor(FRAME)
        }
    }

    val views: List<FakeSdkBannerView> get() = adsSdk.bannerViews

    fun loads(): Int = views.sumOf { it.loadCalls.size }

    private companion object {
        val FRAME: java.time.Duration = java.time.Duration.ofMillis(20)
    }
}
