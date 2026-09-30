package com.company.admob.sample.compose

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.company.admob.sample.compose.ui.Destination
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Visits Home, Feed, Settings and Native styles, rotating on each: no crash, and LeakCanary finds nothing retained
 * afterwards (banners are destroyed when their destination leaves the screen, native ads when it's popped).
 */
@RequiresNetwork
@RunWith(AndroidJUnit4::class)
class NavigationLeakTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(notificationsGranted())
        .around(DetectLeaksAfterTestSuccess())
        .around(AppOpenDisabledRule())
        .around(compose)

    private val device = uiDevice()

    @Test
    fun everyScreenSurvivesRotation() {
        try {
            listOf(Destination.Home, Destination.Feed, Destination.Settings).forEach { destination ->
                compose.navigateTo(destination)
                compose.rotateAndBack(device)
            }
            compose.onNodeWithText("Native styles").performClick()
            compose.waitForIdle()
            compose.rotateAndBack(device)
            Espresso.pressBack() // pops Native styles: its native ads are destroyed
            compose.navigateTo(Destination.Home)
        } finally {
            device.unfreezeRotation()
        }
    }
}
