package com.company.admob.sample.compose

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.company.admob.CompanyAdMob
import com.company.admob.sample.compose.ui.Destination
import org.junit.rules.ExternalResource

/**
 * The test loads real Google test ads, so it needs network access. Offline runs can exclude these with
 * `-Pandroid.testInstrumentationRunnerArguments.notAnnotation=com.company.admob.sample.compose.RequiresNetwork`.
 * Every such test must also pass when no ad loads.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class RequiresNetwork

/**
 * Turns automatic app open ads off for the test, before its Activity starts: every test after the first brings the
 * process back to the foreground, and an app open ad over the app would hide it from the Compose test rule.
 */
class AppOpenDisabledRule : ExternalResource() {
    override fun before() = setEnabled(false)

    override fun after() = setEnabled(true)

    private fun setEnabled(enabled: Boolean) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { CompanyAdMob.client.appOpen.setEnabled(enabled) }
    }
}

/** Opens a bottom-bar [destination]. */
fun ComposeTestRule.navigateTo(destination: Destination) {
    onNodeWithTag("nav_${destination.route}").performClick()
    waitForIdle()
}

/** Rotates to landscape and back, waiting for the recreated Activity each time. */
fun ComposeTestRule.rotateAndBack(device: UiDevice) {
    device.setOrientationLeft()
    waitForIdle()
    device.setOrientationNatural()
    waitForIdle()
}

fun uiDevice(): UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
