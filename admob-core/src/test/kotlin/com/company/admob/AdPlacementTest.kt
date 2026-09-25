package com.company.admob

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AdPlacementTest {

    @Test
    fun `valid keys are accepted`() {
        listOf("a", "home_banner", "level_2_complete", "x".repeat(40)).forEach { key ->
            assertEquals(key, InterstitialPlacement(key).key)
        }
    }

    @Test
    fun `invalid keys are rejected`() {
        listOf("", "Home", "home-banner", "home banner", "x".repeat(41), "ümlaut").forEach { key ->
            assertThrows("key '$key'", IllegalArgumentException::class.java) { BannerPlacement(key) }
        }
    }

    @Test
    fun `each placement class reports its format`() {
        assertEquals(AdFormat.BANNER, BannerPlacement("p").format)
        assertEquals(AdFormat.INTERSTITIAL, InterstitialPlacement("p").format)
        assertEquals(AdFormat.REWARDED, RewardedPlacement("p").format)
        assertEquals(AdFormat.REWARDED_INTERSTITIAL, RewardedInterstitialPlacement("p").format)
        assertEquals(AdFormat.APP_OPEN, AppOpenPlacement("p").format)
        assertEquals(AdFormat.NATIVE, NativePlacement("p").format)
    }

    @Test
    fun `equality is by format and key`() {
        assertEquals(InterstitialPlacement("level"), InterstitialPlacement("level"))
        assertEquals(InterstitialPlacement("level").hashCode(), InterstitialPlacement("level").hashCode())
        assertNotEquals(InterstitialPlacement("level"), InterstitialPlacement("other"))
        assertNotEquals(InterstitialPlacement("level") as AdPlacement, RewardedPlacement("level") as AdPlacement)
    }

    @Test
    fun `toString names the type and key`() {
        assertEquals("RewardedPlacement(extra_life)", RewardedPlacement("extra_life").toString())
    }
}
