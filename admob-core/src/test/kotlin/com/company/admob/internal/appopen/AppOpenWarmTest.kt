package com.company.admob.internal.appopen

import androidx.lifecycle.Lifecycle
import com.company.admob.AdAvailability
import com.company.admob.AdFormat
import com.company.admob.config.PreloadStrategy
import com.company.admob.fakes.FakeAdsSdk.ScriptedLoad
import com.company.admob.internal.fullscreen.FullScreenFormatStrategy
import com.company.admob.internal.fullscreen.LEVEL_END
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
class AppOpenWarmTest {
    private fun AppOpenHarness.notShownBecause(fragment: String, showsSoFar: Int = 0) {
        assertEquals("shows", showsSoFar, shows)
        assertTrue("log mentions '$fragment': ${h.infoLogs}", h.infoLogs.any { "App open not shown" in it && fragment in it })
    }

    @Test
    fun `warm foreground with a cached ad and every gate passing - shown once`() = runTest {
        val a = AppOpenHarness(this).loaded()
        a.returnToApp()
        assertEquals(1, a.shows)
        assertEquals(listOf(a.activity), a.showing().shownIn)

        a.showing().simulateFullShow()
        a.h.run()
        assertFalse(a.h.coordinator.isShowing)
        assertEquals(1, a.shows)
    }

    @Test
    fun `first launches - not shown until the launch count exceeds the minimum`() = runTest {
        val a = AppOpenHarness(this).loaded()
        for (launch in 1..2) {
            a.launchCount = launch
            a.returnToApp()
            a.notShownBecause("launch $launch")
        }
        assertTrue("the ad stays cached", a.h.engine.isReady(APP_OPEN))
        a.launchCount = 3
        a.returnToApp()
        assertEquals(1, a.shows)
    }

    @Test
    fun `the cold start ON_START isn't shown automatically`() = runTest {
        val a = AppOpenHarness(this).loaded()
        a.coldStart()
        assertEquals(0, a.shows)
        assertTrue(a.h.engine.isReady(APP_OPEN))
    }

    @Test
    fun `skipNextForeground - the next foreground is skipped, the following one shows`() = runTest {
        val a = AppOpenHarness(this).loaded()
        a.controller.skipNextForeground()
        a.returnToApp()
        a.notShownBecause("skipNextForeground")
        a.returnToApp()
        assertEquals(1, a.shows)
    }

    @Test
    fun `suppressWhile - suppressed while started, released on destroy`() = runTest {
        val a = AppOpenHarness(this).loaded()
        val owner = TestLifecycleOwner()
        owner.moveTo(Lifecycle.State.STARTED)
        a.controller.suppressWhile(owner)

        a.returnToApp()
        a.notShownBecause("suppressWhile")

        owner.moveTo(Lifecycle.State.CREATED) // stopped: registered, but inactive
        a.returnToApp()
        assertEquals(1, a.shows)

        owner.moveTo(Lifecycle.State.DESTROYED)
        assertEquals(0, a.suppression.suppressorCount)
    }

    @Test
    fun `suppressWhile doesn't leak its owner after ON_DESTROY`() = runTest {
        val a = AppOpenHarness(this)
        val ref = suppressAndDestroy(a)
        assertEquals(0, a.suppression.suppressorCount)
        assertCollected(ref)
    }

    private fun suppressAndDestroy(a: AppOpenHarness): WeakReference<TestLifecycleOwner> {
        val owner = TestLifecycleOwner()
        owner.moveTo(Lifecycle.State.RESUMED)
        a.controller.suppressWhile(owner)
        owner.moveTo(Lifecycle.State.DESTROYED)
        return WeakReference(owner)
    }

    @Test
    fun `suppressWhile ignores an owner that's already destroyed`() = runTest {
        val a = AppOpenHarness(this)
        val owner = TestLifecycleOwner()
        owner.moveTo(Lifecycle.State.CREATED)
        owner.moveTo(Lifecycle.State.DESTROYED)
        a.controller.suppressWhile(owner)
        assertEquals(0, a.suppression.suppressorCount)
    }

    @Test
    fun `returning from an interstitial click-through within 5 s - not shown`() = runTest {
        val a = AppOpenHarness(this).loaded()
        val interstitial = a.h.engine(FullScreenFormatStrategy.Interstitial)
        a.h.run(1_000)
        interstitial.show(a.activity, LEVEL_END) {}
        a.h.run()
        val ad = a.h.adsSdk.fullScreenAds.single { it.format == AdFormat.INTERSTITIAL && it.showCount == 1 }
        ad.simulateShown()
        ad.simulateClicked()

        // The click-through leaves the app while the interstitial is still up.
        a.returnToApp(awayFor = 20.seconds)
        a.notShownBecause("another full-screen ad is showing")

        // No impression, so only the post-full-screen window (not the global interval) holds app open back.
        ad.simulateDismissed()
        a.h.run()
        a.returnToApp(awayFor = 4.seconds)
        a.notShownBecause("too recently")

        a.returnToApp(awayFor = 2.seconds)
        assertEquals(1, a.shows)
    }

    @Test
    fun `an excluded Activity (splash) - not shown`() = runTest {
        val a = AppOpenHarness(this) { excludeActivities(SplashActivity::class.java) }.loaded()
        val splash = Robolectric.buildActivity(SplashActivity::class.java).setup().get()
        a.h.tracker.resumedActivity = splash
        a.returnToApp()
        a.notShownBecause("SplashActivity is excluded")

        a.h.tracker.resumedActivity = a.activity
        a.returnToApp()
        assertEquals(1, a.shows)
    }

    @Test
    fun `waits up to 1 s for an Activity to resume after ON_START`() = runTest {
        val a = AppOpenHarness(this).loaded()
        a.h.tracker.resumedActivity = null
        a.returnToApp()
        a.h.run(400)
        assertEquals(0, a.shows)
        a.h.tracker.resumedActivity = a.activity
        a.h.run(100)
        assertEquals(1, a.shows)
    }

    @Test
    fun `no Activity resumed within 1 s - not shown`() = runTest {
        val a = AppOpenHarness(this).loaded()
        a.h.tracker.resumedActivity = null
        a.returnToApp()
        a.h.run(1_050)
        a.h.tracker.resumedActivity = a.activity
        a.h.run(1_000)
        a.notShownBecause("no Activity resumed in time")
    }

    @Test
    fun `cooldown - not shown again within 4 min`() = runTest {
        val a = AppOpenHarness(this).loaded()
        a.returnToApp()
        a.showing().simulateFullShow()
        a.h.run(1_000) // the reload completes

        a.returnToApp(awayFor = 3.minutes)
        a.notShownBecause("cooldown", showsSoFar = 1)

        a.returnToApp(awayFor = 1.minutes)
        assertEquals(2, a.shows)
    }

    @Test
    fun `no cached ad - not shown, a load starts, and it's never shown late`() = runTest {
        val a = AppOpenHarness(this) { preload = PreloadStrategy.ON_DEMAND }
        a.loaded()
        assertEquals(0, a.loads())
        a.h.adsSdk.enqueueLoad(a.adUnit, ScriptedLoad.Success(), delayMs = 2_000)

        a.returnToApp()
        a.notShownBecause("no fresh ad")
        assertEquals(1, a.loads())

        a.h.run(3_000) // loaded while still in the foreground
        assertTrue(a.h.engine.isReady(APP_OPEN))
        assertEquals(0, a.shows)
    }

    @Test
    fun `expired ad (3 h 56 min) - not shown, and it reloads`() = runTest {
        val a = AppOpenHarness(this).loaded()
        val stale = a.appOpenAds.single()
        val loadsBefore = a.loads()

        a.returnToApp(awayFor = 3.hours + 56.minutes)
        a.notShownBecause("no fresh ad")
        assertTrue(stale.released)
        assertEquals(0, stale.showCount)
        assertEquals(loadsBefore + 1, a.loads())
    }

    @Test
    fun `another full-screen ad showing - not shown`() = runTest {
        val a = AppOpenHarness(this).loaded()
        val interstitial = a.h.engine(FullScreenFormatStrategy.Interstitial)
        a.h.run(1_000)
        interstitial.show(a.activity, LEVEL_END) {}
        a.h.run()
        a.returnToApp()
        a.notShownBecause("another full-screen ad is showing")
    }

    @Test
    fun `not ready, ad-free or disabled placement - not shown`() = runTest {
        val a = AppOpenHarness(this)
        a.returnToApp()
        a.notShownBecause("ads aren't ready")

        val disabled = AppOpenHarness(this) { enabled = false }.loaded()
        disabled.returnToApp()
        disabled.notShownBecause("disabled")
        assertEquals(AdAvailability.Disabled, disabled.controller.availability().value)
    }

    @Test
    fun `setEnabled(false) stops automatic shows but keeps the cache, and true shows again`() = runTest {
        val a = AppOpenHarness(this).loaded()
        a.controller.setEnabled(false)
        a.returnToApp()
        a.notShownBecause("disabled")
        assertTrue(a.h.engine.isReady(APP_OPEN))

        a.controller.setEnabled(true)
        a.returnToApp()
        assertEquals(1, a.shows)
    }

    @Test
    fun `leaving the foreground while waiting for an Activity - not shown`() = runTest {
        val a = AppOpenHarness(this).loaded()
        a.h.tracker.resumedActivity = null
        a.returnToApp()
        a.h.foreground.moveToBackground()
        a.h.tracker.resumedActivity = a.activity
        a.h.run(100)
        a.notShownBecause("left the foreground")
    }

    @Test
    fun `rapid background and foreground x20 - at most one show`() = runTest {
        val a = AppOpenHarness(this).loaded()
        repeat(20) {
            a.returnToApp(awayFor = 100.milliseconds)
            // Dismiss whatever is showing at once, the worst case for loops: the reload completes immediately.
            a.appOpenAds.filter { it.showCount == 1 && !it.released }.forEach { it.simulateFullShow() }
            a.h.run(50)
        }
        assertEquals(1, a.shows)
        assertNull(a.h.coordinator.currentOwner)
    }
}

/** Asserts [ref] can be garbage collected: nothing in the library still holds it. */
internal fun assertCollected(ref: WeakReference<*>) {
    repeat(20) {
        if (ref.get() == null) return
        System.gc()
        Thread.sleep(10)
    }
    assertNull("still strongly reachable", ref.get())
}
