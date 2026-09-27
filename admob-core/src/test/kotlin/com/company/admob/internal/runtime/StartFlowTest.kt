package com.company.admob.internal.runtime

import android.app.Activity
import android.content.Context
import android.os.Looper
import com.company.admob.AdError
import com.company.admob.AdsState
import com.company.admob.CompanyAdMob
import com.company.admob.ConsentStatus
import com.company.admob.UnavailableReason
import com.company.admob.config.AgeTreatment
import com.company.admob.internal.consent.RdpWriter
import com.company.admob.internal.port.SdkInitResult
import com.company.admob.internal.port.SdkRequestConfig
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StartFlowTest {
    private fun newActivity(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private val ready = AdsState.Ready
    private val consentNotObtained = AdsState.Unavailable(UnavailableReason.CONSENT_NOT_OBTAINED)

    private class Started(val h: RuntimeHarness, val runtime: AdsRuntime) {
        val results = mutableListOf<AdsState>()

        fun start(activity: Activity) = runtime.client.start(activity) { results += it }
    }

    private fun TestScope.started(setup: RuntimeHarness.() -> Unit = {}): Started {
        val h = RuntimeHarness(this).apply(setup)
        val runtime = h.runtime()
        h.settle()
        return Started(h, runtime)
    }

    @After
    fun tearDown() = CompanyAdMob.resetForTests()

    @Test
    fun `happy path - consent, one SDK init on the main thread, Ready`() = runTest {
        val s = started {
            consentSdk.canRequestAds = true
            consentSdk.status = ConsentStatus.NOT_REQUIRED
        }
        assertEquals(AdsState.AwaitingStart, s.h.state.value)

        s.start(newActivity())
        s.h.settle()

        assertEquals(listOf(ready), s.results)
        assertEquals(
            listOf(AdsState.AwaitingStart, AdsState.GatheringConsent, AdsState.InitializingSdk, ready),
            s.h.stateChanges(),
        )
        val call = s.h.adsSdk.initializeCalls.single()
        assertEquals(com.company.admob.config.TestIds.APP_ID, call.appId)
        assertEquals(SdkRequestConfig(emptyList(), AgeTreatment.UNSPECIFIED, null), call.request)
        assertSame(Looper.getMainLooper().thread, s.h.adsSdk.initializeThreads.single())
        assertTrue(s.runtime.init.canLoad())
        assertEquals(emptyList<String>(), s.h.logs.errors)
    }

    @Test
    fun `consent not obtained - Unavailable, no SDK init, and a later start gathers again`() = runTest {
        val s = started {
            consentSdk.canRequestAds = false
            consentSdk.status = ConsentStatus.REQUIRED
        }

        s.start(newActivity())
        s.h.settle()
        assertEquals(listOf<AdsState>(consentNotObtained), s.results)
        assertTrue(s.h.adsSdk.initializeCalls.isEmpty())

        s.h.consentSdk.onFormShown = { canRequestAds = true; status = ConsentStatus.OBTAINED }
        s.start(newActivity())
        s.h.settle()
        assertEquals(listOf(consentNotObtained, ready), s.results)
        assertEquals(2, s.h.consentSdk.updateRequests.size)
        assertEquals(1, s.h.adsSdk.initializeCalls.size)
        assertEquals(emptyList<String>(), s.h.logs.errors)
    }

    @Test
    fun `SDK init failure - Failed, no retry before 30 s, then retry reaches Ready`() = runTest {
        val failure = AdError.SdkInitFailed()
        val s = started { adsSdk.initResult = SdkInitResult.Failure(failure) }

        s.start(newActivity())
        s.h.settle()
        assertEquals(listOf<AdsState>(AdsState.Failed(failure)), s.results)

        s.h.clock.advance(29.seconds)
        s.start(newActivity())
        s.h.settle()
        assertEquals(1, s.h.adsSdk.initializeCalls.size)
        assertEquals(AdsState.Failed(failure), s.results.last())

        s.h.clock.advance(1.seconds)
        s.h.adsSdk.initResult = SdkInitResult.Success
        s.start(newActivity())
        s.h.settle()
        assertEquals(2, s.h.adsSdk.initializeCalls.size)
        assertEquals(ready, s.results.last())
        assertEquals(3, s.results.size)
    }

    @Test
    fun `double start while in flight - a single SDK init and both callbacks fire`() = runTest {
        val s = started { adsSdk.initDelayMs = 1_000 }

        s.start(newActivity())
        s.start(newActivity())
        s.h.settle()

        assertEquals(1, s.h.adsSdk.initializeCalls.size)
        assertEquals(1, s.h.consentSdk.updateRequests.size)
        assertEquals(listOf(ready, ready), s.results)
    }

    @Test
    fun `rotation - a start with a new Activity mid-gather joins it and the form uses the new Activity`() = runTest {
        val s = started { consentSdk.updateDelayMs = 1_000 }
        val first = Robolectric.buildActivity(Activity::class.java).setup()
        val second = newActivity()

        s.start(first.get())
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        s.h.scope.testScheduler.advanceTimeBy(500)
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(AdsState.GatheringConsent, s.h.state.value)
        first.pause().stop().destroy()
        s.start(second)
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        s.h.settle()

        assertEquals(1, s.h.consentSdk.updateRequests.size)
        assertSame(second, s.h.consentSdk.formActivity?.get())
        assertEquals(listOf(ready, ready), s.results)
    }

    @Test
    fun `returning user whose form withdraws consent after an early init ends Unavailable`() = runTest {
        val s = started {
            consentSdk.canRequestAds = true
            consentSdk.status = ConsentStatus.OBTAINED
            consentSdk.onFormShown = { canRequestAds = false; status = ConsentStatus.REQUIRED }
        }

        s.start(newActivity())
        s.h.settle()

        assertEquals(1, s.h.adsSdk.initializeCalls.size)
        assertEquals(listOf<AdsState>(consentNotObtained), s.results)
        assertEquals(emptyList<String>(), s.h.logs.errors)
    }

    @Test
    fun `start before initialize completes with NotInitialized`() {
        val results = mutableListOf<AdsState>()
        CompanyAdMob.start(newActivity()) { results += it }
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf<AdsState>(AdsState.NotInitialized), results)
    }

    @Test
    fun `a Ready start completes immediately, posted`() = runTest {
        val s = started()
        s.start(newActivity())
        s.h.settle()

        s.start(newActivity())
        assertEquals(1, s.results.size)
        s.h.settle()
        assertEquals(listOf(ready, ready), s.results)
        assertEquals(1, s.h.consentSdk.updateRequests.size)
    }

    @Test
    fun `gad_rdp is written before the SDK initializes, and removed before it when turned off`() = runTest {
        val h = RuntimeHarness(this)
        val prefs = h.app.getSharedPreferences(RdpWriter.defaultPreferencesName(h.app), Context.MODE_PRIVATE)
        val seenAtInit = mutableListOf<Int?>()
        h.adsSdk.onInitialize = { seenAtInit += prefs.getInt(RdpWriter.KEY, -1).takeIf { it != -1 } }

        val first = h.runtime(testConfig(h.logs) { privacy { restrictedDataProcessing = true } })
        h.settle()
        first.client.start(newActivity())
        h.settle()
        assertEquals(listOf<Int?>(1), seenAtInit)

        CompanyAdMob.resetForTests()
        val next = RuntimeHarness(this)
        next.adsSdk.onInitialize = { seenAtInit += prefs.getInt(RdpWriter.KEY, -1).takeIf { it != -1 } }
        val runtime = next.runtime(testConfig(next.logs) { privacy { restrictedDataProcessing = false } })
        next.settle()
        runtime.client.start(newActivity())
        next.settle()
        assertEquals(listOf(1, null), seenAtInit)
        assertFalse(prefs.contains(RdpWriter.KEY))
    }
}
