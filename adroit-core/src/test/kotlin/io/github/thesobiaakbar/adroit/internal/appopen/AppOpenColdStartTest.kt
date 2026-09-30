package io.github.thesobiaakbar.adroit.internal.appopen

import android.app.Activity
import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.AdsState
import io.github.thesobiaakbar.adroit.FullScreenResult
import io.github.thesobiaakbar.adroit.UnavailableReason
import io.github.thesobiaakbar.adroit.fakes.FakeAdsSdk.ScriptedLoad
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.lang.ref.WeakReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
class AppOpenColdStartTest {
    private fun notShown(error: AdError) = FullScreenResult.NotShown(error)

    @Test
    fun `an ad loads within maxWait - shown, and onComplete only after dismissal`() = runTest {
        val a = AppOpenHarness(this)
        a.coldStart()
        a.showOnColdStart()
        a.h.run(500)
        assertEquals("not ready yet, no load", 0, a.loads())

        a.h.ready() // consent done: the load starts at once
        a.h.run(100)
        assertEquals(1, a.shows)
        assertTrue("no result while the ad is up", a.coldStartResults.isEmpty())

        a.showing().simulateFullShow()
        a.h.run()
        assertEquals(listOf<FullScreenResult>(FullScreenResult.Dismissed(clicked = false)), a.coldStartResults)
    }

    @Test
    fun `timeout - NotShown(Timeout), and the ad is kept for next time`() = runTest {
        val a = AppOpenHarness(this)
        a.h.adsSdk.enqueueLoad(a.adUnit, ScriptedLoad.Success(), delayMs = 6_000)
        a.h.ready()
        a.showOnColdStart(maxWait = 4.seconds)
        a.h.run(3_900)
        assertTrue(a.coldStartResults.isEmpty())
        a.h.run(200)
        assertEquals(listOf(notShown(AdError.Timeout())), a.coldStartResults)

        a.h.run(3_000)
        assertTrue("kept", a.h.engine.isReady(APP_OPEN))
        assertEquals(0, a.shows)
        assertEquals(1, a.coldStartResults.size)
    }

    @Test
    fun `Activity destroyed during the wait - ActivityUnavailable, and it isn't leaked`() = runTest {
        val a = AppOpenHarness(this)
        a.h.adsSdk.defaultLoad = ScriptedLoad.Pending
        val ref = startAndDestroy(a)
        a.h.run(100)
        assertEquals(listOf(notShown(AdError.ActivityUnavailable)), a.coldStartResults)
        a.h.run(5_000)
        assertEquals(1, a.coldStartResults.size)
        assertCollected(ref)
    }

    private fun startAndDestroy(a: AppOpenHarness): WeakReference<Activity> {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        a.showOnColdStart(controller.get())
        a.h.run(100)
        controller.pause().stop().destroy()
        return WeakReference(controller.get())
    }

    @Test
    fun `Activity paused during the wait - ActivityUnavailable`() = runTest {
        val a = AppOpenHarness(this)
        a.showOnColdStart()
        a.h.run(100)
        a.h.tracker.invalidActivities += a.activity
        a.h.run(100)
        assertEquals(listOf(notShown(AdError.ActivityUnavailable)), a.coldStartResults)
    }

    @Test
    fun `an Activity not resumed yet is waited for`() = runTest {
        val a = AppOpenHarness(this).loaded()
        a.h.tracker.invalidActivities += a.activity // called from onCreate, before onResume
        a.showOnColdStart()
        a.h.run(300)
        assertEquals(0, a.shows)
        a.h.tracker.invalidActivities -= a.activity
        a.h.run(100)
        assertEquals(1, a.shows)
    }

    @Test
    fun `called twice - the second gets AlreadyShowing, the first carries on`() = runTest {
        val a = AppOpenHarness(this)
        val second = mutableListOf<FullScreenResult>()
        a.showOnColdStart()
        a.controller.showOnColdStart(a.activity, 4.seconds) { second += it }
        a.h.run()
        assertEquals(listOf(notShown(AdError.AlreadyShowing)), second)

        a.loaded()
        a.showing().simulateFullShow()
        a.h.run()
        assertEquals(listOf<FullScreenResult>(FullScreenResult.Dismissed(clicked = false)), a.coldStartResults)

        // Once the first completes, a new call is evaluated again (here it meets the cooldown).
        a.controller.showOnColdStart(a.activity, 1.seconds) { second += it }
        a.h.run(1_100)
        assertEquals(2, second.size)
        assertTrue(((second[1] as FullScreenResult.NotShown).error) is AdError.FrequencyCapped)
    }

    @Test
    fun `showOnColdStartAllowed = false - NotShown(Disabled) at once`() = runTest {
        val a = AppOpenHarness(this) { showOnColdStartAllowed = false }.loaded()
        a.showOnColdStart()
        a.h.run()
        assertEquals(listOf(notShown(AdError.Disabled(UnavailableReason.DISABLED_BY_CONFIG))), a.coldStartResults)
        assertEquals(0, a.shows)
    }

    @Test
    fun `first launches - not shown on cold start either`() = runTest {
        val a = AppOpenHarness(this).loaded()
        a.launchCount = 1
        a.showOnColdStart()
        a.h.run()
        assertEquals(listOf(notShown(AdError.FrequencyCapped(Duration.INFINITE))), a.coldStartResults)
    }

    @Test
    fun `launch count not read yet - not shown, as not ready`() = runTest {
        val a = AppOpenHarness(this).loaded()
        a.launchCount = 0
        a.showOnColdStart()
        a.h.run()
        assertEquals(listOf(notShown(AdError.NotReady)), a.coldStartResults)
        assertEquals(0, a.shows)
    }

    @Test
    fun `ads become unavailable during the wait - completes at once with that reason`() = runTest {
        val a = AppOpenHarness(this)
        a.showOnColdStart()
        a.h.run(200)
        a.h.readiness.state.value = AdsState.Unavailable(UnavailableReason.CONSENT_NOT_OBTAINED)
        a.h.run(100)
        assertEquals(listOf(notShown(AdError.ConsentRequired)), a.coldStartResults)
    }

    @Test
    fun `setEnabled(false) ends a pending wait with Disabled`() = runTest {
        val a = AppOpenHarness(this)
        a.showOnColdStart()
        a.h.run(200)
        a.controller.setEnabled(false)
        a.h.run(5_000)
        assertEquals(listOf(notShown(AdError.Disabled(UnavailableReason.DISABLED_BY_CONFIG))), a.coldStartResults)
    }

    @Test
    fun `the excluded splash doesn't block its own showOnColdStart`() = runTest {
        val a = AppOpenHarness(this) { excludeActivities(SplashActivity::class.java) }.loaded()
        val splash = Robolectric.buildActivity(SplashActivity::class.java).setup().get()
        a.showOnColdStart(splash)
        a.h.run()
        assertEquals(listOf(splash), a.showing().shownIn)
    }

    @Test
    fun `no second app open within the cooldown after a cold start show, even without an impression`() = runTest {
        val a = AppOpenHarness(this).loaded()
        a.showOnColdStart()
        a.h.run()
        val ad = a.showing()
        ad.simulateShown()
        ad.simulateDismissed() // no impression: FrequencyGate recorded nothing
        a.h.run(1_000)

        a.returnToApp(awayFor = 30.seconds)
        assertEquals(1, a.shows)
        assertTrue(a.h.infoLogs.any { "App open not shown: cooldown" in it })
    }

    @Test
    fun `a warm foreground during a cold start wait doesn't show`() = runTest {
        val a = AppOpenHarness(this)
        a.h.adsSdk.defaultLoad = ScriptedLoad.Pending
        a.h.ready()
        a.showOnColdStart()
        a.returnToApp(awayFor = 100.milliseconds)
        assertTrue(a.h.infoLogs.any { "showOnColdStart() call is in progress" in it })
    }
}
