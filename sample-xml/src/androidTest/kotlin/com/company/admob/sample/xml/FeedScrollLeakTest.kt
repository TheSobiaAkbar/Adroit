package com.company.admob.sample.xml

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Native ads in a RecyclerView: scrolling the feed down and up recycles ad rows (each rebinds its slot's
 * NativeAdHolder), then leaving the tab destroys the Fragment's view. Nothing may be retained.
 */
@RequiresNetwork
@RunWith(AndroidJUnit4::class)
class FeedScrollLeakTest {
    @get:Rule
    val detectLeaks = DetectLeaksAfterTestSuccess()

    @Test
    fun scrollingTheNativeFeedLeavesNoLeaks() {
        ActivityScenario.launch(SplashActivity::class.java).use {
            awaitResumed<MainActivity>()
            onView(withId(R.id.nav_feed)).perform(click())
            repeat(3) {
                repeat(SWIPES) { onView(withId(R.id.list)).perform(swipeUp()) }
                repeat(SWIPES) { onView(withId(R.id.list)).perform(swipeDown()) }
            }
            onView(withId(R.id.nav_home)).perform(click())
        }
    }

    private companion object {
        /** Enough to reach the end of the 30-article feed on a phone. */
        const val SWIPES = 8
    }
}
