package com.company.admob.sample.xml

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.company.admob.AdFormat
import com.company.admob.CompanyAdMob
import com.company.admob.events.AdEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The ad definitions (`SampleAds`) on a device: every entry is configured under its name, test builds use Google's
 * demo ad units, every ad is on by default, and the Details banner (style `collapsible_bottom`) loads. Rotation is covered by unit tests; with demo ad units every ID is the same one here.
 */
@RequiresNetwork
@RunWith(AndroidJUnit4::class)
class AdProfilesDeviceTest {
    @Test
    fun theAdsFileConfiguresEveryAdAndTheCollapsibleBannerLoads() {
        ActivityScenario.launch(SplashActivity::class.java).use {
            // 30 s: the splash (consent, then up to 11 s for the splash ad), plus closing that ad. Its close button
            // is web content, exposed to UI Automator only a few seconds after the ad appears (Pixel 7a).
            awaitResumed<MainActivity>(timeoutMillis = 30_000)
            val placements = CompanyAdMob.diagnostics.snapshot().placements.associateBy { it.key }
            assertEquals(
                setOf(
                    "SplashAd", "LevelCompleteAd", "ExtraLifeRewarded", "BonusRewardedInterstitial", "AppOpenAd",
                    "HomeBanner", "DetailsBanner", "FeedNative", "ArticleNative",
                ),
                placements.keys,
            )
            assertTrue(placements.values.all { it.adUnitId.startsWith(GOOGLE_DEMO_PUBLISHER) })
            assertTrue("every sample ad is on by default", placements.values.all { it.enabled })

            onView(withId(R.id.level_complete)).perform(click())
            awaitResumed<DetailsActivity>(timeoutMillis = 15_000)
            awaitEvent(20_000) { it is AdEvent.Loaded && it.format == AdFormat.BANNER && it.placementKey == "DetailsBanner" }
        }
    }

    private fun awaitEvent(timeoutMillis: Long, predicate: (AdEvent) -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            if (CompanyAdMob.diagnostics.recentEvents.any(predicate)) return
            SystemClock.sleep(200)
        }
        fail("No matching event within $timeoutMillis ms: ${CompanyAdMob.diagnostics.recentEvents.takeLast(10)}")
    }

    private companion object {
        const val GOOGLE_DEMO_PUBLISHER = "ca-app-pub-3940256099942544/"
    }
}
