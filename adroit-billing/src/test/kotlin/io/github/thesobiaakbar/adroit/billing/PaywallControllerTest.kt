package io.github.thesobiaakbar.adroit.billing

import android.app.Activity
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.thesobiaakbar.adroit.billing.internal.BillingLog
import io.github.thesobiaakbar.adroit.billing.internal.config.BillingConfig
import io.github.thesobiaakbar.adroit.billing.internal.events.EventBus
import io.github.thesobiaakbar.adroit.billing.internal.paywall.Offers
import io.github.thesobiaakbar.adroit.billing.internal.paywall.PaywallEngine
import io.github.thesobiaakbar.adroit.billing.internal.paywall.PaywallResolver
import io.github.thesobiaakbar.adroit.billing.internal.paywall.ShowCounterStore
import io.github.thesobiaakbar.adroit.billing.testing.FakeBilling
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PaywallControllerTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val log = BillingLog(BillingLogLevel.NONE)
    private val events = mutableListOf<BillingEvent>()
    private val remote = mutableMapOf<String, String>()
    private val catalog = MutableStateFlow<CatalogState>(
        CatalogState.Ready(mapOf(TestProducts.PremiumSub to Offers.premiumSub, TestProducts.Coins to Offers.coins), setOf(TestProducts.Lifetime)),
    )
    private val entitlements = MutableStateFlow(EntitlementSnapshot(emptyList(), SnapshotStatus.Fresh(0), emptySet()))
    private val foreground = MutableStateFlow(true)
    private val billing = FakeBilling()
    private lateinit var config: BillingConfig

    private fun TestScope.controller(close: String? = null, extra: String = "", initialized: Boolean = true): PaywallController {
        remote["Onboarding"] = """{${close?.let { "\"close\": $it," } ?: ""} "exitPaywall": "ExitDiscount" $extra}"""
        val settings = BillingSettings(TestProducts.entries, TestPaywalls.entries, config = BillingConfigSource { remote[it] })
        val bus = EventBus(listOf(BillingEventListener { events += it }), log)
        config = BillingConfig(settings, bus, log)
        val engine = PaywallEngine(
            settings, config, PaywallResolver(settings, application.resources), ShowCounterStore(application), catalog, entitlements,
            bus, backgroundScope,
        )
        val paywalls = Paywalls({ engine.takeIf { initialized } }, billing, bus, { foreground }, { currentTime })
        return paywalls.controller(TestPaywalls.Onboarding, "home", backgroundScope).also { runCurrent() }
    }

    private val PaywallController.content get() = state.value as PaywallUiState.Content

    private inline fun <reified T : BillingEvent> events() = events.filterIsInstance<T>()

    @Test
    fun `delay 0 shows the close button at once`() = runTest {
        val c = controller("""{"delaySeconds": 0}""")
        assertTrue(c.content.closeVisible)
        assertNull(c.content.closeCountdownSeconds)
        c.onShown()
        assertEquals(listOf(0L), events<BillingEvent.PaywallCloseButtonShown>().map { it.afterSeconds })
    }

    @Test
    fun `delay 3 counts down visible seconds from onShown, then shows the button`() = runTest {
        val c = controller("""{"delaySeconds": 3, "showCountdown": true}""")
        advanceTimeBy(10_000)
        assertEquals(3, c.content.closeCountdownSeconds) // not shown yet: no countdown
        c.onShown()
        val seen = mutableListOf<Int?>()
        repeat(3) {
            seen += c.content.closeCountdownSeconds
            assertFalse(c.content.closeVisible)
            advanceTimeBy(1_001)
        }
        assertEquals(listOf(3, 2, 1), seen)
        assertTrue(c.content.closeVisible)
        assertNull(c.content.closeCountdownSeconds)
        assertEquals(listOf(3L), events<BillingEvent.PaywallCloseButtonShown>().map { it.afterSeconds })
    }

    @Test
    fun `without showCountdown there is no countdown number`() = runTest {
        val c = controller("""{"delaySeconds": 2, "showCountdown": false}""")
        c.onShown()
        assertNull(c.content.closeCountdownSeconds)
        advanceTimeBy(2_001)
        assertTrue(c.content.closeVisible)
    }

    @Test
    fun `delay -1 never shows the button`() = runTest {
        val c = controller("""{"delaySeconds": -1, "back": "ALWAYS"}""")
        c.onShown()
        advanceTimeBy(600_000)
        assertFalse(c.content.closeVisible)
        c.onCloseClicked()
        assertTrue(c.state.value is PaywallUiState.Content)
        assertTrue(events<BillingEvent.PaywallCloseButtonShown>().isEmpty())
    }

    @Test
    fun `the countdown pauses in the background`() = runTest {
        val c = controller("""{"delaySeconds": 3, "showCountdown": true}""")
        c.onShown()
        advanceTimeBy(1_001)
        assertEquals(2, c.content.closeCountdownSeconds)
        foreground.value = false
        advanceTimeBy(60_000)
        assertEquals(2, c.content.closeCountdownSeconds)
        foreground.value = true
        advanceTimeBy(2_001)
        assertTrue(c.content.closeVisible)
        c.onCloseClicked()
        assertEquals(3L, events<BillingEvent.PaywallClosed>().single().secondsVisible)
    }

    @Test
    fun `back policies`() = runTest {
        val always = controller("""{"delaySeconds": 3, "back": "ALWAYS"}""")
        assertTrue(always.content.backAllowed)
        assertFalse(always.onBackPressed())
        assertEquals(PaywallCloseReason.BACK, (always.state.value as PaywallUiState.Closed).reason)

        val afterDelay = controller("""{"delaySeconds": 2, "back": "AFTER_DELAY"}""")
        afterDelay.onShown()
        assertTrue(afterDelay.onBackPressed())
        advanceTimeBy(2_001)
        assertTrue(afterDelay.content.backAllowed)
        assertFalse(afterDelay.onBackPressed())

        val afterNoButton = controller("""{"delaySeconds": -1, "back": "AFTER_DELAY"}""")
        afterNoButton.onShown()
        advanceTimeBy(600_000)
        assertTrue(afterNoButton.onBackPressed())

        val never = controller("""{"delaySeconds": 0, "back": "NEVER"}""")
        assertFalse(never.content.backAllowed)
        assertTrue(never.onBackPressed())
        never.onCloseClicked()
        assertEquals(PaywallCloseReason.CLOSE_BUTTON, (never.state.value as PaywallUiState.Closed).reason)
    }

    @Test
    fun `back and close are consumed during a purchase`() = runTest {
        val c = controller("""{"delaySeconds": 0, "back": "ALWAYS"}""")
        billing.holdNextPurchase()
        c.purchase(activity)
        runCurrent()
        assertEquals(Busy.PURCHASING, c.content.busy)
        assertFalse(c.content.backAllowed)
        assertTrue(c.onBackPressed())
        c.onCloseClicked()
        assertTrue(c.state.value is PaywallUiState.Content)
        c.select(0)
        c.purchase(activity)
        runCurrent()
        assertEquals(1, billing.purchaseRequests.size)
    }

    @Test
    fun `a purchase uses the plan's offer and the paywall's attribution`() = runTest {
        val c = controller(extra = """, "campaign": {"id": "autumn"}""")
        val held = billing.holdNextPurchase()
        c.purchase(activity)
        runCurrent()
        assertEquals(
            PurchaseRequest(TestProducts.PremiumSub, "home", "yearly", OfferSelection.Id("trial-3d"), "autumn", "Onboarding"),
            billing.purchaseRequests.single(),
        )
        val attribution = Attribution("a", "home", "Onboarding", "autumn", "premium_sub", "yearly", "trial-3d", false)
        held.complete(PurchaseResult.Purchased(null, attribution))
        runCurrent()
        assertEquals(PaywallUiState.Purchased(PurchaseResult.Purchased(null, attribution)), c.state.value)
        assertFalse(c.onBackPressed())
        assertEquals(PaywallUiState.Closed(PaywallCloseReason.PURCHASED, null), c.state.value)
    }

    @Test
    fun `cancelled, pending, already owned and failed purchases`() = runTest {
        val attribution = Attribution("a", "home", "Onboarding", null, "premium_sub", "yearly", null, false)
        val cases = listOf(
            PurchaseResult.Cancelled(attribution) to null,
            PurchaseResult.Pending(attribution) to UiMessage(R.string.adroit_billing_msg_pending),
            PurchaseResult.AlreadyOwned(attribution) to UiMessage(R.string.adroit_billing_msg_already_owned),
            PurchaseResult.Failed(BillingError.NetworkError(), attribution) to UiMessage(R.string.adroit_billing_msg_network, retry = true),
            PurchaseResult.Failed(BillingError.PaymentDeclined(), attribution) to UiMessage(R.string.adroit_billing_msg_declined, retry = true),
            PurchaseResult.Failed(BillingError.DeveloperError(), attribution) to UiMessage(R.string.adroit_billing_msg_error),
        )
        val c = controller()
        for ((result, message) in cases) {
            val held = billing.holdNextPurchase()
            c.purchase(activity)
            runCurrent()
            held.complete(result)
            runCurrent()
            assertEquals(result.toString(), message, c.content.message)
            assertNull(c.content.busy)
        }
    }

    @Test
    fun `restore and manage report through messages`() = runTest {
        val c = controller()
        c.restore()
        runCurrent()
        assertEquals(UiMessage(R.string.adroit_billing_msg_nothing_to_restore), c.content.message)
        billing.restoreResult = RestoreResult.Failed(BillingError.NetworkError())
        c.restore()
        runCurrent()
        assertEquals(UiMessage(R.string.adroit_billing_msg_network, retry = true), c.content.message)
        billing.restoreResult = RestoreResult.Restored(setOf("premium"), EntitlementSnapshot.EMPTY_UNKNOWN)
        c.restore()
        runCurrent()
        assertEquals(UiMessage(R.string.adroit_billing_msg_restored), c.content.message)
        billing.manageSubscriptionsResult = false
        c.manage(activity)
        assertEquals(UiMessage(R.string.adroit_billing_msg_manage_failed), c.content.message)
    }

    @Test
    fun `state survives a restore mid-countdown, without reporting the showing twice`() = runTest {
        val plans = """, "plans": [{"product": "PremiumSub", "basePlan": "monthly"}, {"product": "PremiumSub", "basePlan": "yearly"}]"""
        val first = controller("""{"delaySeconds": 5, "showCountdown": true}""", plans)
        first.onShown()
        first.select(1)
        advanceTimeBy(2_001)
        val saved = first.saveState()
        first.dispose()

        val second = controller("""{"delaySeconds": 5, "showCountdown": true}""", plans)
        second.restore(saved)
        assertEquals(1, second.content.selectedIndex)
        assertEquals(3, second.content.closeCountdownSeconds)
        second.onShown()
        advanceTimeBy(3_001)
        assertTrue(second.content.closeVisible)
        assertEquals(1, events<BillingEvent.PaywallShown>().size)
        second.onCloseClicked()
        assertEquals(5L, events<BillingEvent.PaywallClosed>().single().secondsVisible)
    }

    @Test
    fun `an unavailable paywall can always be closed, whatever the config`() = runTest {
        val hard = """{"delaySeconds": -1, "back": "NEVER"}"""
        val disabled = controller(hard, """, "enabled": false""")
        assertEquals(PaywallUiState.Unavailable(SkipReason.PAYWALL_DISABLED, null), disabled.state.value)
        assertFalse(disabled.onBackPressed())
        assertEquals(PaywallUiState.Closed(PaywallCloseReason.ERROR, null), disabled.state.value)

        val error = BillingError.NetworkError()
        catalog.value = CatalogState.Failed(error)
        val failed = controller(hard)
        assertEquals(PaywallUiState.Unavailable(null, error), failed.state.value)
        failed.onCloseClicked()
        assertEquals(PaywallUiState.Closed(PaywallCloseReason.ERROR, null), failed.state.value)

        catalog.value = CatalogState.Loading
        val loading = controller(hard)
        assertEquals(PaywallUiState.Loading, loading.state.value)
        assertFalse(loading.onBackPressed())

        assertEquals(PaywallUiState.Unavailable(SkipReason.NOT_INITIALIZED, null), controller(hard, initialized = false).state.value)
    }

    @Test
    fun `events and the exit paywall`() = runTest {
        val plans = """, "plans": [{"product": "PremiumSub", "basePlan": "monthly"}, {"product": "PremiumSub", "basePlan": "yearly"}]"""
        val c = controller("""{"delaySeconds": 0}""", plans)
        c.onShown()
        c.onShown()
        c.select(1)
        assertEquals(1, events<BillingEvent.PaywallShown>().size)
        assertEquals(listOf("trial-7d", "trial-3d"), events<BillingEvent.OfferShown>().map { it.offerId })
        c.onCloseClicked()
        assertEquals(PaywallUiState.Closed(PaywallCloseReason.CLOSE_BUTTON, TestPaywalls.ExitDiscount), c.state.value)
        assertEquals(PaywallCloseReason.CLOSE_BUTTON, events<BillingEvent.PaywallClosed>().single().reason)
    }

    @Test
    fun `the plans are frozen when they load`() = runTest {
        val c = controller()
        remote["Onboarding"] = """{"plans": [{"product": "Coins"}]}"""
        config.reload()
        runCurrent()
        assertEquals("yearly", c.content.selectedPlan.basePlanId)
    }

    @Test
    fun `forModel renders a fixed model with the same close rules, recording nothing`() = runTest {
        val model = (controller().state.value as PaywallUiState.Content).model
        events.clear()
        val spec = PaywallSpec(model.spec.plans, close = CloseBehavior(delaySeconds = 2))
        val c = PaywallController.forModel(TestPaywalls.Onboarding, model.copy(spec = spec), "preview", backgroundScope, billing, flowOf(true)) { currentTime }

        assertFalse(c.content.closeVisible)
        c.onShown()
        advanceTimeBy(2_001)
        assertTrue(c.content.closeVisible)
        c.onCloseClicked()
        assertEquals(PaywallUiState.Closed(PaywallCloseReason.CLOSE_BUTTON, null), c.state.value)
        assertTrue(events.isEmpty())
    }
}
