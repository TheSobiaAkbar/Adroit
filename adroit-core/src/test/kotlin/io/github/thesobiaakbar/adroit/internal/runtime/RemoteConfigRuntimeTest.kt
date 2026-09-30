package io.github.thesobiaakbar.adroit.internal.runtime

import android.app.Activity
import android.os.Looper
import io.github.thesobiaakbar.adroit.AdAvailability
import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.AdsState
import io.github.thesobiaakbar.adroit.Adroit
import io.github.thesobiaakbar.adroit.FullScreenResult
import io.github.thesobiaakbar.adroit.InterstitialPlacement
import io.github.thesobiaakbar.adroit.UnavailableReason
import io.github.thesobiaakbar.adroit.config.remote.AdConfigProvider
import io.github.thesobiaakbar.adroit.config.remote.AdOverrides
import io.github.thesobiaakbar.adroit.events.AdEvent
import io.github.thesobiaakbar.adroit.events.AdEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** Remote overrides and event listeners through the whole runtime, as configured by the host. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RemoteConfigRuntimeTest {
    private val level = InterstitialPlacement("level")
    private val remoteOff = AdsState.Unavailable(UnavailableReason.DISABLED_BY_REMOTE)

    private fun newActivity(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    @After
    fun tearDown() = Adroit.resetForTests()

    @Test
    fun `remote adsEnabled=false - Unavailable and caches released, enabled again - Ready and reloaded`() = runTest {
        val remote = MutableStateFlow(AdOverrides())
        val h = RuntimeHarness(this)
        val runtime = h.runtime(testConfig(h.logs) { remoteConfig = AdConfigProvider { remote } })
        runtime.client.start(newActivity())
        h.settle()
        assertEquals(AdsState.Ready, h.state.value)
        val first = h.adsSdk.fullScreenAds.single()
        assertEquals(AdAvailability.Ready, runtime.client.interstitial.availability(level).value)

        remote.value = AdOverrides(adsEnabled = false)
        h.settle()

        assertEquals(remoteOff, h.state.value)
        assertTrue(first.released)
        assertEquals(AdAvailability.Disabled, runtime.client.interstitial.availability(level).value)
        val results = mutableListOf<FullScreenResult>()
        runtime.client.interstitial.show(newActivity(), level) { results += it }
        h.settle()
        assertEquals(listOf(FullScreenResult.NotShown(AdError.Disabled(UnavailableReason.DISABLED_BY_REMOTE))), results)

        remote.value = AdOverrides()
        h.settle()

        assertEquals(AdsState.Ready, h.state.value)
        assertEquals(2, h.adsSdk.fullScreenAds.size)
        assertEquals(AdAvailability.Ready, runtime.client.interstitial.availability(level).value)
        assertEquals(1, h.adsSdk.initializeCalls.size)
        assertEquals(emptyList<String>(), h.logs.errors)
    }

    @Test
    fun `remote disable before start - the SDK still initializes, and the state settles on DISABLED_BY_REMOTE`() =
        runTest {
            val h = RuntimeHarness(this)
            val runtime = h.runtime(testConfig(h.logs) { remoteConfig = AdConfigProvider { MutableStateFlow(AdOverrides(adsEnabled = false)) } })
            h.settle()
            val results = mutableListOf<AdsState>()
            runtime.client.start(newActivity()) { results += it }
            h.settle()
            assertEquals(listOf<AdsState>(remoteOff), results)
            assertTrue(h.adsSdk.fullScreenAds.isEmpty())
            assertEquals(emptyList<String>(), h.logs.errors)
        }

    @Test
    fun `ad-free beats a remote disable, and lifting ad-free leaves the remote disable in force`() = runTest {
        val remote = MutableStateFlow(AdOverrides())
        val h = RuntimeHarness(this)
        val runtime = h.runtime(testConfig(h.logs) { remoteConfig = AdConfigProvider { remote } })
        runtime.client.start(newActivity())
        h.settle()
        runtime.client.setAdFree(true)
        remote.value = AdOverrides(adsEnabled = false)
        h.settle()
        assertEquals(AdsState.Unavailable(UnavailableReason.AD_FREE_USER), h.state.value)
        runtime.client.setAdFree(false)
        h.settle()
        assertEquals(remoteOff, h.state.value)
    }

    @Test
    fun `configured listeners get every event on the main thread, in order, and one failing doesn't stop the others`() =
        runTest {
            val calls = mutableListOf<String>()
            val threads = mutableSetOf<Thread>()
            val first = AdEventListener { calls += "first:${it::class.simpleName}"; threads += Thread.currentThread() }
            val failing = AdEventListener { throw IllegalStateException("host bug") }
            val last = AdEventListener { calls += "last:${it::class.simpleName}"; threads += Thread.currentThread() }
            val h = RuntimeHarness(this)
            val runtime = h.runtime(
                testConfig(h.logs) {
                    eventListeners += first
                    eventListeners += failing
                    eventListeners += last
                },
            )
            runtime.client.start(newActivity())
            h.settle()

            assertTrue(calls.isNotEmpty())
            // Pairs of first/last for each event, in emission order.
            assertEquals(calls.size / 2, calls.count { it.startsWith("first:") })
            calls.chunked(2).forEach { (a, b) -> assertEquals(a.removePrefix("first:"), b.removePrefix("last:")) }
            assertTrue("a Loaded event reached the listeners", "first:Loaded" in calls)
            assertEquals(setOf(Looper.getMainLooper().thread), threads)
            assertTrue(h.bus.recent().any { it is AdEvent.Loaded })
        }
}
