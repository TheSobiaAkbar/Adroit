package com.company.admob.internal

import android.app.Activity
import android.os.Looper
import com.company.admob.AdError
import com.company.admob.AdsState
import com.company.admob.BannerState
import com.company.admob.CompanyAdMob
import com.company.admob.FullScreenResult
import com.company.admob.InterstitialPlacement
import com.company.admob.NativeAdResult
import com.company.admob.NativePlacement
import com.company.admob.RewardResult
import com.company.admob.RewardedPlacement
import com.company.admob.BannerPlacement
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Before initialize(), the facade delegates to UninitializedAdsClient. */
@RunWith(RobolectricTestRunner::class)
class UninitializedAdsClientTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val ads = CompanyAdMob

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `state is NotInitialized`() {
        assertEquals(AdsState.NotInitialized, ads.state.value)
        assertFalse(ads.isReady)
    }

    @Test
    fun `interstitial show returns NotInitialized once, asynchronously, on main`() {
        val results = mutableListOf<FullScreenResult>()
        ads.showInterstitial(activity, InterstitialPlacement("level")) {
            assertEquals(Looper.getMainLooper().thread, Thread.currentThread())
            results += it
        }
        assertTrue("must not be synchronous", results.isEmpty())
        idleMain()
        assertEquals(listOf(FullScreenResult.NotShown(AdError.NotInitialized)), results)
    }

    @Test
    fun `rewarded show returns Failed(NotInitialized) once`() {
        val results = mutableListOf<RewardResult>()
        ads.showRewarded(activity, RewardedPlacement("extra_life")) { results += it }
        idleMain()
        assertEquals(listOf(RewardResult.Failed(AdError.NotInitialized)), results)
    }

    @Test
    fun `suspend variants return immediately`() = runTest {
        assertEquals(
            FullScreenResult.NotShown(AdError.NotInitialized),
            ads.interstitial.showAndAwait(activity, InterstitialPlacement("level")),
        )
        assertEquals(
            RewardResult.Failed(AdError.NotInitialized),
            ads.rewarded.showAndAwait(activity, RewardedPlacement("extra_life")),
        )
        assertFalse(ads.awaitReady())
    }

    @Test
    fun `start completes with NotInitialized`() {
        var completed: AdsState? = null
        ads.start(activity) { completed = it }
        idleMain()
        assertEquals(AdsState.NotInitialized, completed)
    }

    @Test
    fun `native load fails and banner is inert`() {
        var result: NativeAdResult? = null
        ads.native.load(NativePlacement("feed")) { result = it }
        idleMain()
        assertEquals(NativeAdResult.Failed(AdError.NotInitialized), result)

        val banner = ads.banner.create(activity, BannerPlacement("home"))
        assertEquals(BannerState.Failed(AdError.NotInitialized), banner.state.value)
        banner.destroy()
        banner.destroy() // idempotent
    }

    @Test
    fun `diagnostics report says not initialized`() {
        val report = ads.diagnostics.snapshot()
        assertEquals(AdsState.NotInitialized, report.state)
        assertEquals(null, report.environment)
        assertTrue(report.toString().contains("not initialized"))
    }
}
