package com.company.admob.sample.xml

import android.app.Activity
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.fail

/**
 * Waits for an Activity of type [T] to be resumed. Whenever the resumed Activity isn't one of the sample's (a
 * full-screen ad from the Google SDK is covering the app), presses back to dismiss it, as a user would.
 */
inline fun <reified T : Activity> awaitResumed(timeoutMillis: Long = 10_000): T {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device = UiDevice.getInstance(instrumentation)
    val deadline = SystemClock.elapsedRealtime() + timeoutMillis
    while (SystemClock.elapsedRealtime() < deadline) {
        var target: T? = null
        var foreign = false
        instrumentation.runOnMainSync {
            val resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            target = resumed.filterIsInstance<T>().firstOrNull()
            foreign = resumed.any { !it.javaClass.name.startsWith(SAMPLE_PACKAGE) }
        }
        target?.let { return it }
        if (foreign) device.pressBack()
        SystemClock.sleep(POLL_MILLIS)
    }
    fail("${T::class.java.simpleName} wasn't resumed within $timeoutMillis ms")
    error("unreachable")
}

const val SAMPLE_PACKAGE: String = "com.company.admob.sample.xml"
const val POLL_MILLIS: Long = 250
