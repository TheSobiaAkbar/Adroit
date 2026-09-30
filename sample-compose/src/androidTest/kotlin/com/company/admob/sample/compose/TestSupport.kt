package com.company.admob.sample.compose

import android.Manifest
import android.os.Build
import androidx.test.rule.GrantPermissionRule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
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

/**
 * Grants the notification permission (Android 13+). Without it, LeakCanary opens its permission screen over the app
 * as soon as it counts retained objects (old screens not collected yet), and the Compose rule loses the app.
 */
fun notificationsGranted(): TestRule =
    if (Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS) else RuleChain.emptyRuleChain()

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
