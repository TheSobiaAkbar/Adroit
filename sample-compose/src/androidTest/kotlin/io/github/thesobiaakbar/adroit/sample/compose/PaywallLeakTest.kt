package io.github.thesobiaakbar.adroit.sample.compose

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.thesobiaakbar.adroit.billing.AdroitBilling
import io.github.thesobiaakbar.adroit.billing.CatalogState
import io.github.thesobiaakbar.adroit.billing.compose.Paywall
import io.github.thesobiaakbar.adroit.sample.compose.billing.SamplePaywalls
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Every paywall opened and closed 5 times (B22), each opening a new one as an app's state would: the controllers, the
 * dialog and bottom sheet windows and their compositions must all be released afterwards. No network needed: in demo
 * mode the products are made up, and without it the paywall opens in its unavailable state.
 */
@RunWith(AndroidJUnit4::class)
class PaywallLeakTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(notificationsGranted())
        .around(DetectLeaksAfterTestSuccess())
        .around(AppOpenDisabledRule())
        .around(compose)

    @Test
    fun openingAndClosingEveryPaywallLeavesNoLeaks() {
        var open by mutableStateOf<SamplePaywalls?>(null)
        var opening by mutableIntStateOf(0)
        compose.setContent {
            open?.let { paywall -> key(opening) { Paywall(paywall, "leak_test", onClose = { open = null }) } }
        }
        // The products load in real time: until then every paywall would only show its loading state.
        compose.waitUntil(CATALOG_TIMEOUT_MILLIS) { AdroitBilling.catalog.value is CatalogState.Ready }
        SamplePaywalls.entries.forEach { paywall ->
            repeat(5) {
                opening++
                open = paywall
                compose.waitForIdle()
                SystemClock.sleep(RENDER_MILLIS)
                compose.waitForIdle()
                open = null
                compose.waitForIdle()
            }
        }
    }

    private companion object {
        /** Long enough for the products to load and the template to draw. */
        const val RENDER_MILLIS = 500L

        const val CATALOG_TIMEOUT_MILLIS = 10_000L
    }
}
