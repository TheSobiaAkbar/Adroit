package com.company.admob.sample.compose

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.company.admob.sample.compose.ui.Destination
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Visits all 12 destinations and rotates on each: no crash, and LeakCanary finds nothing retained afterwards
 * (screens, banners and native ads are released when their destination is left).
 */
@RequiresNetwork
@RunWith(AndroidJUnit4::class)
class PlaygroundNavigationTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(DetectLeaksAfterTestSuccess())
        .around(AppOpenDisabledRule())
        .around(compose)

    private val device = uiDevice()

    @Test
    fun everyDestinationSurvivesRotation() {
        try {
            Destination.entries.forEach { destination ->
                compose.navigateTo(destination)
                compose.rotateAndBack(device)
                // The back stack survived the recreation: the same destination is still shown.
                compose.onNodeWithTag("screen_title", useUnmergedTree = true).assertTextEquals(destination.title)
            }
        } finally {
            device.unfreezeRotation()
        }
    }
}
