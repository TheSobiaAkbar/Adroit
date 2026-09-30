package io.github.thesobiaakbar.adroit.docs

import androidx.test.core.app.ApplicationProvider
import io.github.thesobiaakbar.adroit.BannerSize
import io.github.thesobiaakbar.adroit.CollapsibleBannerPosition
import io.github.thesobiaakbar.adroit.NativeStyle
import io.github.thesobiaakbar.adroit.config.BannerPlacementConfig
import io.github.thesobiaakbar.adroit.config.InterstitialPlacementConfig
import io.github.thesobiaakbar.adroit.config.NativePlacementConfig
import io.github.thesobiaakbar.adroit.config.adMobConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The example ads file in docs/AD_PROFILES.md (res/raw/ads_config.json here) configures what the docs describe. */
@RunWith(RobolectricTestRunner::class)
class AdsFileExampleTest {
    @Test
    fun `the documented ads file defines its four ads with the documented values`() {
        val config = adMobConfig { profiles(ApplicationProvider.getApplicationContext(), R.raw.ads_config) }
        val ads = config.placements.associateBy { it.placement.key }
        assertEquals(setOf("SplashAd", "HistoryAd", "HomeBanner", "FeedNative"), ads.keys)
        assertTrue((ads.getValue("SplashAd") as InterstitialPlacementConfig).splash)
        val history = ads.getValue("HistoryAd") as InterstitialPlacementConfig
        assertEquals(2, history.adUnitIds.size)
        assertEquals(3, history.frequency.counter)
        assertEquals(5, history.frequency.maxPerSession)
        assertEquals(
            BannerSize.Collapsible(CollapsibleBannerPosition.BOTTOM),
            (ads.getValue("HomeBanner") as BannerPlacementConfig).size,
        )
        assertEquals(NativeStyle.LARGE_COMPACT, (ads.getValue("FeedNative") as NativePlacementConfig).style)
        assertEquals(Int.MAX_VALUE, config.frequency.maxInterstitialsPerSession)
    }

    @Test
    fun `the documented enum configures the same ads as the documented file`() {
        val fromFile = adMobConfig { profiles(ApplicationProvider.getApplicationContext(), R.raw.ads_config) }
        val fromEnum = adMobConfig { profiles(AppAds.entries) }
        assertEquals(fromFile.placements, fromEnum.placements)
    }
}
