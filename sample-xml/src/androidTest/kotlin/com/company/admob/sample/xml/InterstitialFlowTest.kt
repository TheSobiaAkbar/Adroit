package com.company.admob.sample.xml

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** "Level complete" reaches DetailsActivity in both branches: the interstitial showed (dismissed) or it didn't. */
@RequiresNetwork
@RunWith(AndroidJUnit4::class)
class InterstitialFlowTest {
    @Test
    fun levelCompleteAlwaysOpensDetails() {
        ActivityScenario.launch(SplashActivity::class.java).use {
            awaitResumed<MainActivity>()
            onView(withId(R.id.level_complete)).perform(click())
            awaitResumed<DetailsActivity>(timeoutMillis = 15_000)
        }
    }
}
