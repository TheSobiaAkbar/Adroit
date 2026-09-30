package io.github.thesobiaakbar.adroit.sample.xml

import android.app.Activity
import android.os.SystemClock
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.fail

/**
 * Waits for an Activity of type [T] to be resumed, and keeps the screen on while it's shown (long tests wait out
 * frequency limits, longer than a phone's screen timeout). Whenever the resumed Activity isn't one of the sample's (a
 * full-screen ad from the Google SDK is covering the app), closes it as a user would; see [closeAd].
 */
// 25 s: a launch runs consent (at most 8 s for the form) and the splash timer (11 s) before MainActivity (docs/SPLASH.md).
inline fun <reified T : Activity> awaitResumed(timeoutMillis: Long = 25_000): T {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device = UiDevice.getInstance(instrumentation)
    val deadline = SystemClock.elapsedRealtime() + timeoutMillis
    var lastBack = 0L
    while (SystemClock.elapsedRealtime() < deadline) {
        var target: T? = null
        var foreign = false
        instrumentation.runOnMainSync {
            val resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            target = resumed.filterIsInstance<T>().firstOrNull()
                ?.also { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            foreign = resumed.any { !it.javaClass.name.startsWith(SAMPLE_PACKAGE) }
        }
        target?.let { return it }
        if (foreign && SystemClock.elapsedRealtime() - lastBack >= BACK_INTERVAL_MILLIS) {
            closeAd(device)
            lastBack = SystemClock.elapsedRealtime()
        }
        SystemClock.sleep(POLL_MILLIS)
    }
    fail("${T::class.java.simpleName} wasn't resumed within $timeoutMillis ms")
    error("unreachable")
}

/**
 * Taps the view [id] of the resumed sample Activity, for a button that may open a full-screen ad. Espresso's
 * `perform(click())` fails when an ad covers the app right after the click (`NoActivityResumedException`); this taps
 * on the main thread and returns, so the caller then handles the ad ([dismissFullScreenAdIfShown]) or the next screen.
 */
fun tapOpeningAd(id: Int) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.runOnMainSync {
        val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            .first { it.javaClass.name.startsWith(SAMPLE_PACKAGE) }
        activity.findViewById<android.view.View>(id).performClick()
    }
}

/**
 * Waits up to [showTimeoutMillis] for a full-screen ad (an Activity outside the sample) to cover the app, then closes
 * it (see [closeAd]). Returns whether an ad showed: without network or fill, none does, and the caller's flow must
 * continue anyway.
 */
fun dismissFullScreenAdIfShown(showTimeoutMillis: Long = 15_000, dismissTimeoutMillis: Long = 60_000): Boolean {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device = UiDevice.getInstance(instrumentation)
    fun foregroundIsAd(): Boolean {
        var foreign = false
        instrumentation.runOnMainSync {
            foreign = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .any { !it.javaClass.name.startsWith(SAMPLE_PACKAGE) }
        }
        return foreign
    }

    val showDeadline = SystemClock.elapsedRealtime() + showTimeoutMillis
    while (!foregroundIsAd()) {
        if (SystemClock.elapsedRealtime() > showDeadline) return false
        SystemClock.sleep(POLL_MILLIS)
    }
    val dismissDeadline = SystemClock.elapsedRealtime() + dismissTimeoutMillis
    while (foregroundIsAd()) {
        if (SystemClock.elapsedRealtime() > dismissDeadline) fail("The ad wasn't dismissed within $dismissTimeoutMillis ms")
        closeAd(device)
        SystemClock.sleep(BACK_INTERVAL_MILLIS)
    }
    return true
}

/**
 * Taps the ad's close button, or presses back when there's none yet. Google's interstitial and rewarded test ads
 * have a button with the content description "Close"; the app open test ad is a web page whose close button is the
 * text "Continue to app" and ignores back. Google's ads ignore back while they can't be closed. Callers wait
 * [BACK_INTERVAL_MILLIS] between calls: back presses sent faster queue up and, once the ad closes, close the app's
 * own screens.
 */
fun closeAd(device: UiDevice) {
    val close = device.findObject(By.desc("Close").clickable(true))
        ?: device.findObject(By.text("Continue to app"))
    if (close != null) close.click() else device.pressBack()
}

const val SAMPLE_PACKAGE: String = "io.github.thesobiaakbar.adroit.sample.xml"
const val POLL_MILLIS: Long = 250
const val BACK_INTERVAL_MILLIS: Long = 2_000
