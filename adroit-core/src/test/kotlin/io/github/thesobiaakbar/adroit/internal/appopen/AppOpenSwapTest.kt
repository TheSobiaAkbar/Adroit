package io.github.thesobiaakbar.adroit.internal.appopen

import io.github.thesobiaakbar.adroit.AdFormat
import io.github.thesobiaakbar.adroit.InterstitialPlacement
import io.github.thesobiaakbar.adroit.config.TestIds
import io.github.thesobiaakbar.adroit.config.remote.AdOverrides
import io.github.thesobiaakbar.adroit.config.remote.PlacementOverride
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The app open ad swapped to an interstitial from Firebase: returns to the app show the interstitial instead. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppOpenSwapTest {
    @Test
    fun `a return to the app shows the interstitial standing in for the app open ad, with the same gates`() = runTest {
        val a = AppOpenHarness(this, withInterstitialEngine = true).loaded()
        a.h.live.update(
            a.h.effective.withOverrides(
                AdOverrides(
                    placements = mapOf(
                        "app_open" to PlacementOverride(format = AdFormat.INTERSTITIAL, adUnitIds = listOf(TestIds.unit(9))),
                    ),
                ),
            ),
        )
        a.h.run(1_000)
        val standIn = InterstitialPlacement("app_open")
        assertTrue(a.interstitialEngine!!.isReady(standIn))
        assertTrue("the app open ad was released", a.appOpenAds.all { it.released })

        a.returnToApp()

        val interstitial = a.h.adsSdk.fullScreenAds.single { it.format == AdFormat.INTERSTITIAL && it.showCount == 1 }
        assertEquals(listOf(a.activity), interstitial.shownIn)
        assertEquals(0, a.shows)
        interstitial.simulateFullShow()
        a.h.run()
        // Counted as the app open ad: its cooldown now applies to the next return.
        assertEquals(1, a.h.frequency.showsThisSession(APP_OPEN))
        a.returnToApp()
        assertEquals(1, a.h.adsSdk.fullScreenAds.sumOf { it.showCount })
    }
}
