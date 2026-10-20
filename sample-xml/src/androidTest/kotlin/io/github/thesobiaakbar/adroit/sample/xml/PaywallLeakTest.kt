package io.github.thesobiaakbar.adroit.sample.xml

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.thesobiaakbar.adroit.billing.views.AdroitPaywall
import io.github.thesobiaakbar.adroit.billing.views.AdroitPaywallActivity
import io.github.thesobiaakbar.adroit.sample.xml.billing.SamplePaywalls
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every paywall opened and closed 5 times (B22): the paywall Activity, its views, its controller and the dialog or
 * bottom sheet window of its template must all be released afterwards. No network needed: in demo mode the products
 * are made up, and without it the paywall opens in its unavailable state, which is closed the same way.
 */
@RunWith(AndroidJUnit4::class)
class PaywallLeakTest {
    @get:Rule
    val detectLeaks = DetectLeaksAfterTestSuccess()

    @Test
    fun openingAndClosingEveryPaywallLeavesNoLeaks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SamplePaywalls.entries.forEach { paywall ->
            repeat(5) {
                ActivityScenario.launch<AdroitPaywallActivity>(AdroitPaywall.intent(context, paywall, "leak_test")).use { scenario ->
                    SystemClock.sleep(RENDER_MILLIS)
                    assertEquals(Lifecycle.State.RESUMED, scenario.state)
                    // Finished as the paywall finishes itself. ActivityScenario's own close first moves it behind a
                    // trampoline Activity and waits for onStop, which on Android 17 never came for this one.
                    scenario.onActivity { it.finish() }
                    val deadline = SystemClock.elapsedRealtime() + CLOSE_TIMEOUT_MILLIS
                    while (scenario.state != Lifecycle.State.DESTROYED && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
                    assertEquals("$paywall wasn't destroyed", Lifecycle.State.DESTROYED, scenario.state)
                }
            }
        }
    }

    private companion object {
        /** Long enough for the products to load and the template to draw. */
        const val RENDER_MILLIS = 500L

        const val CLOSE_TIMEOUT_MILLIS = 5_000L
    }
}
