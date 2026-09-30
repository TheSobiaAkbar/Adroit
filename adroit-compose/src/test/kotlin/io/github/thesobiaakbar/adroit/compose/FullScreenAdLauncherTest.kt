package io.github.thesobiaakbar.adroit.compose

import android.app.Activity
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableIntStateOf
import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.FullScreenResult
import io.github.thesobiaakbar.adroit.RewardResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.lang.ref.WeakReference

@RunWith(RobolectricTestRunner::class)
class FullScreenAdLauncherTest {
    private val client = TestAdsClient()

    @Before
    fun setUp() {
        client.install()
    }

    @After
    fun tearDown() {
        TestAdsClient.uninstall()
    }

    @Test
    fun `each call resolves the Activity at call time, across recreation`() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val launchers = mutableListOf<FullScreenAdLauncher>()
        controller.get().setContent { launchers += rememberFullScreenAdLauncher() }
        idle()
        val results = mutableListOf<FullScreenResult>()
        launchers.last().showInterstitial(LEVEL_END) { results += it }
        assertSame(controller.get(), client.interstitial.shownOn.single())

        controller.recreate()
        controller.get().setContent { launchers += rememberFullScreenAdLauncher() }
        idle()
        launchers.last().showInterstitial(LEVEL_END) { results += it }
        assertSame(controller.get(), client.interstitial.shownOn.last())
        idle()
        assertEquals(List(2) { FullScreenResult.Dismissed(clicked = false) }, results)
    }

    @Test
    fun `the launcher is stable across recompositions`() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val launchers = mutableListOf<FullScreenAdLauncher>()
        val tick = mutableIntStateOf(0)
        controller.get().setContent {
            BasicText("tick ${tick.intValue}")
            launchers += rememberFullScreenAdLauncher()
        }
        idle()
        repeat(3) {
            tick.intValue++
            idle()
        }
        assertTrue(launchers.size > 1)
        assertTrue(launchers.all { it === launchers.first() })
    }

    @Test
    fun `after the composition is disposed, no Activity is held and calls report ActivityUnavailable`() {
        lateinit var launcher: FullScreenAdLauncher
        val activity = showAndDestroy { launcher = it }
        assertCollected(activity)

        val interstitial = mutableListOf<FullScreenResult>()
        val rewarded = mutableListOf<RewardResult>()
        launcher.showInterstitial(LEVEL_END) { interstitial += it }
        launcher.showRewarded(BONUS) { rewarded += it }
        // Never synchronous, like the core APIs.
        assertTrue(interstitial.isEmpty() && rewarded.isEmpty())
        idle()
        assertEquals(listOf(FullScreenResult.NotShown(AdError.ActivityUnavailable)), interstitial)
        assertEquals(listOf(RewardResult.Failed(AdError.ActivityUnavailable)), rewarded)
        assertTrue(client.interstitial.shownOn.isEmpty() && client.rewarded.shownOn.isEmpty())
    }

    @Test
    fun `showRewarded shows over the composition's Activity`() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        lateinit var launcher: FullScreenAdLauncher
        controller.get().setContent { launcher = rememberFullScreenAdLauncher() }
        idle()
        val results = mutableListOf<RewardResult>()
        launcher.showRewarded(BONUS) { results += it }
        idle()
        assertSame(controller.get(), client.rewarded.shownOn.single())
        assertEquals(listOf(RewardResult.NotEarned), results)
    }

    private fun showAndDestroy(keep: (FullScreenAdLauncher) -> Unit): WeakReference<Activity> {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller.get().setContent { keep(rememberFullScreenAdLauncher()) }
        idle()
        controller.pause().stop().destroy()
        idle()
        return WeakReference(controller.get())
    }
}

internal fun idle() {
    repeat(3) { shadowOf(Looper.getMainLooper()).idle() }
}

internal fun assertCollected(ref: WeakReference<*>) {
    repeat(20) {
        if (ref.get() == null) return
        System.gc()
        Thread.sleep(10)
    }
    assertNull("still strongly reachable", ref.get())
}
