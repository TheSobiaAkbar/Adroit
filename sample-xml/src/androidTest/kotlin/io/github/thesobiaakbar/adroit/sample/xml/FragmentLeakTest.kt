package io.github.thesobiaakbar.adroit.sample.xml

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
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
 * Switching tabs (each Fragment's banner and native ads are destroyed or detached with its view) and rotating
 * (Activity recreated; native ads kept by ViewModels) must leave no leaked Activity, Fragment, View or ad.
 * LeakCanary fails the test if anything is retained after it passes.
 */
@RequiresNetwork
@RunWith(AndroidJUnit4::class)
class FragmentLeakTest {
    @get:Rule
    val detectLeaks = DetectLeaksAfterTestSuccess()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val tabs = listOf(R.id.nav_home, R.id.nav_feed, R.id.nav_settings)

    @Test
    fun tabsAndRotationsLeaveNoLeaks() {
        ActivityScenario.launch(SplashActivity::class.java).use {
            awaitResumed<MainActivity>()
            repeat(10) { index -> onView(withId(tabs[index % tabs.size])).perform(click()) }
            onView(withId(R.id.nav_feed)).perform(click())
            try {
                repeat(3) {
                    device.setOrientationLeft()
                    awaitResumed<MainActivity>()
                    device.setOrientationNatural()
                    awaitResumed<MainActivity>()
                }
            } finally {
                device.unfreezeRotation()
            }
        }
    }
}
