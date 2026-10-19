package io.github.thesobiaakbar.adroit.billing.views

import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import io.github.thesobiaakbar.adroit.billing.BackBehavior
import io.github.thesobiaakbar.adroit.billing.CloseBehavior
import io.github.thesobiaakbar.adroit.billing.PaywallCloseReason
import io.github.thesobiaakbar.adroit.billing.PaywallController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PaywallViewTest {
    private val billing = FakeBilling()
    private val defaults = AdroitPaywall.definitions to AdroitPaywall.controllers

    @After
    fun reset() {
        AdroitPaywall.definitions = defaults.first
        AdroitPaywall.controllers = defaults.second
    }

    @Test
    fun `a PaywallView from XML attributes renders, blocks back per the policy, and reports its close`() {
        val paywall = TestPaywall(Fixtures.spec(close = CloseBehavior(delaySeconds = 0, back = BackBehavior.NEVER)))
        AdroitPaywall.definitions = { name -> paywall.takeIf { it.name == name } }
        AdroitPaywall.controllers = { definition, placement, scope ->
            PaywallController.forModel(definition, Fixtures.model(definition.defaults), placement, scope, billing)
        }
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContentView(io.github.thesobiaakbar.adroit.billing.views.test.R.layout.adroit_billing_test_paywall_view)
        val view = activity.findViewById<PaywallView>(io.github.thesobiaakbar.adroit.billing.views.test.R.id.test_paywall)
        val closes = mutableListOf<PaywallCloseReason>()
        view.onClose = { closes += it }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Go premium", view.findViewById<TextView>(R.id.adroit_billing_title).text)
        activity.onBackPressedDispatcher.onBackPressed()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(activity.isFinishing)

        view.findViewById<View>(R.id.adroit_billing_close).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(PaywallCloseReason.CLOSE_BUTTON), closes)
        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue(activity.isFinishing)
    }
}
