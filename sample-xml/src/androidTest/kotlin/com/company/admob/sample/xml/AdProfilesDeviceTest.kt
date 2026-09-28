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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The ad definitions (`SampleAds`) on a device: every entry is configured under its name, test builds use Google's
 * demo ad units, the ad turned off by default is off, and the Details banner (style `collapsible_bottom`) loads. Rotation is covered by unit tests; with demo ad units every ID is the same one here.
 */
@RequiresNetwork
@RunWith(AndroidJUnit4::class)
class AdProfilesDeviceTest {
    @Test
    fun theAdsFileConfiguresEveryAdAndTheCollapsibleBannerLoads() {
        ActivityScenario.launch(SplashActivity::class.java).use {
            // 20 s, not the default 10: this class runs first, so it gets the run's only cold start, and with it a
            // possible cold-start app open ad (up to about 6 s after launch). That ad's close button ("Continue to
            // app") is web content, exposed to UI Automator only about 5 s after the ad appears (Pixel 7a).
            awaitResumed<MainActivity>(timeoutMillis = 20_000)
            val placements = CompanyAdMob.diagnostics.snapshot().placements.associateBy { it.key }
            assertEquals(
                setOf(
                    "SplashAd", "LevelCompleteAd", "ExtraLifeRewarded", "BonusRewardedInterstitial", "AppOpenAd",
                    "HomeBanner", "DetailsBanner", "FeedNative", "ArticleNative",
                ),
                placements.keys,
            )
            assertTrue(placements.values.all { it.adUnitId.startsWith(GOOGLE_DEMO_PUBLISHER) })
            assertFalse("SplashAd is off by default", placements.getValue("SplashAd").enabled)

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
