package com.company.admob.sample.xml

import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Interstitial and rewarded ads shown and dismissed 3 times each: the ad Activities, the callbacks and the host
 * Activity must all be released afterwards. Waits out the sample's frequency limits (60 s for the interstitial
 * placement, 30 s between any two full-screen ads), so it takes several minutes. Needs network to show ads; without
 * fill, each attempt continues without an ad and only the no-ad path is covered.
 */
@RequiresNetwork
@RunWith(AndroidJUnit4::class)
class FullScreenLeakTest {
    @get:Rule
    val detectLeaks = DetectLeaksAfterTestSuccess()

    @Test
    fun showingAndDismissingFullScreenAdsLeavesNoLeaks() {
        ActivityScenario.launch(SplashActivity::class.java).use {
            awaitResumed<MainActivity>()
            // Waits out the global interval after a possible cold-start app open ad, and lets the placements preload.
            SystemClock.sleep(GLOBAL_INTERVAL_MILLIS)

            repeat(3) { attempt ->
                if (attempt > 0) SystemClock.sleep(INTERSTITIAL_INTERVAL_MILLIS)
                tapOpeningAd(R.id.level_complete)
                val shown = dismissFullScreenAdIfShown()
                Log.i(TAG, "Interstitial ${attempt + 1}: ${if (shown) "shown and dismissed" else "not shown"}")
                awaitResumed<DetailsActivity>()
                pressBack()
                awaitResumed<MainActivity>()
            }

            repeat(3) { attempt ->
                SystemClock.sleep(GLOBAL_INTERVAL_MILLIS)
                tapOpeningAd(R.id.extra_life)
                val shown = dismissFullScreenAdIfShown()
                Log.i(TAG, "Rewarded ${attempt + 1}: ${if (shown) "shown and dismissed" else "not shown"}")
                awaitResumed<MainActivity>()
            }
        }
    }

    private companion object {
        const val TAG = "FullScreenLeakTest"

        /** SampleAdsConfig: `minIntervalBetweenFullScreenAds = 30.seconds`, plus a margin. */
        const val GLOBAL_INTERVAL_MILLIS = 31_000L

        /** SampleAdsConfig: the interstitial placement's `minInterval = 60.seconds`, plus a margin. */
        const val INTERSTITIAL_INTERVAL_MILLIS = 61_000L
    }
}
