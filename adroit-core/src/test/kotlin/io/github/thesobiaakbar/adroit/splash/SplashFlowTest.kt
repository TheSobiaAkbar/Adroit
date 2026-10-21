package io.github.thesobiaakbar.adroit.splash

import android.app.Activity
import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.AdFormat
import io.github.thesobiaakbar.adroit.FullScreenResult
import io.github.thesobiaakbar.adroit.config.SplashTimings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration

/** Every path of the splash sequence, on virtual time (docs/SPLASH.md). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SplashFlowTest {
    private class FakePorts : SplashPorts {
        var timings = SplashTimings()
        var online = true
        var resumed = true
        var unavailable = false
        var adAvailable = true
        var adReady = false
        val startCalls = mutableListOf<Duration>()
        var finishStart: (() -> Unit)? = null
        var answerStartAtOnce = true
        var formShowing = false
        val requested = mutableListOf<String>()
        var showCallback: ((FullScreenResult) -> Unit)? = null
        val abandoned = mutableListOf<String>()

        override fun timings() = timings
        override fun isOnline() = online
        override fun startAds(activity: Activity, consentTimeout: Duration, onDone: () -> Unit) {
            startCalls += consentTimeout
            if (answerStartAtOnce) onDone() else finishStart = onDone
        }
        override fun consentFormShowing() = formShowing
        override fun requestAd(key: String): Boolean {
            requested += key
            return adAvailable
        }
        var format: AdFormat? = AdFormat.INTERSTITIAL
        override fun servingFormat(key: String) = format
        override fun isAdReady(key: String) = adReady
        override fun adsUnavailable() = unavailable
        override fun isResumed(activity: Activity) = resumed
        override fun showAd(activity: Activity, key: String, onResult: (FullScreenResult) -> Unit) {
            showCallback = onResult
        }
        override fun abandonAd(key: String) {
            abandoned += key
        }
        override fun log(message: String) = Unit
    }

    private val ports = FakePorts()
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private fun TestScope.flow(premium: Boolean = false, splashAd: String? = "SplashAd"): SplashFlow =
        SplashFlow(splashAd, premium, ports, backgroundScope).also {
            it.start(activity)
            runCurrent()
        }

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `no ad in time - the progress follows the timer and the splash finishes when it ends`() = runTest {
        val flow = flow()
        assertEquals(listOf(SplashTimings().consentTimeout), ports.startCalls)
        assertEquals(listOf("SplashAd"), ports.requested)
        assertEquals(SplashState.Loading(0), flow.state.value)

        advance(5_500)
        assertEquals(SplashState.Loading(50), flow.state.value)
        advance(5_400)
        assertTrue(flow.state.value is SplashState.Loading)
        advance(200)
        assertEquals(SplashState.Finished(adShown = false), flow.state.value)
        assertEquals("a late splash ad would never show: dropped", listOf("SplashAd"), ports.abandoned)
    }

    @Test
    fun `the ad is ready - progress jumps to 100, the ad shows, and the splash waits for the user to close it`() = runTest {
        val flow = flow()
        advance(3_000)
        ports.adReady = true
        advance(100)
        assertEquals(SplashState.ShowingAd, flow.state.value)

        // Far past the timer: still on the ad.
        advance(60_000)
        assertEquals(SplashState.ShowingAd, flow.state.value)
        ports.showCallback!!(FullScreenResult.Dismissed(clicked = false))
        runCurrent()
        assertEquals(SplashState.Finished(adShown = true), flow.state.value)
    }

    @Test
    fun `a format swap during the timer requests the ad again in its new format, and shows it`() = runTest {
        val flow = flow()
        assertEquals(listOf("SplashAd"), ports.requested)
        advance(300)
        // Firebase activates a value that swaps SplashAd to an app open ad: the interstitial requested is released.
        ports.format = AdFormat.APP_OPEN
        advance(100)
        assertEquals(listOf("SplashAd", "SplashAd"), ports.requested)
        advance(1_000)
        assertEquals("no new request while the format stays", 2, ports.requested.size)
        ports.adReady = true
        advance(100)
        assertEquals(SplashState.ShowingAd, flow.state.value)
    }

    @Test
    fun `an ad that fails to show finishes the splash at once`() = runTest {
        val flow = flow()
        ports.adReady = true
        advance(100)
        ports.showCallback!!(FullScreenResult.NotShown(AdError.ShowFailed()))
        runCurrent()
        assertEquals(SplashState.Finished(adShown = false), flow.state.value)
    }

    @Test
    fun `premium - no consent, no ad, the premium timer`() = runTest {
        val flow = flow(premium = true)
        assertTrue(ports.startCalls.isEmpty())
        assertTrue(ports.requested.isEmpty())
        advance(2_900)
        assertTrue(flow.state.value is SplashState.Loading)
        advance(200)
        assertEquals(SplashState.Finished(adShown = false), flow.state.value)
    }

    @Test
    fun `offline - the library still starts, no ad is requested, the offline timer`() = runTest {
        ports.online = false
        val flow = flow()
        assertEquals(1, ports.startCalls.size)
        assertTrue(ports.requested.isEmpty())
        advance(3_100)
        assertEquals(SplashState.Finished(adShown = false), flow.state.value)
    }

    @Test
    fun `consent that never answers is abandoned after its timeout plus the guard, with no progress bar until then`() = runTest {
        ports.answerStartAtOnce = false
        val flow = flow()
        advance(9_900)
        assertEquals(SplashState.Preparing, flow.state.value)
        assertTrue(ports.requested.isEmpty())
        advance(200) // 8 s form timeout + 2 s guard
        assertEquals(SplashState.Loading(0), flow.state.value)
    }

    @Test
    fun `a consent form on screen holds the splash until the user answers - no timer, no ad request`() = runTest {
        ports.answerStartAtOnce = false
        val flow = flow()
        advance(3_000)
        ports.formShowing = true
        advance(120_000) // the user reads the form for two minutes
        assertEquals(SplashState.Preparing, flow.state.value)
        assertTrue(ports.requested.isEmpty())

        ports.formShowing = false
        ports.finishStart!!()
        advance(100)
        assertEquals(listOf("SplashAd"), ports.requested)
        assertEquals(SplashState.Loading(0), flow.state.value)
    }

    @Test
    fun `after the form closes, a start that never answers is still bounded`() = runTest {
        ports.answerStartAtOnce = false
        val flow = flow()
        ports.formShowing = true
        advance(60_000)
        ports.formShowing = false
        advance(9_900)
        assertEquals(SplashState.Preparing, flow.state.value)
        advance(200)
        assertTrue(flow.state.value is SplashState.Loading)
    }

    @Test
    fun `a recreated splash during consent hands its Activity to the consent step`() = runTest {
        ports.answerStartAtOnce = false
        val flow = flow()
        val recreated = Robolectric.buildActivity(Activity::class.java).setup().get()
        flow.start(recreated)
        assertEquals(2, ports.startCalls.size)
        ports.finishStart!!()
        advance(100)
        flow.start(recreated) // after consent: nothing to hand over
        assertEquals(2, ports.startCalls.size)
    }

    @Test
    fun `the timer uses the values active after consent, from Firebase`() = runTest {
        ports.answerStartAtOnce = false
        val flow = flow()
        ports.timings = SplashTimings(splashSeconds = 13)
        ports.finishStart!!()
        runCurrent()
        advance(12_900)
        assertTrue(flow.state.value is SplashState.Loading)
        advance(200)
        assertEquals(SplashState.Finished(adShown = false), flow.state.value)
    }

    @Test
    fun `an ad ready while the screen is in the background waits for it, and is never shown after the timer`() = runTest {
        val flow = flow()
        ports.resumed = false
        ports.adReady = true
        advance(5_000)
        assertTrue(flow.state.value is SplashState.Loading)
        advance(6_100)
        assertEquals(SplashState.Finished(adShown = false), flow.state.value)
        assertEquals(null, ports.showCallback)
    }

    @Test
    fun `ads unavailable (no consent) - the timer still runs, and the ad is no longer awaited`() = runTest {
        val flow = flow()
        ports.unavailable = true
        ports.adReady = true
        advance(11_100)
        assertEquals(SplashState.Finished(adShown = false), flow.state.value)
        assertEquals(null, ports.showCallback)
    }

    @Test
    fun `no splash ad configured, or off - just the timer`() = runTest {
        ports.adAvailable = false
        val flow = flow()
        ports.adReady = true
        advance(11_100)
        assertEquals(SplashState.Finished(adShown = false), flow.state.value)
        assertFalse(ports.showCallback != null)
    }

    @Test
    fun `a rotation hands over the new Activity without restarting`() = runTest {
        val flow = flow()
        advance(4_000)
        val rotated = Robolectric.buildActivity(Activity::class.java).setup().get()
        flow.start(rotated)
        runCurrent()
        assertEquals(1, ports.startCalls.size)
        assertEquals(SplashState.Loading(36), flow.state.value)
    }

    @Test
    fun `progress is clamped and a zero timer is complete`() {
        assertEquals(100, SplashFlow.progressOf(Duration.ZERO, Duration.ZERO))
        assertEquals(100, SplashFlow.progressOf(Duration.INFINITE, kotlin.time.Duration.parse("1s")))
    }
}
