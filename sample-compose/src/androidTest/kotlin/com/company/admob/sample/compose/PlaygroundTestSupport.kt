package com.company.admob.sample.compose

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.company.admob.CompanyAdMob
import com.company.admob.sample.compose.ui.Destination
import org.junit.rules.ExternalResource

/**
 * The test loads real Google test ads, so it needs network access. The nightly CI job runs these with
 * `-Pandroid.testInstrumentationRunnerArguments.annotation=com.company.admob.sample.compose.RequiresNetwork`;
 * offline runs can exclude them with `notAnnotation`. Every such test must also pass when no ad loads.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class RequiresNetwork

/**
 * Turns automatic app open ads off for the test, before its Activity starts: every test after the first brings the
 * process back to the foreground, and an app open ad over the playground would hide it from the Compose test rule.
 */
class AppOpenDisabledRule : ExternalResource() {
    override fun before() = setEnabled(false)

    override fun after() = setEnabled(true)

    private fun setEnabled(enabled: Boolean) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { CompanyAdMob.client.appOpen.setEnabled(enabled) }
    }
}

/** Opens [destination] through the navigation drawer. */
fun ComposeTestRule.navigateTo(destination: Destination) {
    onNodeWithTag("open_drawer").performClick()
    onNodeWithTag("nav_${destination.route}").performScrollTo().performClick()
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

/** The node's text, joined. */
fun SemanticsNodeInteraction.text(): String =
    fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }
