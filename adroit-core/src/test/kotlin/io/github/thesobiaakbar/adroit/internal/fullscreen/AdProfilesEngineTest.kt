package io.github.thesobiaakbar.adroit.internal.fullscreen

import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.AdsState
import io.github.thesobiaakbar.adroit.FullScreenResult
import io.github.thesobiaakbar.adroit.InterstitialPlacement
import io.github.thesobiaakbar.adroit.UnavailableReason
import io.github.thesobiaakbar.adroit.config.AdDefinition
import io.github.thesobiaakbar.adroit.config.AdMobConfig
import io.github.thesobiaakbar.adroit.config.AdProfile
import io.github.thesobiaakbar.adroit.config.AdSettings
import io.github.thesobiaakbar.adroit.config.PreloadStrategy
import io.github.thesobiaakbar.adroit.fakes.TestClock
import io.github.thesobiaakbar.adroit.AdFormat
import io.github.thesobiaakbar.adroit.internal.config.AdProfiles
import io.github.thesobiaakbar.adroit.config.TestIds
import io.github.thesobiaakbar.adroit.config.remote.AdOverrides
import io.github.thesobiaakbar.adroit.config.remote.PlacementOverride
import io.github.thesobiaakbar.adroit.fakes.FakeAdsSdk.ScriptedLoad
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
    private fun config(block: io.github.thesobiaakbar.adroit.config.PlacementsBuilder.() -> Unit): AdMobConfig = engineConfig {
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

    private fun TestScope.harness(block: io.github.thesobiaakbar.adroit.config.PlacementsBuilder.() -> Unit) =
        EngineHarness(this, config(block)).apply { ready(); run(1_000) }

    // --- Rotation ---

    @Test
    fun `each load uses the next ad unit, back to the first after the last`() = runTest {
        val h = harness { interstitial(history, ids) { retry = EXACT_RETRY } }
        repeat(3) { h.fullShow() }
        assertEquals(ids + ids.first(), h.loadedUnits())
        val shownUnits = h.events.recent().filterIsInstance<io.github.thesobiaakbar.adroit.events.AdEvent.Shown>().map { it.adUnitId }
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

    // --- Loading only when asked ---

    @Test
    fun `a manual ad loads only when asked, and a failed load isn't retried`() = runTest {
        val h = EngineHarness(this, config { interstitial(history, ids) { preload = PreloadStrategy.NONE; retry = EXACT_RETRY } })
        h.ready()
        h.run(60_000)
        assertEquals("nothing loads at start", emptyList<String>(), h.loadedUnits())

        h.show(history)
        h.run(60_000)
        assertEquals(FullScreenResult.NotShown(AdError.NotReady), h.results.single())
        assertEquals("a show without an ad doesn't load one for later", emptyList<String>(), h.loadedUnits())

        h.adsSdk.enqueueLoad(ids[0], ScriptedLoad.Failure(AdError.NoFill(), retryable = true))
        h.engine.preload(history)
        h.run(600_000)
        assertEquals("no retry", listOf(ids[0]), h.loadedUnits())

        h.engine.preload(history)
        h.run(1_000)
        assertEquals("the next preload is the retry", ids.take(2), h.loadedUnits())
        assertTrue(h.engine.isReady(history))
    }

    @Test
    fun `a manual ad isn't reloaded after its show, nor after it expires`() = runTest {
        val h = harness { interstitial(history, ids) { preload = PreloadStrategy.NONE } }
        h.engine.preload(history)
        h.run(1_000)
        h.fullShow()
        assertTrue(h.results.last() is FullScreenResult.Dismissed)
        assertEquals(listOf(ids[0]), h.loadedUnits())

        h.engine.preload(history)
        h.run(1_000)
        h.run(2 * 3_600_000L) // past the interstitial TTL
        assertTrue(!h.engine.isReady(history))
        assertEquals(ids.take(2), h.loadedUnits())
    }

    @Test
    fun `a remote value switching an ad to auto loads it at once`() = runTest {
        val h = harness { interstitial(history, ids) { preload = PreloadStrategy.NONE } }
        assertEquals(emptyList<String>(), h.loadedUnits())
        val auto = PlacementOverride(preload = PreloadStrategy.AFTER_READY)
        h.live.update(h.live.current.withOverrides(AdOverrides(placements = mapOf("HistoryAd" to auto))))
        h.run(1_000)
        assertEquals(listOf(ids[0]), h.loadedUnits())
    }

    @Test
    fun `abandoning the splash ad stops its retries and drops a late ad, unless its ad unit is shared`() = runTest {
        val alone = EngineHarness(this, config { interstitial(splash, TestIds.unit(1)) { this.splash = true; retry = EXACT_RETRY } })
        alone.adsSdk.enqueueLoad(TestIds.unit(1), ScriptedLoad.Failure(AdError.NoFill(), retryable = true))
        alone.ready()
        alone.run(1_000)
        alone.engine.abandon(splash)
        alone.run(600_000)
        assertEquals(1, alone.adsSdk.loadCount(TestIds.unit(1)))
        assertTrue(!alone.engine.isReady(splash))

        val shared = EngineHarness(
            this,
            config {
                interstitial(splash, TestIds.unit(1)) { this.splash = true; retry = EXACT_RETRY }
                interstitial(history, TestIds.unit(1)) { retry = EXACT_RETRY }
            },
        )
        shared.adsSdk.enqueueLoad(TestIds.unit(1), ScriptedLoad.Failure(AdError.NoFill(), retryable = true))
        shared.ready()
        shared.run(1_000)
        shared.engine.abandon(splash)
        shared.run(60_000)
        assertEquals("HistoryAd still wants it", 2, shared.adsSdk.loadCount(TestIds.unit(1)))
        assertTrue(shared.engine.isReady(history))
    }

    // --- loadAgain ---

    private enum class Defs(override val defaults: AdProfile) : AdDefinition {
        Again(AdProfile.interstitial(ids = listOf(TestIds.unit(1), TestIds.unit(2)), loadAgain = true)),
        Once(AdProfile.interstitial(ids = listOf(TestIds.unit(5), TestIds.unit(6)))),
        Slow(AdProfile.interstitial(ids = listOf(TestIds.unit(8)), loadTimeoutSeconds = 3)),
        NoWait(AdProfile.interstitial(ids = listOf(TestIds.unit(9)), loadTimeoutSeconds = 0)),
        Counted(AdProfile.interstitial(ids = listOf(TestIds.unit(10)), counter = 2)),
        Quiet(AdProfile.interstitial(ids = listOf(TestIds.unit(11)), showLoading = false)),
        LongLoading(AdProfile.interstitial(ids = listOf(TestIds.unit(12)), loadingSeconds = 3)),
        Splash(AdProfile.interstitial(ids = listOf(TestIds.unit(13)), splash = true)),
    }

    private val again = InterstitialPlacement("Again")
    private val once = InterstitialPlacement("Once")

    private fun TestScope.profilesHarness(loading: Boolean = false): EngineHarness = EngineHarness(
        this,
        engineConfig {
            frequency { minIntervalBetweenFullScreenAds = Duration.ZERO }
            profiles(Defs.entries, AdSettings())
        },
        loadingScreens = if (loading) FakeLoadingScreens(TestClock(testScheduler)) else null,
    ).apply { ready(); run(60_000) }

    @Test
    fun `with loadAgain the next ad unit loads right after each show, without it nothing loads`() = runTest {
        val h = profilesHarness()
        assertEquals("profiles load only when asked", emptyList<String>(), h.loadedUnits())

        h.engine.preload(again)
        h.engine.preload(once)
        h.run(1_000)
        h.fullShow(again)
        assertEquals(listOf(TestIds.unit(1), TestIds.unit(5), TestIds.unit(2)), h.loadedUnits())
        assertTrue(h.engine.isReady(again))
        h.fullShow(again)
        assertEquals("back to the first ad unit", TestIds.unit(1), h.loadedUnits().last())

        val before = h.loadedUnits().size
        h.fullShow(once)
        h.run(600_000)
        assertEquals("Once waits for the app to ask", before, h.loadedUnits().size)
        assertTrue(!h.engine.isReady(once))
    }

    @Test
    fun `a loadAgain load that fails isn't retried`() = runTest {
        val h = profilesHarness()
        h.engine.preload(again)
        h.run(1_000)
        h.adsSdk.enqueueLoad(TestIds.unit(2), ScriptedLoad.Failure(AdError.NoFill(), retryable = true))
        h.fullShow(again)
        h.run(600_000)
        assertEquals(listOf(TestIds.unit(1), TestIds.unit(2)), h.loadedUnits())
    }

    @Test
    fun `Firebase can turn loadAgain on`() = runTest {
        val h = profilesHarness()
        val on = PlacementOverride().also { it.loadAgain = true }
        h.live.update(h.live.current.withOverrides(AdOverrides(placements = mapOf("Once" to on))))
        h.engine.preload(once)
        h.run(1_000)
        h.fullShow(once)
        assertEquals(listOf(TestIds.unit(5), TestIds.unit(6)), h.loadedUnits())
    }

    // --- Load timeout (PROMPT 29A) ---

    private val slow = InterstitialPlacement("Slow")

    @Test
    fun `an ad that isn't loaded is requested and shown if it arrives within the load timeout`() = runTest {
        val h = profilesHarness()
        h.adsSdk.enqueueLoad(TestIds.unit(5), ScriptedLoad.Success(), delayMs = 5_000)
        h.show(once)
        h.run(4_900)
        assertTrue("still waiting, within the default 8 s", h.results.isEmpty())
        h.run(200)
        h.lastAd().simulateFullShow()
        h.run(1_000)
        assertEquals(listOf(FullScreenResult.Dismissed(clicked = false)), h.results)
    }

    @Test
    fun `past the load timeout the show gives up, and the late ad is kept for the next show`() = runTest {
        val h = profilesHarness()
        h.adsSdk.enqueueLoad(TestIds.unit(8), ScriptedLoad.Success(), delayMs = 10_000)
        h.show(slow)
        h.run(3_100)
        assertEquals(listOf(FullScreenResult.NotShown(AdError.Timeout())), h.results)
        h.run(10_000)
        assertTrue("kept", h.engine.isReady(slow))

        h.fullShow(slow)
        assertTrue(h.results.last() is FullScreenResult.Dismissed)
        assertEquals("one request, the late one, served the next show", 1, h.adsSdk.loadCount(TestIds.unit(8)))
    }

    @Test
    fun `a load timeout of 0 doesn't wait, and placements that aren't profiles never wait`() = runTest {
        val h = profilesHarness()
        h.show(InterstitialPlacement("NoWait"))
        h.run()
        assertEquals(listOf(FullScreenResult.NotShown(AdError.NotReady)), h.results)

        val dsl = EngineHarness(this, config { interstitial(history, ids) { preload = PreloadStrategy.NONE } })
        dsl.ready()
        dsl.show(history)
        dsl.run()
        assertEquals(listOf(FullScreenResult.NotShown(AdError.NotReady)), dsl.results)
    }

    @Test
    fun `a second show while one waits is AlreadyShowing, and a failed load ends the wait at once`() = runTest {
        val h = profilesHarness()
        h.adsSdk.enqueueLoad(TestIds.unit(5), ScriptedLoad.Failure(AdError.NoFill(), retryable = true), delayMs = 1_000)
        h.show(once)
        h.show(again)
        h.run(100)
        assertEquals(listOf(FullScreenResult.NotShown(AdError.AlreadyShowing)), h.results)
        h.run(1_000)
        assertEquals("nothing retries it, so the wait ends with the failure", FullScreenResult.NotShown(AdError.NoFill()), h.results.last())
    }

    @Test
    fun `the counter counts the trigger once, even when the show waits for the ad`() = runTest {
        val h = profilesHarness()
        val counted = InterstitialPlacement("Counted")
        h.adsSdk.enqueueLoad(TestIds.unit(10), ScriptedLoad.Success(), delayMs = 2_000)
        h.show(counted)
        h.run()
        assertEquals(FullScreenResult.NotShown(AdError.CounterNotReached(1)), h.results.single())
        h.show(counted)
        h.run(2_100)
        h.lastAd().simulateFullShow()
        h.run(1_000)
        assertTrue(h.results.last() is FullScreenResult.Dismissed)
        h.show(counted)
        h.run()
        assertEquals("counting starts over after the show", FullScreenResult.NotShown(AdError.CounterNotReached(1)), h.results.last())
    }

    @Test
    fun `the load timeout comes from AdSettings, and Firebase can change it for all ads or one`() = runTest {
        val h = profilesHarness()
        h.adsSdk.enqueueLoad(TestIds.unit(5), ScriptedLoad.Pending)
        val remote = AdProfiles.parseRemote(
            mapOf("Once" to AdFormat.INTERSTITIAL, "Slow" to AdFormat.INTERSTITIAL),
            mapOf("Slow" to """{ "loadTimeoutSeconds": 1 }"""),
            settings = """{ "loadTimeoutSeconds": 2 }""",
        )
        h.live.update(h.live.current.withOverrides(remote.overrides))
        h.show(once)
        h.run(1_900)
        assertTrue(h.results.isEmpty())
        h.run(200)
        assertEquals("AdSettings from Firebase: 2 s", listOf(FullScreenResult.NotShown(AdError.Timeout())), h.results)

        h.adsSdk.enqueueLoad(TestIds.unit(8), ScriptedLoad.Pending)
        h.show(slow)
        h.run(1_100)
        assertEquals("the ad's own Firebase value wins: 1 s", 2, h.results.size)
    }

    // --- Loading screen (PROMPT 29B) ---

    private fun EngineHarness.screens() = checkNotNull(loadingScreens).screens

    @Test
    fun `a ready ad shows after a loading screen of loadingSeconds, which goes when the ad opens`() = runTest {
        val h = profilesHarness(loading = true)
        h.engine.preload(once)
        h.run(1_000)
        val start = h.clock.elapsedRealtimeMillis()
        h.show(once)
        h.run(900)
        assertEquals(1, h.screens().size)
        assertEquals("not shown during the loading screen", 0, h.lastAd().showCount)
        h.run(200)
        assertEquals(1, h.lastAd().showCount)
        assertTrue("still up until the ad opens", h.loadingScreens!!.showing)
        h.lastAd().simulateShown()
        assertEquals("gone the moment the ad opens", start + 1_100, h.screens().single().dismissedAt)
        h.lastAd().simulateImpression()
        h.lastAd().simulateDismissed()
        h.run(1_000)
        assertEquals(listOf(FullScreenResult.Dismissed(clicked = false)), h.results)
    }

    @Test
    fun `the loading screen covers the wait, and the ad shows as soon as it arrives past loadingSeconds`() = runTest {
        val h = profilesHarness(loading = true)
        h.adsSdk.enqueueLoad(TestIds.unit(5), ScriptedLoad.Success(), delayMs = 3_000)
        h.show(once)
        h.run(100)
        assertTrue(h.loadingScreens!!.showing)
        h.run(3_000)
        assertEquals(1, h.lastAd().showCount)
        h.lastAd().simulateFailedToShow()
        h.run()
        assertTrue("a show failure removes it", !h.loadingScreens!!.showing)
    }

    @Test
    fun `the loading screen goes when the wait times out or the load fails`() = runTest {
        val h = profilesHarness(loading = true)
        h.adsSdk.enqueueLoad(TestIds.unit(5), ScriptedLoad.Pending)
        h.show(once)
        h.run(8_100)
        assertEquals(FullScreenResult.NotShown(AdError.Timeout()), h.results.single())
        assertTrue(!h.loadingScreens!!.showing)

        h.adsSdk.enqueueLoad(TestIds.unit(8), ScriptedLoad.Failure(AdError.NoFill(), retryable = true), delayMs = 500)
        h.show(slow)
        h.run(1_000)
        assertEquals(FullScreenResult.NotShown(AdError.NoFill()), h.results.last())
        assertEquals(2, h.screens().size)
        assertTrue(!h.loadingScreens!!.showing)
    }

    @Test
    fun `showLoading false shows without a loading screen, and loadingSeconds sets its length`() = runTest {
        val h = profilesHarness(loading = true)
        val quiet = InterstitialPlacement("Quiet")
        val long = InterstitialPlacement("LongLoading")
        listOf(quiet, long).forEach { h.engine.preload(it) }
        h.run(1_000)
        h.show(quiet)
        h.run()
        assertEquals(1, h.adsSdk.fullScreenAds[0].showCount)
        h.adsSdk.fullScreenAds[0].simulateFullShow()
        h.run(1_000)
        assertTrue("no screen so far", h.screens().isEmpty())

        h.show(long)
        h.run(2_900)
        assertEquals(0, h.adsSdk.fullScreenAds[1].showCount)
        h.run(200)
        assertEquals(1, h.adsSdk.fullScreenAds[1].showCount)
    }

    @Test
    fun `the splash ad gets no loading screen on the splash, but one when it's shown later from another screen`() = runTest {
        val h = profilesHarness(loading = true)
        val splashAd = InterstitialPlacement("Splash")
        h.engine.preload(splashAd)
        h.run(1_000)
        h.engine.showOnSplash(h.activity(), splashAd) { h.results += it }
        h.run()
        assertEquals(1, h.lastAd().showCount)
        h.lastAd().simulateFullShow()
        h.run(1_000)
        assertTrue("the splash is its screen", h.screens().isEmpty())

        // splashAdMode "later": nothing cached, AdSlots.showFullScreen waits for it behind a loading screen.
        h.adsSdk.enqueueLoad(TestIds.unit(13), ScriptedLoad.Success(), delayMs = 2_000)
        h.show(splashAd)
        h.run(100)
        assertTrue(h.loadingScreens!!.showing)
        h.run(2_000)
        assertEquals(1, h.lastAd().showCount)
        h.lastAd().simulateFullShow()
        h.run()
        assertEquals(FullScreenResult.Dismissed(clicked = false), h.results.last())
    }

    @Test
    fun `leaving the screen during the loading screen cancels the show and removes it`() = runTest {
        val h = profilesHarness(loading = true)
        h.engine.preload(once)
        h.run(1_000)
        val activity = h.activity()
        h.engine.show(activity, once) { h.results += it }
        h.run(500)
        h.tracker.invalidActivities += activity
        h.run(600)
        assertEquals(FullScreenResult.NotShown(AdError.ActivityUnavailable), h.results.single())
        assertTrue(!h.loadingScreens!!.showing)
        assertTrue("the ad stays for later", h.engine.isReady(once))
    }
}
