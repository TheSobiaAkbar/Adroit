package io.github.thesobiaakbar.adroit.config

import io.github.thesobiaakbar.adroit.AdFormat
import io.github.thesobiaakbar.adroit.BannerSize
import io.github.thesobiaakbar.adroit.internal.config.GoogleDemoAdUnits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins every demo ID to Google's published table. Literals are copied on purpose, not referenced.
 * Source: https://developers.google.com/admob/android/next-gen/test-ads (docs/architecture/01_SDK_RESEARCH.md §1.5).
 */
class GoogleDemoAdUnitsTest {
    @Test
    fun `values match Google's published demo IDs`() {
        assertEquals("ca-app-pub-3940256099942544", GoogleDemoAdUnits.DEMO_PUBLISHER_PREFIX)
        assertEquals("ca-app-pub-3940256099942544~3347511713", GoogleDemoAdUnits.SAMPLE_APP_ID)
        assertEquals("ca-app-pub-3940256099942544/9257395921", GoogleDemoAdUnits.APP_OPEN)
        assertEquals("ca-app-pub-3940256099942544/9214589741", GoogleDemoAdUnits.ADAPTIVE_BANNER)
        assertEquals("ca-app-pub-3940256099942544/6300978111", GoogleDemoAdUnits.FIXED_SIZE_BANNER)
        assertEquals("ca-app-pub-3940256099942544/1033173712", GoogleDemoAdUnits.INTERSTITIAL)
        assertEquals("ca-app-pub-3940256099942544/5224354917", GoogleDemoAdUnits.REWARDED)
        assertEquals("ca-app-pub-3940256099942544/5354046379", GoogleDemoAdUnits.REWARDED_INTERSTITIAL)
        assertEquals("ca-app-pub-3940256099942544/2247696110", GoogleDemoAdUnits.NATIVE)
        assertEquals("ca-app-pub-3940256099942544/1044960115", GoogleDemoAdUnits.NATIVE_VIDEO)
    }

    @Test
    fun `forFormat maps each format and banner size`() {
        assertEquals("ca-app-pub-3940256099942544/9257395921", GoogleDemoAdUnits.forFormat(AdFormat.APP_OPEN))
        assertEquals("ca-app-pub-3940256099942544/1033173712", GoogleDemoAdUnits.forFormat(AdFormat.INTERSTITIAL))
        assertEquals("ca-app-pub-3940256099942544/5224354917", GoogleDemoAdUnits.forFormat(AdFormat.REWARDED))
        assertEquals(
            "ca-app-pub-3940256099942544/5354046379",
            GoogleDemoAdUnits.forFormat(AdFormat.REWARDED_INTERSTITIAL),
        )
        assertEquals("ca-app-pub-3940256099942544/2247696110", GoogleDemoAdUnits.forFormat(AdFormat.NATIVE))
        assertEquals("ca-app-pub-3940256099942544/9214589741", GoogleDemoAdUnits.forFormat(AdFormat.BANNER))
        assertEquals(
            "ca-app-pub-3940256099942544/9214589741",
            GoogleDemoAdUnits.forFormat(AdFormat.BANNER, BannerSize.AnchoredAdaptive),
        )
        assertEquals(
            "ca-app-pub-3940256099942544/9214589741",
            GoogleDemoAdUnits.forFormat(AdFormat.BANNER, BannerSize.InlineAdaptive(maxHeightDp = 250)),
        )
        assertEquals(
            "ca-app-pub-3940256099942544/6300978111",
            GoogleDemoAdUnits.forFormat(AdFormat.BANNER, BannerSize.Standard),
        )
        assertEquals(
            "ca-app-pub-3940256099942544/6300978111",
            GoogleDemoAdUnits.forFormat(AdFormat.BANNER, BannerSize.MediumRectangle),
        )
    }

    @Test
    fun `every demo ID is well formed and recognised as demo`() {
        val adUnits = listOf(
            GoogleDemoAdUnits.APP_OPEN, GoogleDemoAdUnits.ADAPTIVE_BANNER, GoogleDemoAdUnits.FIXED_SIZE_BANNER,
            GoogleDemoAdUnits.INTERSTITIAL, GoogleDemoAdUnits.REWARDED, GoogleDemoAdUnits.REWARDED_INTERSTITIAL,
            GoogleDemoAdUnits.NATIVE, GoogleDemoAdUnits.NATIVE_VIDEO,
        )
        adUnits.forEach {
            assertTrue(it, AdUnitId(it).isWellFormed)
            assertTrue(it, GoogleDemoAdUnits.isDemo(it))
        }
        assertTrue(AdMobAppId(GoogleDemoAdUnits.SAMPLE_APP_ID).isWellFormed)
        assertTrue(GoogleDemoAdUnits.isDemo(GoogleDemoAdUnits.SAMPLE_APP_ID))
        assertFalse(GoogleDemoAdUnits.isDemo("ca-app-pub-1234567890123456/1234567890"))
    }

    @Test
    fun `id shape checks`() {
        assertTrue(AdUnitId("ca-app-pub-1234567890123456/1234567890").isWellFormed)
        assertFalse(AdUnitId("ca-app-pub-1234567890123456~1234567890").isWellFormed)
        assertFalse(AdUnitId("ca-app-pub-123456789012345/1234567890").isWellFormed)
        assertFalse(AdUnitId(" ca-app-pub-1234567890123456/1234567890").isWellFormed)
        assertFalse(AdUnitId("").isWellFormed)
        assertTrue(AdMobAppId("ca-app-pub-1234567890123456~1234567890").isWellFormed)
        assertFalse(AdMobAppId("ca-app-pub-1234567890123456/1234567890").isWellFormed)
    }
}
