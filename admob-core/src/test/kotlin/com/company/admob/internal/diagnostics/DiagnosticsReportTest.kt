package com.company.admob.internal.diagnostics

import android.app.Activity
import com.company.admob.AdAvailability
import com.company.admob.AdError
import com.company.admob.AdFormat
import com.company.admob.AdsState
import com.company.admob.CompanyAdMob
import com.company.admob.CompanyAdMobVersion
import com.company.admob.InterstitialPlacement
import com.company.admob.NativePlacement
import com.company.admob.config.AdEnvironment
import com.company.admob.config.TestIds
import com.company.admob.config.remote.AdConfigProvider
import com.company.admob.config.remote.AdOverrides
import com.company.admob.config.remote.PlacementOverride
import com.company.admob.fakes.FakeAdsSdk.ScriptedLoad
import com.company.admob.internal.config.GoogleDemoAdUnits
import com.company.admob.internal.runtime.RuntimeHarness
import com.company.admob.internal.runtime.testConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DiagnosticsReportTest {
    private val level = InterstitialPlacement("level")
    private val feed = NativePlacement("feed")

    private class ResumedScreen : Activity()

    private fun newActivity(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    @After
    fun tearDown() = CompanyAdMob.resetForTests()

    @Test
    fun `the snapshot reports every field`() = runTest {
        val h = RuntimeHarness(this)
        h.tracker.resumedActivity = Robolectric.buildActivity(ResumedScreen::class.java).setup().get()
        val runtime = h.runtime(
            testConfig(h.logs) {
                testing { testDeviceIds += "DEVICE-A" }
                placements { native(feed, TestIds.unit(3)) { enabled = false } }
            },
        )
        runtime.client.start(newActivity())
        h.settle()

        val report = runtime.client.diagnostics.snapshot()

        assertEquals(CompanyAdMobVersion.NAME, report.libraryVersion)
        assertEquals("fake-1.0.0", report.googleSdkVersion)
        assertEquals(AdEnvironment.DEVELOPMENT, report.environment)
        assertEquals(AdsState.Ready, report.state)
        assertTrue(report.consent.canRequestAds)
        assertTrue(report.testMode!!.demoAdUnitsSubstituted)
        assertEquals(1, report.testMode!!.testDeviceCount)
        assertNull(report.activeFullScreen)
        assertEquals("ResumedScreen", report.currentActivity)
        assertNull(report.lastError)
        assertEquals("none", report.activeOverrides)

        val interstitial = report.placements.single { it.key == "level" }
        assertEquals(AdFormat.INTERSTITIAL, interstitial.format)
        assertEquals(GoogleDemoAdUnits.INTERSTITIAL, interstitial.adUnitId)
        assertTrue(interstitial.enabled)
        assertEquals(AdAvailability.Ready, interstitial.availability)
        assertNotNull(interstitial.cachedAdAge)
        assertEquals(0, interstitial.retryAttempt)
        assertNull(interstitial.nextRetryIn)
        assertEquals(0, interstitial.showsThisSession)

        val native = report.placements.single { it.key == "feed" }
        assertFalse(native.enabled)
        assertEquals(AdAvailability.Disabled, native.availability)

        val text = report.toString()
        assertTrue(GoogleDemoAdUnits.INTERSTITIAL in text)
        assertTrue("ResumedScreen" in text)
        assertTrue(runtime.client.diagnostics.recentEvents.isNotEmpty())
    }

    @Test
    fun `a failed load shows up as the last error and the retry schedule`() = runTest {
        val h = RuntimeHarness(this)
        h.adsSdk.defaultLoad = ScriptedLoad.Failure(AdError.NoFill(), retryable = true)
        val runtime = h.runtime()
        runtime.client.start(newActivity())
        h.settle()
        // Settling runs virtual time out, so retries have run; the count and last error are what matter here.
        val report = runtime.client.diagnostics.snapshot()
        val p = report.placements.single()
        assertTrue(p.retryAttempt >= 1)
        assertTrue(p.availability is AdAvailability.Failed)
        assertEquals(AdError.NoFill(), report.lastError)
    }

    @Test
    fun `PRODUCTION - no full ad unit ID anywhere in the report, and no recent events`() = runTest {
        val h = RuntimeHarness(this)
        val fullId = TestIds.unit(1)
        // An error message carrying a full ad unit ID must be redacted too.
        h.adsSdk.defaultLoad = ScriptedLoad.Failure(AdError.Internal("load failed for $fullId"), retryable = false)
        val remote = AdOverrides(placements = mapOf("level" to PlacementOverride(adUnitId = TestIds.unit(5))))
        val runtime = h.runtime(
            testConfig(h.logs) {
                environment = AdEnvironment.PRODUCTION
                remoteConfig = AdConfigProvider { MutableStateFlow(remote) }
            },
        )
        runtime.client.start(newActivity())
        h.settle()

        val report = runtime.client.diagnostics.snapshot()
        val text = report.toString()

        assertEquals(AdEnvironment.PRODUCTION, report.environment)
        assertFalse(report.testMode!!.demoAdUnitsSubstituted)
        assertEquals("…0005", report.placements.single().adUnitId)
        assertTrue("adUnitId=…0005" in report.activeOverrides)
        assertTrue((report.lastError as AdError.Internal).detail.contains(fullId))
        assertFalse(fullId in text)
        assertFalse(TestIds.unit(5) in text)
        assertTrue("load failed for …0001" in text)
        assertTrue(runtime.client.diagnostics.recentEvents.isEmpty())
    }

    @Test
    fun `openAdInspector delegates to the SDK and delivers its result once, on the main thread`() = runTest {
        val h = RuntimeHarness(this)
        val runtime = h.runtime()
        h.settle()
        val results = mutableListOf<AdError?>()

        runtime.client.diagnostics.openAdInspector(newActivity()) { results += it }
        h.settle()
        h.adsSdk.adInspectorResult = AdError.Internal("Ad Inspector is only available on test devices")
        runtime.client.diagnostics.openAdInspector(newActivity()) { results += it }
        h.settle()

        assertEquals(2, h.adsSdk.adInspectorOpenCount)
        assertEquals(listOf(null, AdError.Internal("Ad Inspector is only available on test devices")), results)
    }
}
