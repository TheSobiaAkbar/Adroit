package io.github.thesobiaakbar.adroit.internal.runtime

import android.app.Activity
import android.os.Looper
import io.github.thesobiaakbar.adroit.AdsState
import io.github.thesobiaakbar.adroit.AppOpenPlacement
import io.github.thesobiaakbar.adroit.BannerPlacement
import io.github.thesobiaakbar.adroit.Adroit
import io.github.thesobiaakbar.adroit.ConsentStatus
import io.github.thesobiaakbar.adroit.InterstitialPlacement
import io.github.thesobiaakbar.adroit.NativePlacement
import io.github.thesobiaakbar.adroit.RewardedInterstitialPlacement
import io.github.thesobiaakbar.adroit.RewardedPlacement
import io.github.thesobiaakbar.adroit.config.AdEnvironment
import io.github.thesobiaakbar.adroit.config.PreloadStrategy
import io.github.thesobiaakbar.adroit.config.TestIds
import io.github.thesobiaakbar.adroit.events.AdEvent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/**
 * The performance audit's startup-request measurement (docs/audits/PERFORMANCE_AUDIT.md, measurement 3) on virtual
 * time: the ad requests in the first 10 seconds after Ready, with the placements of the Compose playground's default
 * configuration.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StartupRequestsTest {
    @After
    fun tearDown() = Adroit.resetForTests()

    @Test
    fun `release shape - the first 10 seconds after Ready request each AFTER_READY placement once`() = runTest {
        val requested = requestsIn10SecondsAfterReady(useDemoAdUnits = false)
        // "level" comes from testConfig's own interstitial placement.
        assertEquals(
            listOf("app_open", "interstitial", "level", "rewarded", "rewarded_interstitial", "templates"),
            requested.sorted(),
        )
    }

    @Test
    fun `debug shape - demo ad units are shared per format, and so are their caches`() = runTest {
        val requested = requestsIn10SecondsAfterReady(useDemoAdUnits = true)
        // One interstitial for both interstitial placements. The three native placements share one pool, sized by
        // the largest (feed's 2) and preloading because one of them is AFTER_READY (architecture §20).
        assertEquals(
            listOf("app_open", "level", "rewarded", "rewarded_interstitial", "templates", "templates"),
            requested.sorted(),
        )
    }

    private fun TestScope.requestsIn10SecondsAfterReady(useDemoAdUnits: Boolean): List<String> {
        val h = RuntimeHarness(this).apply {
            consentSdk.canRequestAds = true
            consentSdk.status = ConsentStatus.NOT_REQUIRED
        }
        val runtime = h.runtime(
            testConfig(h.logs) {
                if (!useDemoAdUnits) {
                    environment = AdEnvironment.STAGING
                    testing {
                        this.useDemoAdUnits = false
                        testDeviceIds += "TEST-DEVICE"
                    }
                }
                placements {
                    banner(BannerPlacement("anchored"), TestIds.unit(1))
                    banner(BannerPlacement("inline"), TestIds.unit(2))
                    interstitial(InterstitialPlacement("interstitial"), TestIds.unit(3))
                    rewarded(RewardedPlacement("rewarded"), TestIds.unit(4))
                    rewardedInterstitial(RewardedInterstitialPlacement("rewarded_interstitial"), TestIds.unit(5))
                    appOpen(AppOpenPlacement("app_open"), TestIds.unit(6))
                    native(NativePlacement("templates"), TestIds.unit(7)) { poolSize = 1 }
                    native(NativePlacement("feed"), TestIds.unit(8)) {
                        poolSize = 2
                        preload = PreloadStrategy.ON_DEMAND
                    }
                    native(NativePlacement("custom"), TestIds.unit(9)) { preload = PreloadStrategy.NONE }
                }
            },
        )
        h.settle()
        runtime.client.start(Robolectric.buildActivity(Activity::class.java).setup().get()) {}
        h.settle()
        assertEquals(AdsState.Ready, h.state.value)

        runFor(10_000)
        return h.bus.recent().filterIsInstance<AdEvent.LoadRequested>().map { it.placementKey }
    }

    /** Advances virtual time and Robolectric's main looper together, in 50 ms steps. */
    private fun TestScope.runFor(millis: Long) {
        repeat((millis / STEP_MS).toInt()) {
            advanceTimeBy(STEP_MS)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(STEP_MS))
        }
    }

    private companion object {
        const val STEP_MS = 50L
    }
}
