package io.github.thesobiaakbar.adroit.internal.runtime

import android.app.Activity
import io.github.thesobiaakbar.adroit.Adroit
import io.github.thesobiaakbar.adroit.AdsState
import io.github.thesobiaakbar.adroit.BannerPlacement
import io.github.thesobiaakbar.adroit.BannerState
import io.github.thesobiaakbar.adroit.ConsentStatus
import io.github.thesobiaakbar.adroit.UnavailableReason
import io.github.thesobiaakbar.adroit.config.TestIds
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun tearDown() = Adroit.resetForTests()

    @Test
    fun `isAdFree is the last setAdFree value, before and after initialize, and false after a reset`() = runTest {
        assertFalse("never set", Adroit.isAdFree)
        Adroit.setAdFree(true)
        assertTrue("before initialize", Adroit.isAdFree)

        val h = RuntimeHarness(this)
        Adroit.initialize(h.app, testConfig(h.logs)) { _, _ -> h.deps() }
        h.settle()
        assertTrue("kept by initialize", Adroit.isAdFree)
        Adroit.setAdFree(false)
        assertFalse("after initialize", Adroit.isAdFree)

        Adroit.setAdFree(true)
        Adroit.resetForTests()
        assertFalse(Adroit.isAdFree)
    }

    @Test
    fun `set before initialize - applied at initialize, and start ends ad-free with the SDK initialized`() = runTest {
        val h = RuntimeHarness(this)
        Adroit.setAdFree(true)
        Adroit.initialize(h.app, testConfig(h.logs)) { _, _ -> h.deps() }
        h.settle()
        val runtime = Adroit.runtime!!
        assertTrue(runtime.adFree.isAdFree)

        val results = mutableListOf<AdsState>()
        Adroit.start(newActivity()) { results += it }
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

    @Test
    fun `ad-free with start() never called - a banner is Hidden, not Loading for ever, and loads again once turned off`() =
        runTest {
            val h = RuntimeHarness(this)
            val runtime = h.runtime(
                testConfig(h.logs) { placements { banner(BannerPlacement("home"), TestIds.unit(2)) } },
            )
            h.settle()
            // An app may skip start() for a premium user (no consent form): ads stay AwaitingStart.
            runtime.client.setAdFree(true)
            val banner = runtime.client.banner.create(newActivity(), BannerPlacement("home"))
            h.settle()

            assertEquals(AdsState.AwaitingStart, h.state.value)
            assertEquals(BannerState.Hidden(UnavailableReason.AD_FREE_USER), banner.state.value)

            runtime.client.setAdFree(false)
            h.settle()
            assertEquals(BannerState.Loading, banner.state.value)
            banner.destroy()
        }
}
