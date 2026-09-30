package io.github.thesobiaakbar.adroit.sample.xml

import android.app.Activity
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs against the R8-processed `minifiedDebug` build (this module's test build type): the app starts, and the
 * library's custom views are inflated from XML under their original class names, which proves the keep rules AAPT
 * generates for views referenced in layouts cover them. Passes offline too (ads just don't load).
 */
@RunWith(AndroidJUnit4::class)
class ReleaseSmokeTest {
    @Test
    fun minifiedAppStartsAndInflatesLibraryViewsFromXml() {
        ActivityScenario.launch(SplashActivity::class.java).use {
            val main = awaitResumed<MainActivity>(timeoutMillis = 10_000)
            assertEquals(SLOT_VIEW, viewClassName(main, R.id.home_banner))

            onView(withId(R.id.level_complete)).perform(click())
            val details = awaitResumed<DetailsActivity>(timeoutMillis = 15_000)
            assertEquals(NATIVE_VIEW, viewClassName(details, R.id.details_native))

            pressBack()
            awaitResumed<MainActivity>()
        }
    }

    private fun viewClassName(activity: Activity, id: Int): String {
        var name = ""
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            name = activity.findViewById<View>(id).javaClass.name
        }
        return name
    }

    private companion object {
        // String literals, so R8's mapping of the test APK can't rewrite them: the names must survive minification.
        const val SLOT_VIEW = "io.github.thesobiaakbar.adroit.views.AdSlotView"
        const val NATIVE_VIEW = "io.github.thesobiaakbar.adroit.views.AdNativeView"
    }
}
