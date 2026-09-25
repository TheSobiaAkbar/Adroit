package com.company.admob.fakes

import android.app.Activity
import androidx.test.core.app.ApplicationProvider
import com.company.admob.AdError
import com.company.admob.AdFormat
import com.company.admob.BannerSize
import com.company.admob.GoogleSdkEscapeHatch
import com.company.admob.InterstitialPlacement
import com.company.admob.NativePlacement
import com.company.admob.Reward
import com.company.admob.config.AgeTreatment
import com.company.admob.config.DebugGeography
import com.company.admob.events.AdRevenue
import com.company.admob.events.AdSourceInfo
import com.company.admob.internal.port.BannerListener
import com.company.admob.internal.port.ConsentParams
import com.company.admob.internal.port.ConsentUpdateResult
import com.company.admob.internal.port.FullScreenAdListener
import com.company.admob.internal.port.LoadedFullScreenAd
import com.company.admob.internal.port.LoadedNativeAd
import com.company.admob.internal.port.NativeAdListener
import com.company.admob.internal.port.RequestExtras
import com.company.admob.internal.port.SdkInitResult
import com.company.admob.internal.port.SdkLoadResult
import com.company.admob.internal.port.SdkRequestConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

/** Sanity of the scripting every engine test relies on. */
@OptIn(GoogleSdkEscapeHatch::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FakeAdsSdkTest {
    private val extras = RequestExtras(InterstitialPlacement("level_complete"), customizer = null)
    private val request = SdkRequestConfig(emptyList(), AgeTreatment.UNSPECIFIED, null)

    @Test
    fun `scripted loads are consumed in order per ad unit, then the default applies`() = runTest {
        val sdk = FakeAdsSdk(this)
        sdk.enqueueLoad("a", FakeAdsSdk.ScriptedLoad.Failure(AdError.NoFill(), retryable = true))
        sdk.enqueueLoad("a", FakeAdsSdk.ScriptedLoad.Success())
        val results = mutableListOf<SdkLoadResult<LoadedFullScreenAd>>()

        repeat(3) { sdk.loadFullScreen(AdFormat.INTERSTITIAL, "a", extras) { results += it } }
        assertEquals("results are never synchronous", 0, results.size)
        advanceUntilIdle()

        assertEquals(SdkLoadResult.Failure(AdError.NoFill(), true), results[0])
        assertTrue(results[1] is SdkLoadResult.Success)
        assertTrue(results[2] is SdkLoadResult.Success)
        assertEquals(3, sdk.loadCount("a"))
        assertEquals(extras, sdk.loadCalls.first().extras)
        assertEquals(2, sdk.fullScreenAds.size)
        assertEquals(AdFormat.INTERSTITIAL, sdk.fullScreenAds.first().format)
    }

    @Test
    fun `delays follow virtual time and pending loads never complete`() = runTest {
        val sdk = FakeAdsSdk(this)
        sdk.enqueueLoad("a", FakeAdsSdk.ScriptedLoad.Success(), delayMs = 5_000)
        sdk.enqueueLoad("b", FakeAdsSdk.ScriptedLoad.Pending)
        val results = mutableListOf<String>()
        sdk.loadFullScreen(AdFormat.REWARDED, "a", extras) { results += "a" }
        sdk.loadNative("b", extras) { results += "b" }

        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(emptyList<String>(), results)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("a"), results)
        advanceUntilIdle()
        assertEquals(listOf("a"), results)
    }

    @Test
    fun `fake full-screen ad forwards simulated events to the show listener`() = runTest {
        val sdk = FakeAdsSdk(this)
        var loaded: LoadedFullScreenAd? = null
        sdk.loadFullScreen(AdFormat.REWARDED, "a", extras) { loaded = (it as SdkLoadResult.Success).ad }
        advanceUntilIdle()
        val ad = sdk.fullScreenAds.single()
        assertSame(ad, loaded)

        val events = mutableListOf<String>()
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        ad.setServerSideVerification("custom")
        ad.show(activity, recordingListener(events))
        ad.simulateShown()
        ad.simulateReward(Reward("coins", 5))
        ad.simulateDismissed()
        ad.release()

        assertEquals(listOf("shown", "reward:coins:5", "dismissed"), events)
        assertEquals("custom", ad.serverSideVerificationData)
        assertEquals(1, ad.showCount)
        assertTrue(ad.released)
    }

    @Test
    fun `fake native ad records destroy and forwards events until then`() = runTest {
        val sdk = FakeAdsSdk(this)
        var loaded: LoadedNativeAd? = null
        sdk.loadNative("n", RequestExtras(NativePlacement("n"), null)) { loaded = (it as SdkLoadResult.Success).ad }
        advanceUntilIdle()
        val ad = sdk.nativeAds.single()
        assertSame(ad, loaded)
        assertEquals(AdFormat.NATIVE, sdk.loadCalls.single().format)

        var impressions = 0
        ad.setListener(object : NativeAdListener {
            override fun onImpression() { impressions++ }
            override fun onClicked() = Unit
            override fun onPaid(revenue: AdRevenue) = Unit
        })
        ad.simulateImpression()
        ad.destroy()
        ad.simulateImpression()

        assertEquals(1, impressions)
        assertTrue(ad.isDestroyed)
        assertSame(ad, ad.bindInternal())
    }

    @Test
    fun `initialize, request config, banner and Ad Inspector are recorded`() = runTest {
        val sdk = FakeAdsSdk(this)
        sdk.initResult = SdkInitResult.Failure(AdError.SdkInitFailed())
        sdk.initDelayMs = 1_000
        var initResult: SdkInitResult? = null
        sdk.initialize(ApplicationProvider.getApplicationContext(), "app", request, disableCrashReporting = true) { initResult = it }
        sdk.updateRequestConfig(request.copy(testDeviceIds = listOf("device")))
        var inspectorResult: AdError? = AdError.NotReady
        sdk.openAdInspector { inspectorResult = it }
        advanceUntilIdle()

        assertEquals(SdkInitResult.Failure(AdError.SdkInitFailed()), initResult)
        assertEquals(FakeAdsSdk.InitializeCall("app", request, true), sdk.initializeCalls.single())
        assertEquals(listOf("device"), sdk.requestConfigUpdates.single().testDeviceIds)
        assertNull(inspectorResult)
        assertEquals(1, sdk.adInspectorOpenCount)

        val banner = sdk.createBannerView(Robolectric.buildActivity(Activity::class.java).get()) as FakeSdkBannerView
        var loads = 0
        banner.load("b", BannerSize.Standard, 320, extras, object : BannerListener {
            override fun onLoaded(source: AdSourceInfo?, isRefresh: Boolean) { loads++ }
            override fun onFailedToLoad(error: AdError, retryable: Boolean, isRefresh: Boolean) = Unit
            override fun onImpression() = Unit
            override fun onClicked() = Unit
            override fun onPaid(revenue: AdRevenue) = Unit
        })
        banner.simulateLoaded()
        banner.destroy()
        banner.simulateLoaded()
        assertEquals(1, loads)
        assertEquals("b", banner.loadCalls.single().adUnitId)
        assertTrue(banner.destroyed)
        assertSame(banner, sdk.bannerViews.single())
    }

    @Test
    fun `fake consent SDK scripts update and form results`() = runTest {
        val consent = FakeConsentSdk(this)
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        consent.canRequestAds = false
        consent.updateResult = ConsentUpdateResult.Failure(AdError.NetworkError())
        consent.onFormShown = { canRequestAds = true }
        val params = ConsentParams(DebugGeography.EEA, listOf("hash"), tagUnderAgeOfConsent = false, consentSyncId = null)

        var update: ConsentUpdateResult? = null
        var formError: AdError? = AdError.NotReady
        consent.requestUpdate(activity, params) { update = it }
        consent.loadAndShowFormIfRequired(activity) { formError = it }
        advanceUntilIdle()

        assertEquals(ConsentUpdateResult.Failure(AdError.NetworkError()), update)
        assertNull(formError)
        assertTrue(consent.canRequestAds())
        assertEquals(listOf(params), consent.updateRequests)
        assertEquals(1, consent.formShowCount)
    }

    @Test
    fun `fake clock, tracker, foreground source and store`() = runTest {
        val clock = FakeClock()
        clock.advance(30.seconds)
        assertEquals(30_000L, clock.elapsedRealtimeMillis())
        assertEquals(1_700_000_030_000L, clock.currentTimeMillis())

        val activity = Robolectric.buildActivity(Activity::class.java).get()
        val tracker = FakeActivityTracker(resumedActivity = activity)
        assertTrue(tracker.isValidForShow(activity))
        tracker.invalidActivities += activity
        assertEquals(false, tracker.isValidForShow(activity))

        val foreground = FakeAppForegroundSource()
        val events = mutableListOf<Boolean>()
        val job = launch { foreground.foregroundEvents.collect { events += it.isColdStart } }
        runCurrent()
        foreground.moveToForeground(isColdStart = true)
        foreground.moveToBackground()
        foreground.moveToForeground()
        runCurrent()
        job.cancel()
        assertEquals(listOf(true, false), events)
        assertTrue(foreground.isInForeground)

        val store = InMemoryKeyValueStore()
        assertEquals(0, store.getInt("launch_count", 0))
        store.putInt("launch_count", 3)
        assertEquals(3, store.getInt("launch_count", 0))
    }

    private fun recordingListener(events: MutableList<String>) = object : FullScreenAdListener {
        override fun onShown() { events += "shown" }
        override fun onImpression() { events += "impression" }
        override fun onClicked() { events += "clicked" }
        override fun onPaid(revenue: AdRevenue) { events += "paid" }
        override fun onRewardEarned(reward: Reward) { events += "reward:${reward.type}:${reward.amount}" }
        override fun onDismissed() { events += "dismissed" }
        override fun onFailedToShow(error: AdError) { events += "failed" }
    }
}
