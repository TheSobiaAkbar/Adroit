package com.company.admob.internal.fullscreen

import com.company.admob.AdError
import com.company.admob.AdsState
import com.company.admob.FullScreenResult
import com.company.admob.InterstitialPlacement
import com.company.admob.UnavailableReason
import com.company.admob.config.AdMobConfig
import com.company.admob.config.TestIds
import com.company.admob.config.remote.AdOverrides
import com.company.admob.config.remote.PlacementOverride
import com.company.admob.fakes.FakeAdsSdk.ScriptedLoad
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Rotation, counter, overall interstitial cap and splash (05_AD_PROFILES.md §4). */
@RunWith(RobolectricTestRunner::class)
class AdProfilesEngineTest {
    private val history = InterstitialPlacement("HistoryAd")
    private val splash = InterstitialPlacement("SplashAd")
    private val ids = listOf(TestIds.unit(1), TestIds.unit(2), TestIds.unit(3))

    /** No global gap, so only the rules under test apply. */
    private fun config(block: com.company.admob.config.PlacementsBuilder.() -> Unit): AdMobConfig = engineConfig {
        frequency { minIntervalBetweenFullScreenAds = Duration.ZERO }
        placements(block)
    }

    private fun EngineHarness.fullShow(placement: InterstitialPlacement = history) {
        show(placement)
        run()
        // The ad on screen: shown and not yet released (a reload may have loaded a newer one since).
        adsSdk.fullScreenAds.last { it.showCount > 0 && !it.released }.simulateFullShow()
        run(1_000)
    }

    private fun EngineHarness.loadedUnits() = adsSdk.loadCalls.map { it.adUnitId }

    private fun TestScope.harness(block: com.company.admob.config.PlacementsBuilder.() -> Unit) =
        EngineHarness(this, config(block)).apply { ready(); run(1_000) }

    // --- Rotation ---

    @Test
    fun `each load uses the next ad unit, back to the first after the last`() = runTest {
        val h = harness { interstitial(history, ids) { retry = EXACT_RETRY } }
        repeat(3) { h.fullShow() }
        assertEquals(ids + ids.first(), h.loadedUnits())
        val shownUnits = h.events.recent().filterIsInstance<com.company.admob.events.AdEvent.Shown>().map { it.adUnitId }
        assertEquals("events name the unit each ad came from", ids, shownUnits)
    }

    @Test
    fun `a failed load retries with the next ad unit`() = runTest {
        val h = EngineHarness(this, config { interstitial(history, ids) { retry = EXACT_RETRY } })
        h.adsSdk.enqueueLoad(ids[0], ScriptedLoad.Failure(AdError.NoFill(), retryable = true))
        h.ready()
        h.run(60_000)
        assertEquals(ids.take(2), h.loadedUnits())
        assertTrue(h.engine.isReady(history))
    }

    @Test
    fun `placements with the same list share one cache and one position`() = runTest {
        val menu = InterstitialPlacement("menu_ad")
        val h = harness {
            interstitial(history, ids)
            interstitial(menu, ids)
        }
        assertEquals(listOf(ids[0]), h.loadedUnits())
        h.fullShow(menu)
        assertEquals(ids.take(2), h.loadedUnits())
        assertTrue(h.engine.isReady(history))
    }

    @Test
    fun `a remotely changed list starts at its first ad unit`() = runTest {
        val h = harness { interstitial(history, ids) }
        h.fullShow()
        val remote = listOf(TestIds.unit(8), TestIds.unit(9))
        h.live.update(h.live.current.withOverrides(AdOverrides(placements = mapOf("HistoryAd" to PlacementOverride(adUnitIds = remote)))))
        h.run(1_000)
        assertEquals(listOf(ids[0], ids[1], remote[0]), h.loadedUnits())
    }

    // --- Counter ---

    @Test
    fun `with counter 3 the ad shows on the 3rd call, then the 6th`() = runTest {
        val h = harness { interstitial(history, TestIds.unit(1)) { frequency { counter = 3 } } }
        repeat(2) { h.show(history) }
        h.run()
        assertEquals(
            listOf(FullScreenResult.NotShown(AdError.CounterNotReached(2)), FullScreenResult.NotShown(AdError.CounterNotReached(1))),
            h.results,
        )
        h.fullShow()
        assertTrue(h.results.last() is FullScreenResult.Dismissed)
        h.results.clear()
        repeat(2) { h.show(history) }
        h.run()
        assertEquals(listOf(AdError.CounterNotReached(2), AdError.CounterNotReached(1)), h.results.map { (it as FullScreenResult.NotShown).error })
        h.fullShow()
        assertTrue(h.results.last() is FullScreenResult.Dismissed)
    }

    @Test
    fun `a due counter stays due until an ad actually shows`() = runTest {
        val h = EngineHarness(this, config { interstitial(history, TestIds.unit(1)) { frequency { counter = 2 }; retry = EXACT_RETRY } })
        h.adsSdk.enqueueLoad(TestIds.unit(1), ScriptedLoad.Failure(AdError.NoFill(), retryable = true))
        h.ready()
        h.show(history)
        h.show(history)
        h.run()
        assertEquals(listOf(AdError.CounterNotReached(1), AdError.NotReady), h.results.map { (it as FullScreenResult.NotShown).error })
        h.run(60_000) // the retry loads an ad
        h.fullShow()
        assertTrue("the next call shows, without counting to 2 again", h.results.last() is FullScreenResult.Dismissed)
    }

    @Test
    fun `while a counter is set the timer is ignored`() = runTest {
        val h = harness {
            interstitial(history, TestIds.unit(1)) { frequency { counter = 1; minInterval = 60.seconds } }
        }
        h.fullShow()
        h.fullShow()
        assertEquals(2, h.results.count { it is FullScreenResult.Dismissed })
    }

    @Test
    fun `without a counter the timer applies`() = runTest {
        val h = harness { interstitial(history, TestIds.unit(1)) { frequency { minInterval = 60.seconds } } }
        h.fullShow()
        h.show(history)
        h.run()
        assertTrue((h.results.last() as FullScreenResult.NotShown).error is AdError.FrequencyCapped)
    }

    // --- Overall interstitial cap ---

    @Test
    fun `the overall cap stops interstitials even below their own limit, and ignores splash ones`() = runTest {
        val menu = InterstitialPlacement("menu_ad")
        val h = EngineHarness(
            this,
            engineConfig {
                frequency {
                    minIntervalBetweenFullScreenAds = Duration.ZERO
                    maxInterstitialsPerSession = 2
                }
                placements {
                    interstitial(history, TestIds.unit(1)) { frequency { maxPerSession = 10 } }
                    interstitial(menu, TestIds.unit(2))
                    interstitial(splash, TestIds.unit(3)) { this.splash = true }
                }
            },
        ).apply { ready(); run(1_000) }
        h.fullShow(history)
        h.fullShow(splash)
        h.fullShow(menu)
        h.show(history)
        h.run()
        assertEquals(FullScreenResult.NotShown(AdError.FrequencyCapped(Duration.INFINITE)), h.results.last())
        // A splash ad isn't reloaded after its show; load another one explicitly.
        h.engine.preload(splash)
        h.run(1_000)
        h.fullShow(splash)
        assertTrue("splash ads don't count and aren't capped", h.results.last() is FullScreenResult.Dismissed)
    }

    @Test
    fun `a remote overall cap replaces the local one`() = runTest {
        val h = harness { interstitial(history, TestIds.unit(1)) }
        h.live.update(h.live.current.withOverrides(AdOverrides(maxInterstitialsPerSession = 1)))
        h.fullShow()
        h.show(history)
        h.run()
        assertEquals(FullScreenResult.NotShown(AdError.FrequencyCapped(Duration.INFINITE)), h.results.last())
    }

    // --- Splash ---

    @Test
    fun `showOnSplash waits for readiness and an ad, then shows`() = runTest {
        val h = EngineHarness(this, config { interstitial(splash, TestIds.unit(1)) { this.splash = true } })
        h.engine.showOnSplash(h.activity(), splash) { h.results += it }
        h.run(500)
        assertTrue(h.results.isEmpty())
        h.ready()
        h.run(500)
        h.lastAd().simulateFullShow()
        h.run(1_000)
        assertEquals(listOf(FullScreenResult.Dismissed(clicked = false)), h.results)
    }

    @Test
    fun `a shown splash ad isn't reloaded, unless another placement shares its ad unit`() = runTest {
        val alone = EngineHarness(this, config { interstitial(splash, TestIds.unit(1)) { this.splash = true } })
        alone.ready()
        alone.run(1_000)
        alone.engine.show(alone.activity(), splash) { alone.results += it }
        alone.run()
        alone.lastAd().simulateFullShow()
        alone.run(1_000)
        assertEquals(1, alone.adsSdk.loadCount(TestIds.unit(1)))

        val shared = EngineHarness(
            this,
            config {
                interstitial(splash, TestIds.unit(1)) { this.splash = true }
                interstitial(history, TestIds.unit(1))
            },
        )
        shared.ready()
        shared.run(1_000)
        shared.engine.show(shared.activity(), splash) { shared.results += it }
        shared.run()
        shared.lastAd().simulateFullShow()
        shared.run(1_000)
        assertEquals(2, shared.adsSdk.loadCount(TestIds.unit(1)))
    }

    @Test
    fun `showOnSplash gives up after the splash timeout, exactly once`() = runTest {
        val h = EngineHarness(
            this,
            config { interstitial(splash, TestIds.unit(1)) { this.splash = true; splashTimeout = 3.seconds } },
        )
        h.engine.showOnSplash(h.activity(), splash) { h.results += it }
        h.run(3_100)
        h.ready()
        h.run(1_000)
        assertEquals(listOf(FullScreenResult.NotShown(AdError.Timeout())), h.results)
    }

    @Test
    fun `showOnSplash ends at once when ads are off, and a second call while waiting is AlreadyShowing`() = runTest {
        val h = EngineHarness(this, config { interstitial(splash, TestIds.unit(1)) { this.splash = true } })
        h.engine.showOnSplash(h.activity(), splash) { h.results += it }
        h.engine.showOnSplash(h.activity(), splash) { h.results += it }
        h.run()
        h.readiness.state.value = AdsState.Unavailable(UnavailableReason.CONSENT_NOT_OBTAINED)
        h.run(100)
        assertEquals(
            listOf(FullScreenResult.NotShown(AdError.AlreadyShowing), FullScreenResult.NotShown(AdError.ConsentRequired)),
            h.results,
        )
    }
}
