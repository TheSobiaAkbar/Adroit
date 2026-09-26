package com.company.admob.sample.xml

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Banners: the Home banner (XML, Fragment view lifecycle) through 5 rotations, then the Details screen (a banner
 * attached from code, and a native ad) opened and left 5 times. LeakCanary fails the test if an Activity, Fragment,
 * View or ad is retained afterwards.
 */
@RequiresNetwork
@RunWith(AndroidJUnit4::class)
class BannerLeakTest {
    @get:Rule
    val detectLeaks = DetectLeaksAfterTestSuccess()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun bannersSurviveRotationAndNavigationWithoutLeaks() {
        ActivityScenario.launch(SplashActivity::class.java).use {
            awaitResumed<MainActivity>()
            try {
                repeat(5) {
                    device.setOrientationLeft()
                    awaitResumed<MainActivity>()
                    device.setOrientationNatural()
                    awaitResumed<MainActivity>()
                }
            } finally {
                device.unfreezeRotation()
            }
            repeat(5) {
                // The first may show an interstitial (dismissed by awaitResumed); later ones are frequency capped.
                onView(withId(R.id.level_complete)).perform(click())
                awaitResumed<DetailsActivity>(timeoutMillis = 15_000)
                pressBack()
                awaitResumed<MainActivity>()
            }
        }
    }
}
