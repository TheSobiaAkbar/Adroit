package com.company.admob.sample.compose

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.company.admob.sample.compose.ui.Destination
import com.company.admob.sample.compose.ui.screens.FEED_TAG
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Native ads in a LazyColumn: scrolling to the end and back three times (ad items leave and re-enter the
 * composition, and the retainer evicts slots beyond 3), then leaving the screen (its ads are destroyed with the
 * destination). LeakCanary finds nothing retained.
 */
@RequiresNetwork
@RunWith(AndroidJUnit4::class)
class NativeFeedLeakTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(DetectLeaksAfterTestSuccess())
        .around(AppOpenDisabledRule())
        .around(compose)

    @Test
    fun scrollingTheNativeFeedLeavesNoLeaks() {
        compose.navigateTo(Destination.Native)
        compose.onNodeWithText("Feed").performClick()
        // Step by 3 rows, so every ad item (every 7th row) is composed on the way down and up.
        val down = (0..LAST_ROW step 3) + LAST_ROW
        repeat(3) {
            (down + down.reversed()).forEach { row ->
                compose.onNodeWithTag(FEED_TAG).performScrollToIndex(row)
                compose.waitForIdle()
            }
        }
        compose.navigateTo(Destination.Dashboard)
    }

    private companion object {
        /** 42 articles and a native ad after every 6th: 49 rows. */
        const val LAST_ROW = 48
    }
}
