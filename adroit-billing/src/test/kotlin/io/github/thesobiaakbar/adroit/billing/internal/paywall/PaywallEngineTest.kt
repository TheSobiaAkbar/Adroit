package io.github.thesobiaakbar.adroit.billing.internal.paywall

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.thesobiaakbar.adroit.billing.BillingConfigSource
import io.github.thesobiaakbar.adroit.billing.BillingEvent
import io.github.thesobiaakbar.adroit.billing.BillingEventListener
import io.github.thesobiaakbar.adroit.billing.BillingLogLevel
import io.github.thesobiaakbar.adroit.billing.BillingSettings
import io.github.thesobiaakbar.adroit.billing.CatalogState
import io.github.thesobiaakbar.adroit.billing.EntitlementGrant
import io.github.thesobiaakbar.adroit.billing.EntitlementSnapshot
import io.github.thesobiaakbar.adroit.billing.GrantSource
import io.github.thesobiaakbar.adroit.billing.GrantState
import io.github.thesobiaakbar.adroit.billing.PaywallCloseReason
import io.github.thesobiaakbar.adroit.billing.PaywallModelState
import io.github.thesobiaakbar.adroit.billing.ProductType
import io.github.thesobiaakbar.adroit.billing.ShowDecision
import io.github.thesobiaakbar.adroit.billing.SkipReason
import io.github.thesobiaakbar.adroit.billing.SnapshotStatus
import io.github.thesobiaakbar.adroit.billing.TestPaywalls
import io.github.thesobiaakbar.adroit.billing.TestProducts
import io.github.thesobiaakbar.adroit.billing.internal.BillingLog
import io.github.thesobiaakbar.adroit.billing.internal.config.BillingConfig
import io.github.thesobiaakbar.adroit.billing.internal.events.EventBus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PaywallEngineTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val log = BillingLog(BillingLogLevel.NONE)
    private val events = mutableListOf<BillingEvent>()
    private val remote = mutableMapOf<String, String>()
    private val catalog = MutableStateFlow<CatalogState>(
        CatalogState.Ready(mapOf(TestProducts.PremiumSub to Offers.premiumSub, TestProducts.Coins to Offers.coins), setOf(TestProducts.Lifetime)),
    )
    private val entitlements = MutableStateFlow(EntitlementSnapshot(emptyList(), SnapshotStatus.Fresh(0), emptySet()))
    private val store = ShowCounterStore(application)

    /** 2026-10-01T00:00:00Z. */
    private var now = 1_790_812_800_000
    private var elapsed = 1_000_000L
    private lateinit var config: BillingConfig

    private fun TestScope.engine(): PaywallEngine {
        val settings = BillingSettings(TestProducts.entries, TestPaywalls.entries, config = BillingConfigSource { remote[it] })
        val bus = EventBus(listOf(BillingEventListener { events += it }), log)
        config = BillingConfig(settings, bus, log)
        return PaywallEngine(
            settings, config, PaywallResolver(settings, application.resources), store, catalog, entitlements, bus, backgroundScope,
            now = { now }, elapsed = { elapsed }, zone = TimeZone.getTimeZone("UTC"),
        )
    }

    private fun PaywallEngine.decide() = shouldShow(TestPaywalls.Onboarding, "home")

    private fun skip(reason: SkipReason) = ShowDecision.Skip(reason)

    private fun suppressed() = events.filterIsInstance<BillingEvent.PaywallSuppressed>().map { it.reason }

    private fun show(json: String) {
        remote["Onboarding"] = """{"show": $json}"""
    }

    @Test
    fun `with no rules it shows`() = runTest {
        assertEquals(ShowDecision.Show, engine().decide())
        assertEquals(emptyList<String>(), suppressed())
    }

    @Test
    fun `global and paywall enabled flags`() = runTest {
        remote["AdroitBilling"] = """{"enabled": false}"""
        assertEquals(skip(SkipReason.GLOBAL_DISABLED), engine().decide())
        remote.clear()
        remote["Onboarding"] = """{"enabled": false}"""
        assertEquals(skip(SkipReason.PAYWALL_DISABLED), engine().decide())
        assertEquals(listOf("global_disabled", "paywall_disabled"), suppressed())
        assertEquals("home", events.filterIsInstance<BillingEvent.PaywallSuppressed>().last().placement)
    }

    @Test
    fun `the campaign window by the device clock`() = runTest {
        remote["Onboarding"] = """{"campaign": {"id": "autumn", "start": "2026-10-01T00:00:00Z", "end": "2026-10-31T23:59:59Z"}}"""
        val engine = engine()
        now -= 1
        assertEquals(skip(SkipReason.OUTSIDE_CAMPAIGN), engine.decide())
        now += 1
        assertEquals(ShowDecision.Show, engine.decide())
        now += 31L * 86_400_000
        assertEquals(skip(SkipReason.OUTSIDE_CAMPAIGN), engine.decide())
        assertEquals("autumn", events.filterIsInstance<BillingEvent.PaywallSuppressed>().last().campaignId)
    }

    @Test
    fun `entitled users are skipped, by the products' entitlements or hideIfEntitled`() = runTest {
        val engine = engine()
        entitlements.value = EntitlementSnapshot(
            listOf(EntitlementGrant("premium_sub", ProductType.SUBSCRIPTION, setOf("premium", "ad_free"), GrantState.ACTIVE, GrantSource.CLIENT, 0)),
            SnapshotStatus.Fresh(0), emptySet(),
        )
        assertEquals(skip(SkipReason.ENTITLED), engine.decide())
        show("""{"hideIfEntitled": ["gold"]}""")
        config.reload()
        assertEquals(ShowDecision.Show, engine.decide())
    }

    @Test
    fun `firstAfterLaunches counts app launches`() = runTest {
        show("""{"firstAfterLaunches": 2}""")
        val engine = engine()
        store.countLaunch()
        assertEquals(skip(SkipReason.LAUNCHES_NOT_REACHED), engine.decide())
        store.countLaunch()
        assertEquals(ShowDecision.Show, engine.decide())
    }

    @Test
    fun `maxPerSession and maxPerDay, with a day rollover`() = runTest {
        show("""{"maxPerSession": 2, "maxPerDay": 3}""")
        var engine = engine()
        repeat(2) {
            assertEquals(ShowDecision.Show, engine.decide())
            engine.recordShown(TestPaywalls.Onboarding)
        }
        assertEquals(skip(SkipReason.SESSION_CAP), engine.decide())
        engine = engine() // a new session, same day
        assertEquals(ShowDecision.Show, engine.decide())
        engine.recordShown(TestPaywalls.Onboarding)
        assertEquals(skip(SkipReason.DAILY_CAP), engine.decide())
        now += 86_400_000 - 1 // still 23:59:59.999 the same day
        assertEquals(skip(SkipReason.DAILY_CAP), engine().decide())
        now += 1
        assertEquals(ShowDecision.Show, engine().decide())
    }

    @Test
    fun `the counter shows on the 3rd, 6th and 9th call, like the ads`() = runTest {
        show("""{"counter": 3, "timerSeconds": 3600}""")
        val engine = engine()
        val shown = (1..9).filter { call ->
            (engine.decide() == ShowDecision.Show).also { if (it) engine.recordShown(TestPaywalls.Onboarding) }
        }
        assertEquals(listOf(3, 6, 9), shown) // the timer is ignored while a counter is set
        assertEquals(6, suppressed().count { it == "counter_not_reached" })
    }

    @Test
    fun `a due counter stays due until the paywall really shows`() = runTest {
        show("""{"counter": 2}""")
        val engine = engine()
        engine.decide()
        assertEquals(ShowDecision.Show, engine.decide())
        assertEquals(ShowDecision.Show, engine.decide()) // not shown: still due
        engine.recordShown(TestPaywalls.Onboarding)
        assertEquals(skip(SkipReason.COUNTER_NOT_REACHED), engine.decide())
    }

    @Test
    fun `the timer uses elapsed time in a process and the wall clock across processes`() = runTest {
        show("""{"timerSeconds": 30}""")
        val engine = engine()
        engine.recordShown(TestPaywalls.Onboarding)
        now -= 3_600_000 // the user moves the clock back: no effect in this process
        elapsed += 29_999
        assertEquals(skip(SkipReason.TIMER), engine.decide())
        elapsed += 1
        assertEquals(ShowDecision.Show, engine.decide())

        now += 3_600_000
        engine.recordShown(TestPaywalls.Onboarding)
        val next = engine() // a new process: the saved wall-clock time
        now += 10_000
        assertEquals(skip(SkipReason.TIMER), next.decide())
        now -= 20_000 // a last showing in the future doesn't block
        assertEquals(ShowDecision.Show, next.decide())
    }

    @Test
    fun `rules apply in order`() = runTest {
        remote["AdroitBilling"] = """{"enabled": false}"""
        remote["Onboarding"] = """{"enabled": false, "show": {"counter": 5}}"""
        assertEquals(skip(SkipReason.GLOBAL_DISABLED), engine().decide())
        remote.remove("AdroitBilling")
        assertEquals(skip(SkipReason.PAYWALL_DISABLED), engine().decide())
        show("""{"maxPerSession": 1, "counter": 2, "timerSeconds": 60}""")
        val engine = engine()
        engine.decide()
        assertEquals(ShowDecision.Show, engine.decide())
        engine.recordShown(TestPaywalls.Onboarding)
        assertEquals(skip(SkipReason.SESSION_CAP), engine.decide())
    }

    @Test
    fun `no purchasable plan or a hidden paywall skips once the catalog is ready, loading shows`() = runTest {
        val engine = engine()
        catalog.value = CatalogState.Loading
        assertEquals(ShowDecision.Show, engine.decide())
        catalog.value = CatalogState.Ready(emptyMap(), TestProducts.entries.toSet())
        assertEquals(skip(SkipReason.NO_PLANS), engine.decide())

        catalog.value = CatalogState.Ready(mapOf(TestProducts.PremiumSub to Offers.premiumSub), emptySet())
        remote["Onboarding"] = """{"plans": [{"product": "PremiumSub", "basePlan": "yearly", "offer": {"trialDays": 7}}], "onOfferUnavailable": "HIDE_PAYWALL"}"""
        config.reload()
        assertEquals(skip(SkipReason.OFFER_UNAVAILABLE), engine.decide())
        val unavailable = events.filterIsInstance<BillingEvent.OfferUnavailable>().single()
        assertEquals(BillingEvent.OfferUnavailable("premium_sub", "yearly", "trialDays:7 policy:prefer_free_trial", null, "Onboarding", "home", now), unavailable)
    }

    @Test
    fun `the exit paywall shows once per session after a close`() = runTest {
        val engine = engine()
        assertNull(engine.recordClosed(TestPaywalls.Onboarding, "home", PaywallCloseReason.PURCHASED))
        assertEquals(TestPaywalls.ExitDiscount, engine.recordClosed(TestPaywalls.Onboarding, "home", PaywallCloseReason.CLOSE_BUTTON))
        engine.recordShown(TestPaywalls.ExitDiscount)
        assertNull(engine.recordClosed(TestPaywalls.Onboarding, "home", PaywallCloseReason.BACK))
        assertNull(engine.recordClosed(TestPaywalls.Credits, "home", PaywallCloseReason.BACK)) // none set

        remote["ExitDiscount"] = """{"enabled": false}"""
        val next = engine()
        assertNull(next.recordClosed(TestPaywalls.Onboarding, "home", PaywallCloseReason.BACK))
    }

    @Test
    fun `the model follows the catalog and the config`() = runTest {
        val engine = engine()
        val model = engine.model(TestPaywalls.Onboarding)
        assertEquals("yearly", (model.value as PaywallModelState.Ready).model.plans.single().basePlanId)
        catalog.value = CatalogState.Loading
        runCurrent()
        assertEquals(PaywallModelState.Loading, model.value)
        catalog.value = CatalogState.Ready(mapOf(TestProducts.PremiumSub to Offers.premiumSub), emptySet())
        remote["Onboarding"] = """{"plans": [{"product": "PremiumSub", "basePlan": "monthly"}]}"""
        config.reload()
        runCurrent()
        assertEquals("monthly", (model.value as PaywallModelState.Ready).model.plans.single().basePlanId)
        assertEquals(model, engine.model(TestPaywalls.Onboarding))
    }

    @Test
    fun `an unregistered paywall is skipped`() = runTest {
        val other = object : io.github.thesobiaakbar.adroit.billing.PaywallDefinition {
            override val name = "Other"
            override val defaults = TestPaywalls.Credits.defaults
        }
        assertEquals(skip(SkipReason.NOT_REGISTERED), engine().shouldShow(other, "home"))
    }
}
