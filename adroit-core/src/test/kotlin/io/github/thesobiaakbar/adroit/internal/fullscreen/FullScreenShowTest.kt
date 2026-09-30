package io.github.thesobiaakbar.adroit.internal.fullscreen

import io.github.thesobiaakbar.adroit.AdAvailability
import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.AdsState
import io.github.thesobiaakbar.adroit.FullScreenResult
import io.github.thesobiaakbar.adroit.InterstitialPlacement
import io.github.thesobiaakbar.adroit.UnavailableReason
import io.github.thesobiaakbar.adroit.config.AdEnvironment
import io.github.thesobiaakbar.adroit.config.PreloadStrategy
import io.github.thesobiaakbar.adroit.config.TestIds
import io.github.thesobiaakbar.adroit.config.adMobConfig
import io.github.thesobiaakbar.adroit.events.AdEvent
import io.github.thesobiaakbar.adroit.fakes.FakeAdsSdk.ScriptedLoad
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
class FullScreenShowTest {
    private fun kotlinx.coroutines.test.TestScope.loaded(): EngineHarness =
        EngineHarness(this).apply {
            ready()
            run(1_000)
        }

    private fun notShown(error: AdError) = FullScreenResult.NotShown(error)

    // --- Gates, in order ---

    @Test
    fun `gate 1 - readiness`() = runTest {
        val h = EngineHarness(this)
        val expected = mapOf(
            AdsState.AwaitingStart to AdError.NotInitialized,
            AdsState.Unavailable(UnavailableReason.CONSENT_NOT_OBTAINED) to AdError.ConsentRequired,
            AdsState.Unavailable(UnavailableReason.AD_FREE_USER) to AdError.Disabled(UnavailableReason.AD_FREE_USER),
            AdsState.Failed(AdError.SdkInitFailed()) to AdError.SdkInitFailed(),
        )
        for ((state, error) in expected) {
            h.readiness.state.value = state
            h.show()
        }
        h.run()
        assertEquals(expected.values.map(::notShown), h.results)
    }

    @Test
    fun `gate 2 - unknown placement, then disabled placement`() = runTest {
        val h = EngineHarness(
            this,
            engineConfig { placements { interstitial(LEVEL_END, TestIds.unit(1)) { enabled = false } } },
        )
        h.ready()
        h.show(MENU)
        h.show(LEVEL_END)
        h.run()
        assertTrue((h.results[0] as FullScreenResult.NotShown).error is AdError.InvalidConfiguration)
        assertEquals(notShown(AdError.Disabled(UnavailableReason.DISABLED_BY_CONFIG)), h.results[1])
    }

    @Test
    fun `gate 3 - an invalid Activity isn't used and the ad stays cached`() = runTest {
        val h = loaded()
        val activity = h.activity()
        h.tracker.invalidActivities += activity
        h.show(activity = activity)
        h.run()

        assertEquals(listOf(notShown(AdError.ActivityUnavailable)), h.results)
        assertEquals(0, h.lastAd().showCount)
        assertTrue(h.engine.isReady(LEVEL_END))
    }

    @Test
    fun `gate 4 - another full-screen ad is showing, and one ad is never shown twice`() = runTest {
        val h = loaded()
        h.show(LEVEL_END)
        h.show(MENU)
        h.show(LEVEL_END)
        h.run()

        assertEquals(listOf(notShown(AdError.AlreadyShowing), notShown(AdError.AlreadyShowing)), h.results)
        assertEquals(listOf(1, 0), h.adsSdk.fullScreenAds.map { it.showCount })
    }

    @Test
    fun `gate 5 - frequency cap after an impression`() = runTest {
        val h = loaded()
        h.show()
        h.run()
        h.adsSdk.fullScreenAds.first().simulateFullShow()
        h.run(1_000)
        h.show()
        h.run()

        assertEquals(notShown(AdError.FrequencyCapped(29.seconds)), h.results.last())
    }

    @Test
    fun `gate 6 - no ad yet is NotReady and starts a load`() = runTest {
        val h = EngineHarness(this)
        h.adsSdk.defaultLoad = ScriptedLoad.Pending
        h.ready()
        h.engine.clear(LEVEL_END)
        val before = h.adsSdk.loadCalls.size
        h.show()
        h.run()

        assertEquals(listOf(notShown(AdError.NotReady)), h.results)
        assertEquals(before + 1, h.adsSdk.loadCalls.size)
    }

    @Test
    fun `gate 6 - an expired ad isn't shown, and a new one loads`() = runTest {
        val h = loaded()
        val stale = h.adsSdk.fullScreenAds.first()
        h.foreground.moveToBackground() // no expiry job in the background: expiry is found at show time
        h.run(55 * 60_000L + 1)
        h.show()
        h.run()

        assertEquals(listOf(notShown(AdError.Expired)), h.results)
        assertEquals(0, stale.showCount)
        assertTrue(stale.released)
        assertEquals(3, h.adsSdk.loadCalls.size)
    }

    @Test
    fun `NONE placements don't load on a show miss`() = runTest {
        val h = EngineHarness(
            this,
            engineConfig { placements { interstitial(LEVEL_END, TestIds.unit(1)) { preload = PreloadStrategy.NONE } } },
        )
        h.ready()
        h.show()
        h.run()
        assertEquals(listOf(notShown(AdError.NotReady)), h.results)
        assertTrue(h.adsSdk.loadCalls.isEmpty())
    }

    // --- Show outcomes ---

    @Test
    fun `success - Dismissed(clicked), frequency recorded, coordinator released, reload`() = runTest {
        val h = loaded()
        h.show()
        h.run()
        val ad = h.adsSdk.fullScreenAds.first()
        assertTrue(h.coordinator.isShowing)
        assertEquals(AdAvailability.Unavailable, h.engine.availability(LEVEL_END).value)

        ad.simulateShown()
        ad.simulateImpression()
        ad.simulateClicked()
        ad.simulateDismissed()
        h.run()

        assertEquals(listOf(FullScreenResult.Dismissed(clicked = true)), h.results)
        assertFalse(h.coordinator.isShowing)
        assertTrue(ad.released)
        assertTrue(h.coordinator.lastClickAtElapsed != null)
        assertEquals(3, h.adsSdk.loadCalls.size)
        assertTrue(h.frequency.check(MENU) is io.github.thesobiaakbar.adroit.internal.reliability.FrequencyDecision.Capped)
    }

    @Test
    fun `show failure - NotShown(ShowFailed), coordinator released, reload`() = runTest {
        val h = loaded()
        h.show()
        h.run()
        val failure = AdError.ShowFailed()
        h.adsSdk.fullScreenAds.first().simulateFailedToShow(failure)
        h.run()

        assertEquals(listOf(notShown(failure)), h.results)
        assertFalse(h.coordinator.isShowing)
        assertEquals(3, h.adsSdk.loadCalls.size)
    }

    @Test
    fun `watchdog - no SDK callback means Timeout after 5 s, and late callbacks are ignored`() = runTest {
        val h = loaded()
        h.show()
        h.run()
        val ad = h.adsSdk.fullScreenAds.first()
        val listener = ad.listener!!

        h.run(4_999)
        assertTrue(h.results.isEmpty())
        h.run(1)
        assertEquals(listOf(notShown(AdError.Timeout())), h.results)
        assertFalse(h.coordinator.isShowing)
        assertTrue(ad.released)

        listener.onShown()
        listener.onDismissed()
        listener.onFailedToShow(AdError.ShowFailed())
        h.run()
        assertEquals(1, h.results.size)
    }

    @Test
    fun `markShown stops the watchdog, so a long ad still ends with Dismissed`() = runTest {
        val h = loaded()
        h.show()
        h.run()
        val ad = h.adsSdk.fullScreenAds.first()
        ad.simulateShown()
        h.run(60_000)
        ad.simulateDismissed()
        h.run()
        assertEquals(listOf(FullScreenResult.Dismissed(clicked = false)), h.results)
    }

    @Test
    fun `the result callback is never synchronous, even for a gate failure`() = runTest {
        val h = EngineHarness(this)
        var returned = false
        h.engine.show(h.activity(), LEVEL_END) { assertTrue("delivered before show() returned", returned) ; h.results += it }
        returned = true
        assertTrue(h.results.isEmpty())
        h.run()
        assertEquals(1, h.results.size)
    }

    @Test
    fun `blocked while cached releases the ad and disables, and Ready again reloads`() = runTest {
        val h = loaded()
        val ad = h.adsSdk.fullScreenAds.first()
        h.readiness.state.value = AdsState.Unavailable(UnavailableReason.AD_FREE_USER)
        h.engine.onAdsBlocked(UnavailableReason.AD_FREE_USER)
        h.run()

        assertTrue(ad.released)
        assertEquals(AdAvailability.Disabled, h.engine.availability(LEVEL_END).value)
        assertFalse(h.engine.isReady(LEVEL_END))

        h.ready()
        h.run(1_000)
        assertEquals(AdAvailability.Ready, h.engine.availability(LEVEL_END).value)
        assertEquals(4, h.adsSdk.loadCalls.size)
    }

    @Test
    fun `a load that completes after a block is discarded`() = runTest {
        val h = EngineHarness(this)
        h.adsSdk.enqueueLoad(h.adUnit(LEVEL_END), ScriptedLoad.Success(), delayMs = 1_000)
        h.ready()
        h.engine.onAdsBlocked(UnavailableReason.AD_FREE_USER)
        h.run(2_000)
        assertTrue(h.lastAd().released)
        assertEquals(AdAvailability.Disabled, h.engine.availability(LEVEL_END).value)
    }

    @Test
    fun `ad unit IDs are redacted in INFO logs in PRODUCTION`() = runTest {
        val unit = TestIds.unit(7)
        val h = EngineHarness(
            this,
            adMobConfig {
                environment = AdEnvironment.PRODUCTION
                placements { interstitial(InterstitialPlacement("level_end"), unit) }
            },
        )
        h.ready()
        h.run(1_000)
        h.show()
        h.run()
        assertTrue(h.infoLogs.any { it.contains("…" + unit.takeLast(4)) })
        assertNull(h.infoLogs.firstOrNull { it.contains(unit) })
    }
}
