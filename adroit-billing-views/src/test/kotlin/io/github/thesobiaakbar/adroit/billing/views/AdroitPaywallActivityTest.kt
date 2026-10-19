package io.github.thesobiaakbar.adroit.billing.views

import android.app.Activity
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.RadioButton
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import io.github.thesobiaakbar.adroit.billing.Attribution
import io.github.thesobiaakbar.adroit.billing.BackBehavior
import io.github.thesobiaakbar.adroit.billing.CloseBehavior
import io.github.thesobiaakbar.adroit.billing.PaywallCloseReason
import io.github.thesobiaakbar.adroit.billing.PaywallController
import io.github.thesobiaakbar.adroit.billing.PaywallSpec
import io.github.thesobiaakbar.adroit.billing.PaywallTemplate
import io.github.thesobiaakbar.adroit.billing.PurchaseResult
import io.github.thesobiaakbar.adroit.billing.ShowDecision
import io.github.thesobiaakbar.adroit.billing.SkipReason
import io.github.thesobiaakbar.adroit.billing.testing.FakeBilling
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class AdroitPaywallActivityTest {
    private val billing = FakeBilling()
    private val defaults = AdroitPaywall.definitions to AdroitPaywall.controllers
    private val defaultShouldShow = AdroitPaywall.shouldShow
    private var created = 0

    private fun use(spec: PaywallSpec): TestPaywall {
        val paywall = TestPaywall(spec)
        AdroitPaywall.definitions = { name -> paywall.takeIf { it.name == name } }
        AdroitPaywall.controllers = { definition, placement, scope ->
            created++
            PaywallController.forModel(definition, Fixtures.model(definition.defaults), placement, scope, billing)
        }
        return paywall
    }

    @After
    fun reset() {
        AdroitPaywall.definitions = defaults.first
        AdroitPaywall.controllers = defaults.second
        AdroitPaywall.shouldShow = defaultShouldShow
    }

    private fun launch(spec: PaywallSpec): ActivityScenario<AdroitPaywallActivity> {
        val paywall = use(spec)
        val intent = AdroitPaywall.intent(ApplicationProvider.getApplicationContext(), paywall, "home")
        return ActivityScenario.launchActivityForResult<AdroitPaywallActivity>(intent).also { idle() }
    }

    /** Advances the main looper's clock in small steps, so delays scheduled on the way run too. */
    private fun idle(millis: Long = 0) {
        val looper = shadowOf(Looper.getMainLooper())
        looper.idle()
        var left = millis
        while (left > 0) {
            val step = minOf(left, 100)
            looper.idleFor(Duration.ofMillis(step))
            left -= step
        }
        looper.idle()
    }

    private fun ActivityScenario<AdroitPaywallActivity>.view(id: Int): View {
        var view: View? = null
        onActivity { view = it.findViewById(id) }
        return view!!
    }

    private fun ActivityScenario<AdroitPaywallActivity>.back() {
        onActivity { it.onBackPressedDispatcher.onBackPressed() }
        idle()
    }

    private fun ActivityScenario<AdroitPaywallActivity>.reason(): PaywallCloseReason =
        AdroitPaywall.contract().parseResult(result.resultCode, result.resultData)

    private fun ActivityScenario<AdroitPaywallActivity>.finishing(): Boolean {
        var finishing = false
        onActivity { finishing = it.isFinishing }
        return finishing
    }

    @Test
    fun `delay 0 shows the close button at once, and it closes with CLOSE_BUTTON`() {
        val scenario = launch(Fixtures.spec(close = CloseBehavior(delaySeconds = 0)))
        val close = scenario.view(R.id.adroit_billing_close)

        assertEquals(View.VISIBLE, close.visibility)
        assertEquals("Close", close.contentDescription)
        assertTrue(close.layoutParams.width >= 48 * close.resources.displayMetrics.density - 1)
        close.performClick()
        idle()

        assertEquals(PaywallCloseReason.CLOSE_BUTTON, scenario.reason())
    }

    @Test
    fun `delay 3 shows a countdown, then the close button`() {
        val scenario = launch(Fixtures.spec(close = CloseBehavior(delaySeconds = 3, showCountdown = true)))
        val close = scenario.view(R.id.adroit_billing_close)
        val count = scenario.view(R.id.adroit_billing_close_count) as TextView

        assertEquals("3", count.text)
        assertFalse(close.isClickable)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, close.importantForAccessibility)
        idle(1_001)
        assertEquals("2", count.text)
        idle(2_001)

        assertEquals(View.VISIBLE, scenario.view(R.id.adroit_billing_close_icon).visibility)
        assertTrue(close.isClickable)
    }

    @Test
    fun `delay -1 never shows the close button`() {
        val scenario = launch(Fixtures.spec(close = CloseBehavior(delaySeconds = CloseBehavior.NO_CLOSE_BUTTON)))

        idle(120_000)

        assertEquals(View.INVISIBLE, scenario.view(R.id.adroit_billing_close).visibility)
    }

    @Test
    fun `back is consumed while close back is NEVER`() {
        val scenario = launch(Fixtures.spec(close = CloseBehavior(back = BackBehavior.NEVER)))

        scenario.back()

        assertFalse(scenario.finishing())
    }

    @Test
    fun `back closes with ALWAYS, as BACK`() {
        val scenario = launch(Fixtures.spec(close = CloseBehavior(back = BackBehavior.ALWAYS)))

        scenario.back()

        assertEquals(PaywallCloseReason.BACK, scenario.reason())
    }

    @Test
    fun `a purchase sends the selected plan with the placement and returns PURCHASED`() {
        val scenario = launch(Fixtures.spec())
        val plans = scenario.view(R.id.adroit_billing_plans) as android.view.ViewGroup
        plans.getChildAt(1).performClick()
        idle()
        val cta = scenario.view(R.id.adroit_billing_cta) as Button
        assertEquals("Continue", cta.text)

        billing.nextPurchaseResult = PurchaseResult.Purchased(null, Attribution("a", "home", "Onboarding", null, "premium_sub", "yearly", null, false))
        cta.performClick()
        idle()

        assertEquals("home", billing.purchaseRequests.single().placement)
        assertEquals("yearly", billing.purchaseRequests.single().basePlanId)

        assertEquals(PaywallCloseReason.PURCHASED, scenario.reason())
    }

    @Test
    fun `the selected plan changes the purchase button and the terms, plans read as radio buttons`() {
        val scenario = launch(Fixtures.spec())
        val cta = scenario.view(R.id.adroit_billing_cta) as Button
        val disclosure = scenario.view(R.id.adroit_billing_disclosure) as TextView
        assertEquals("Start free trial", cta.text)
        assertEquals("7 days free, then \$4.99/month. Cancel anytime in Google Play.", disclosure.text)

        val plans = scenario.view(R.id.adroit_billing_plans) as android.view.ViewGroup
        plans.getChildAt(1).performClick()
        idle()

        assertEquals("\$29.99/year, renews automatically. Cancel anytime in Google Play.", disclosure.text)
        val info = AccessibilityNodeInfo()
        plans.getChildAt(1).onInitializeAccessibilityNodeInfo(info)
        assertEquals(RadioButton::class.java.name, info.className)
        assertTrue(info.isCheckable)
        @Suppress("DEPRECATION")
        assertTrue(info.isChecked)
        assertTrue(plans.getChildAt(1).isSelected)
        assertFalse(plans.getChildAt(0).isSelected)
    }

    @Test
    fun `rotation keeps the controller, the selection and the countdown`() {
        val scenario = launch(Fixtures.spec(close = CloseBehavior(delaySeconds = 5, showCountdown = true)))
        (scenario.view(R.id.adroit_billing_plans) as android.view.ViewGroup).getChildAt(1).performClick()
        idle(2_001)

        scenario.recreate()
        idle()

        assertEquals(1, created)
        assertTrue((scenario.view(R.id.adroit_billing_plans) as android.view.ViewGroup).getChildAt(1).isSelected)
        assertEquals("3", (scenario.view(R.id.adroit_billing_close_count) as TextView).text)
    }

    @Test
    fun `dialog and bottom sheet templates show a card over a scrim that closes once the X shows`() {
        for (template in listOf(PaywallTemplate.DIALOG, PaywallTemplate.BOTTOM_SHEET)) {
            val scenario = launch(Fixtures.spec(template = template, close = CloseBehavior(delaySeconds = 0)))
            var container: View? = null
            scenario.onActivity { container = it.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) }
            assertEquals(View.VISIBLE, scenario.view(R.id.adroit_billing_disclosure).visibility)

            container!!.performClick()
            idle()

            assertEquals(template.name, PaywallCloseReason.CLOSE_BUTTON, scenario.reason())
        }
    }

    @Test
    fun `an unknown paywall finishes with ERROR`() {
        use(Fixtures.spec())
        val intent = Intent(ApplicationProvider.getApplicationContext(), AdroitPaywallActivity::class.java)
            .putExtra(AdroitPaywallActivity.EXTRA_PAYWALL, "Nope")
        val scenario = ActivityScenario.launchActivityForResult<AdroitPaywallActivity>(intent)
        idle()

        assertEquals(PaywallCloseReason.ERROR, scenario.reason())
    }

    @Test
    fun `show honours shouldShow, and force skips it`() {
        val paywall = use(Fixtures.spec())
        AdroitPaywall.shouldShow = { _, _ -> ShowDecision.Skip(SkipReason.COUNTER_NOT_REACHED) }
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

        assertEquals(ShowDecision.Skip(SkipReason.COUNTER_NOT_REACHED), AdroitPaywall.show(host, paywall, "home"))
        assertNull(shadowOf(host).nextStartedActivity)

        assertEquals(ShowDecision.Show, AdroitPaywall.show(host, paywall, "home", force = true))
        val started = shadowOf(host).nextStartedActivity
        assertEquals(AdroitPaywallActivity::class.java.name, started.component?.className)
        assertEquals("Onboarding", started.getStringExtra(AdroitPaywallActivity.EXTRA_PAYWALL))
    }

    @Test
    fun `the contract maps results, and no result is BACK`() {
        val contract = AdroitPaywall.contract()
        assertEquals(PaywallCloseReason.BACK, contract.parseResult(Activity.RESULT_CANCELED, null))
        assertEquals(
            PaywallCloseReason.PURCHASED,
            contract.parseResult(Activity.RESULT_OK, Intent().putExtra(AdroitPaywallActivity.EXTRA_REASON, "PURCHASED")),
        )
    }

    @Test
    fun `no AppCompat, Material or Compose class at runtime`() {
        // androidx.fragment does arrive, transitively from Play Billing (play-services-basement); this module never
        // references it, and checkModuleDependencies forbids adding it directly.
        listOf(
            "androidx.appcompat.app.AppCompatActivity",
            "com.google.android.material.bottomsheet.BottomSheetDialog",
            "androidx.compose.runtime.Composer",
        ).forEach { name ->
            assertTrue(name, runCatching { Class.forName(name) }.isFailure)
        }
    }
}
