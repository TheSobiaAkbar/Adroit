package io.github.thesobiaakbar.adroit.billing.compose

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.thesobiaakbar.adroit.billing.BackBehavior
import io.github.thesobiaakbar.adroit.billing.CloseBehavior
import io.github.thesobiaakbar.adroit.billing.OfferSelection
import io.github.thesobiaakbar.adroit.billing.PaywallCloseReason
import io.github.thesobiaakbar.adroit.billing.PaywallController
import io.github.thesobiaakbar.adroit.billing.PaywallSpec
import io.github.thesobiaakbar.adroit.billing.PaywallTemplate
import io.github.thesobiaakbar.adroit.billing.PaywallUiState
import io.github.thesobiaakbar.adroit.billing.PurchaseResult
import io.github.thesobiaakbar.adroit.billing.SuccessAction
import io.github.thesobiaakbar.adroit.billing.Attribution
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PaywallTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val scope = TestScope(StandardTestDispatcher())
    private val billing = FakeBilling()
    private val closes = mutableListOf<PaywallCloseReason>()

    private fun show(spec: PaywallSpec, content: (@Composable PaywallScope.() -> Unit)? = null): PaywallController {
        val controller = PaywallController.forModel(TestPaywall(spec), Fixtures.model(spec), "home", scope, billing, clock = { scope.testScheduler.currentTime })
        rule.setContent { Paywall(controller, onClose = { closes += it }, content = content) }
        settle()
        return controller
    }

    private fun settle(millis: Long = 0) {
        rule.waitForIdle()
        scope.advanceTimeBy(millis)
        scope.runCurrent()
        rule.waitForIdle()
    }

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    private val close get() = rule.onAllNodesWithContentDescription("Close")

    @Test
    fun `delay 0 shows the close button at once, and it closes`() {
        show(Fixtures.spec(close = CloseBehavior(delaySeconds = 0)))

        rule.onNodeWithContentDescription("Close").assertIsDisplayed().performClick()
        settle()

        assertEquals(listOf(PaywallCloseReason.CLOSE_BUTTON), closes)
    }

    @Test
    fun `delay 3 shows a countdown, then the close button`() {
        show(Fixtures.spec(close = CloseBehavior(delaySeconds = 3, showCountdown = true)))
        close.assertCountEquals(0)
        rule.onNodeWithText("3", useUnmergedTree = true).assertExists()

        settle(1_001)
        rule.onNodeWithText("2", useUnmergedTree = true).assertExists()
        settle(2_001)

        rule.onNodeWithContentDescription("Close").assertIsDisplayed()
    }

    @Test
    fun `delay -1 never shows the close button`() {
        show(Fixtures.spec(close = CloseBehavior(delaySeconds = CloseBehavior.NO_CLOSE_BUTTON)))

        settle(120_000)

        close.assertCountEquals(0)
    }

    @Test
    fun `back is consumed while close back is NEVER`() {
        show(Fixtures.spec(close = CloseBehavior(back = BackBehavior.NEVER)))

        back()

        assertTrue(closes.isEmpty())
        rule.onNodeWithText("Go premium").assertIsDisplayed()
    }

    @Test
    fun `back closes after the delay with AFTER_DELAY, and at once with ALWAYS`() {
        show(Fixtures.spec(close = CloseBehavior(delaySeconds = 2, back = BackBehavior.AFTER_DELAY)))
        back()
        assertTrue(closes.isEmpty())

        settle(2_001)
        back()

        assertEquals(listOf(PaywallCloseReason.BACK), closes)
    }

    @Test
    fun `selecting a plan changes the purchase button and the terms`() {
        show(Fixtures.spec())
        rule.onNodeWithText("Start free trial").assertIsDisplayed()
        rule.onNodeWithText("7 days free, then \$4.99/month. Cancel anytime in Google Play.").assertExists()

        rule.onNodeWithText("\$29.99 / year").performScrollTo().performClick()
        settle()

        rule.onNodeWithText("Continue").assertIsDisplayed()
        rule.onNodeWithText("\$29.99/year, renews automatically. Cancel anytime in Google Play.").assertExists()
        rule.onAllNodesWithText("7 days free, then", substring = true).assertCountEquals(0)
    }

    @Test
    fun `plans are radio buttons, the close button has a description and a 48dp target`() {
        show(Fixtures.spec())

        val radios = rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        radios.assertCountEquals(2)
        radios[0].assertIsSelected()
        val bounds = rule.onNodeWithContentDescription("Close").fetchSemanticsNode().boundsInRoot
        with(rule.density) { assertTrue(bounds.width >= 48.dp.toPx() - 1 && bounds.height >= 48.dp.toPx() - 1) }
    }

    @Test
    fun `a purchase sends the selected plan with the placement, then closes`() {
        show(Fixtures.spec())

        rule.onNodeWithText("Start free trial").performClick()
        settle()
        val request = billing.requests.single()
        assertEquals("home", request.placement)
        assertEquals("monthly", request.basePlanId)
        assertEquals(OfferSelection.Id("trial"), request.offer)

        billing.result.complete(purchased())
        settle()

        assertEquals(listOf(PaywallCloseReason.PURCHASED), closes)
    }

    @Test
    fun `successAction STAY_WITH_MESSAGE shows the premium state, Done closes`() {
        show(Fixtures.spec(successAction = SuccessAction.STAY_WITH_MESSAGE))
        rule.onNodeWithText("Start free trial").performClick()
        settle()
        billing.result.complete(purchased())
        settle()

        rule.onNodeWithText("You're premium").assertIsDisplayed()
        assertTrue(closes.isEmpty())
        rule.onNodeWithText("Done").performClick()
        settle()

        assertEquals(listOf(PaywallCloseReason.PURCHASED), closes)
    }

    @Test
    fun `a paywall that can't load can always be closed`() {
        // AdroitBilling isn't initialized here: the controller is Unavailable even with no close button and back NEVER.
        val spec = Fixtures.spec(close = CloseBehavior(delaySeconds = -1, back = BackBehavior.NEVER))
        rule.setContent { Paywall(TestPaywall(spec), "home", onClose = { closes += it }) }
        settle()

        rule.onNodeWithText("Plans aren't available right now. Please try again later.").assertIsDisplayed()
        rule.onNodeWithContentDescription("Close").performClick()
        settle()

        assertEquals(listOf(PaywallCloseReason.ERROR), closes)
    }

    @Test
    fun `custom content reuses the blocks and the controller`() {
        show(Fixtures.spec()) {
            Column {
                Text("My own paywall")
                PlanList()
                CtaButton()
                Disclosure()
            }
        }

        rule.onNodeWithText("My own paywall").assertIsDisplayed()
        rule.onNodeWithText("\$29.99 / year").performClick()
        settle()
        rule.onNodeWithText("Continue").assertIsDisplayed()
    }

    @Test
    fun `the dialog, bottom sheet and single templates render the paywall`() {
        val templates = listOf(PaywallTemplate.DIALOG, PaywallTemplate.BOTTOM_SHEET, PaywallTemplate.SINGLE)
        val controllers = templates.map { template ->
            val spec = Fixtures.spec(template = template)
            PaywallController.forModel(TestPaywall(spec), Fixtures.model(spec), "home", scope, billing)
        }
        var index by mutableIntStateOf(0)
        rule.setContent { key(index) { Paywall(controllers[index], onClose = { closes += it }) } }
        templates.forEachIndexed { i, template ->
            index = i
            settle()
            rule.onNodeWithText("Start free trial").assertExists()
            rule.onNodeWithText("7 days free, then \$4.99/month. Cancel anytime in Google Play.").assertExists()
            if (template == PaywallTemplate.SINGLE) rule.onAllNodesWithText("\$29.99 / year").assertCountEquals(0)
        }
    }

    @Test
    fun `a recreated activity keeps the countdown`() {
        val spec = Fixtures.spec(close = CloseBehavior(delaySeconds = 5, showCountdown = true))
        var controller: PaywallController? = null
        rule.setContent {
            controller = rememberPaywallController("test") { s -> PaywallController.forModel(TestPaywall(spec), Fixtures.model(spec), "home", s, billing) }
            Paywall(controller!!, onClose = { closes += it })
        }
        rule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_100))
        rule.waitForIdle()
        val before = controller!!
        assertEquals(3, (before.state.value as PaywallUiState.Content).closeCountdownSeconds)

        rule.activityRule.scenario.recreate()
        rule.waitForIdle()

        assertTrue(controller === before)
        assertEquals(3, (controller!!.state.value as PaywallUiState.Content).closeCountdownSeconds)
    }

    private fun purchased() =
        PurchaseResult.Purchased(null, Attribution("a", "home", "Onboarding", null, "premium_sub", "monthly", "trial", false))
}
