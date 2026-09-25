package com.company.admob.internal.runtime

import android.app.Activity
import com.company.admob.AdsState
import com.company.admob.CompanyAdMob
import com.company.admob.ConsentStatus
import com.company.admob.UnavailableReason
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AdFreeTest {
    private val adFreeState = AdsState.Unavailable(UnavailableReason.AD_FREE_USER)
    private fun newActivity(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    @After
    fun tearDown() = CompanyAdMob.resetForTests()

    @Test
    fun `set before initialize - applied at initialize, and start ends ad-free with the SDK initialized`() = runTest {
        val h = RuntimeHarness(this)
        CompanyAdMob.setAdFree(true)
        CompanyAdMob.initialize(h.app, testConfig(h.logs)) { _, _ -> h.deps() }
        h.settle()
        val runtime = CompanyAdMob.runtime!!
        assertTrue(runtime.adFree.isAdFree)

        val results = mutableListOf<AdsState>()
        CompanyAdMob.start(newActivity()) { results += it }
        h.settle()

        assertEquals(listOf<AdsState>(adFreeState), results)
        assertEquals(1, h.adsSdk.initializeCalls.size)
        assertTrue(runtime.init.isSdkInitialized)
        assertEquals(emptyList<String>(), h.logs.errors)
    }

    @Test
    fun `set during init - init finishes, then the state is ad-free`() = runTest {
        val h = RuntimeHarness(this).apply { adsSdk.initDelayMs = 1_000 }
        val runtime = h.runtime()
        h.settle()
        val results = mutableListOf<AdsState>()
        runtime.client.start(newActivity()) { results += it }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        h.scope.testScheduler.advanceTimeBy(500)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(AdsState.InitializingSdk, h.state.value)

        runtime.client.setAdFree(true)
        h.settle()

        assertTrue(runtime.init.isSdkInitialized)
        assertEquals(listOf<AdsState>(adFreeState), results)
        assertEquals(adFreeState, h.state.value)
        assertEquals(emptyList<String>(), h.logs.errors)
    }

    @Test
    fun `toggled after Ready - blocked listeners are told, and turning it off returns to Ready without re-init`() =
        runTest {
            val h = RuntimeHarness(this)
            val runtime = h.runtime()
            val blocked = mutableListOf<UnavailableReason>()
            runtime.adFree.addListener { blocked += it }
            runtime.client.start(newActivity())
            h.settle()
            assertEquals(AdsState.Ready, h.state.value)

            runtime.client.setAdFree(true)
            assertEquals(adFreeState, h.state.value)
            assertEquals(listOf(UnavailableReason.AD_FREE_USER), blocked)
            assertTrue(!runtime.init.canLoad())

            runtime.client.setAdFree(true) // no change, no second notification
            assertEquals(1, blocked.size)

            runtime.client.setAdFree(false)
            assertEquals(AdsState.Ready, h.state.value)
            assertEquals(1, h.adsSdk.initializeCalls.size)
            assertEquals(emptyList<String>(), h.logs.errors)
        }

    @Test
    fun `turned off while consent isn't obtained - Unavailable(CONSENT_NOT_OBTAINED)`() = runTest {
        val h = RuntimeHarness(this).apply {
            consentSdk.canRequestAds = false
            consentSdk.status = ConsentStatus.REQUIRED
        }
        val runtime = h.runtime()
        runtime.client.setAdFree(true)
        runtime.client.start(newActivity())
        h.settle()
        assertEquals(adFreeState, h.state.value)

        runtime.client.setAdFree(false)
        assertEquals(AdsState.Unavailable(UnavailableReason.CONSENT_NOT_OBTAINED), h.state.value)
        assertEquals(emptyList<String>(), h.logs.errors)
    }

    @Test
    fun `consent withdrawn through privacy options - Unavailable and blocked listeners told`() = runTest {
        val h = RuntimeHarness(this).apply {
            consentSdk.status = ConsentStatus.OBTAINED
            consentSdk.privacyOptionsRequired = true
            consentSdk.onPrivacyOptionsFormShown = { canRequestAds = false; status = ConsentStatus.REQUIRED }
        }
        val runtime = h.runtime()
        val blocked = mutableListOf<UnavailableReason>()
        runtime.adFree.addListener { blocked += it }
        runtime.client.start(newActivity())
        h.settle()
        assertEquals(AdsState.Ready, h.state.value)

        runtime.client.privacy.showPrivacyOptions(newActivity())
        h.settle()

        assertEquals(AdsState.Unavailable(UnavailableReason.CONSENT_NOT_OBTAINED), h.state.value)
        assertEquals(listOf(UnavailableReason.CONSENT_NOT_OBTAINED), blocked)

        // Granting again later initializes nothing new and returns to Ready.
        h.consentSdk.onPrivacyOptionsFormShown = { canRequestAds = true; status = ConsentStatus.OBTAINED }
        runtime.client.privacy.showPrivacyOptions(newActivity())
        h.settle()
        assertEquals(AdsState.Ready, h.state.value)
        assertEquals(1, h.adsSdk.initializeCalls.size)
        assertEquals(emptyList<String>(), h.logs.errors)
    }
}
