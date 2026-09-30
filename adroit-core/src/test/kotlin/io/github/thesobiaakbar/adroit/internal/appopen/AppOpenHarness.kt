package io.github.thesobiaakbar.adroit.internal.appopen

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.github.thesobiaakbar.adroit.AdFormat
import io.github.thesobiaakbar.adroit.AppOpenPlacement
import io.github.thesobiaakbar.adroit.FullScreenResult
import io.github.thesobiaakbar.adroit.config.AppOpenPlacementBuilder
import io.github.thesobiaakbar.adroit.config.TestIds
import io.github.thesobiaakbar.adroit.fakes.FakeLoadedFullScreenAd
import io.github.thesobiaakbar.adroit.internal.fullscreen.EXACT_RETRY
import io.github.thesobiaakbar.adroit.internal.fullscreen.EngineHarness
import io.github.thesobiaakbar.adroit.internal.fullscreen.FullScreenAdEngine
import io.github.thesobiaakbar.adroit.internal.fullscreen.FullScreenFormatStrategy
import io.github.thesobiaakbar.adroit.internal.fullscreen.LEVEL_END
import io.github.thesobiaakbar.adroit.internal.fullscreen.engineConfig
import kotlinx.coroutines.test.TestScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal val APP_OPEN = AppOpenPlacement("app_open")

/** A splash screen, for the excluded-Activity gate. */
class SplashActivity : Activity()

/** A [LifecycleOwner] the test moves through its states directly. */
internal class TestLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle get() = registry

    fun moveTo(state: Lifecycle.State) {
        registry.currentState = state
    }
}

/**
 * An [AppOpenController] over an app open [FullScreenAdEngine] and fakes (see [EngineHarness]). An interstitial
 * placement shares the coordinator and frequency gate, for the "another full-screen ad" gates. [launchCount]
 * starts past the default minimum, and a resumed Activity is in front.
 */
internal class AppOpenHarness(
    scope: TestScope,
    /** Also runs an interstitial engine, which serves app open moments while the app open ad is swapped. */
    withInterstitialEngine: Boolean = false,
    configure: AppOpenPlacementBuilder.() -> Unit = {},
) {
    val h = EngineHarness(
        scope,
        engineConfig {
            placements {
                appOpen(APP_OPEN, TestIds.unit(5)) {
                    retry = EXACT_RETRY
                    configure()
                }
                interstitial(LEVEL_END, TestIds.unit(1))
            }
        },
        FullScreenFormatStrategy.AppOpen,
    )
    var launchCount = 3
    val suppression = AppOpenSuppression(h.clock)
    val interstitialEngine: FullScreenAdEngine? =
        if (withInterstitialEngine) h.engine(FullScreenFormatStrategy.Interstitial) else null
    val controller = AppOpenController(
        engine = h.engine,
        interstitialEngine = interstitialEngine,
        config = h.live,
        readiness = h.readiness,
        activityTracker = h.tracker,
        foreground = h.foreground,
        frequency = h.frequency,
        coordinator = h.coordinator,
        suppression = suppression,
        launchCount = { launchCount },
        clock = h.clock,
        scope = h.scope.backgroundScope,
        log = h.log,
    )
    val adUnit: String = h.adUnit(APP_OPEN)
    val activity: Activity = h.activity()
    val coldStartResults = mutableListOf<FullScreenResult>()

    init {
        controller.start()
        h.tracker.resumedActivity = activity
        h.run()
    }

    /** Ready, with an app open ad cached. */
    fun loaded(): AppOpenHarness = apply {
        h.ready()
        h.run(1_000)
    }

    /** A warm return to the app, after [awayFor] in the background. */
    fun returnToApp(awayFor: Duration = 1.seconds) {
        h.foreground.moveToBackground()
        h.run(awayFor.inWholeMilliseconds)
        h.foreground.moveToForeground()
        h.run()
    }

    fun coldStart() {
        h.foreground.moveToForeground(isColdStart = true)
        h.run()
    }

    fun showOnColdStart(activity: Activity = this.activity, maxWait: Duration = 4.seconds) =
        controller.showOnColdStart(activity, maxWait) { coldStartResults += it }

    val appOpenAds: List<FakeLoadedFullScreenAd> get() = h.adsSdk.fullScreenAds.filter { it.format == AdFormat.APP_OPEN }

    /** App open shows so far. */
    val shows: Int get() = appOpenAds.sumOf { it.showCount }

    /** The app open ad on screen now. */
    fun showing(): FakeLoadedFullScreenAd = appOpenAds.single { it.showCount == 1 && !it.released }

    fun loads(): Int = h.adsSdk.loadCount(adUnit)
}
