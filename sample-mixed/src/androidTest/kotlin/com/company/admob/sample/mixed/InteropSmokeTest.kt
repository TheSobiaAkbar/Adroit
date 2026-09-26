package com.company.admob.sample.mixed

import android.app.Activity
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens the XML screen hosting Compose and the Compose screen hosting XML, rotates each, and goes back: no crash.
 * Passes offline too (ads just don't show), so it isn't network-gated.
 */
@RunWith(AndroidJUnit4::class)
class InteropSmokeTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun bothInteropScreensSurviveRotationAndBack() {
        ActivityScenario.launch(MainActivity::class.java).use {
            try {
                visit<LegacyXmlActivity>(R.id.open_legacy_xml)
                visit<ComposeHostActivity>(R.id.open_compose_host)
            } finally {
                device.unfreezeRotation()
            }
        }
    }

    private inline fun <reified T : Activity> visit(buttonId: Int) {
        onView(withId(buttonId)).perform(click())
        awaitResumed<T>()
        device.setOrientationLeft()
        awaitResumed<T>()
        device.setOrientationNatural()
        awaitResumed<T>()
        pressBack()
        awaitResumed<MainActivity>()
    }

    private inline fun <reified T : Activity> awaitResumed(timeoutMillis: Long = 10_000) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            var resumed = false
            instrumentation.runOnMainSync {
                resumed = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).any { it is T }
            }
            if (resumed) return
            SystemClock.sleep(250)
        }
        fail("${T::class.java.simpleName} wasn't resumed within $timeoutMillis ms")
    }
}
