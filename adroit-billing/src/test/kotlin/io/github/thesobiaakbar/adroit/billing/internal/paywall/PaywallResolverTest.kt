package io.github.thesobiaakbar.adroit.billing.internal.paywall

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.thesobiaakbar.adroit.billing.BillingError
import io.github.thesobiaakbar.adroit.billing.BillingSettings
import io.github.thesobiaakbar.adroit.billing.CatalogState
import io.github.thesobiaakbar.adroit.billing.OfferEligibility
import io.github.thesobiaakbar.adroit.billing.OfferFallback
import io.github.thesobiaakbar.adroit.billing.OfferRule
import io.github.thesobiaakbar.adroit.billing.OfferSelector
import io.github.thesobiaakbar.adroit.billing.PaywallModelState
import io.github.thesobiaakbar.adroit.billing.PaywallPlan
import io.github.thesobiaakbar.adroit.billing.PaywallSpec
import io.github.thesobiaakbar.adroit.billing.PaywallTexts
import io.github.thesobiaakbar.adroit.billing.PlanSpec
import io.github.thesobiaakbar.adroit.billing.SkipReason
import io.github.thesobiaakbar.adroit.billing.TestPaywalls
import io.github.thesobiaakbar.adroit.billing.TestProducts
import io.github.thesobiaakbar.adroit.billing.internal.config.ResolvedPaywall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PaywallResolverTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val catalog = CatalogState.Ready(
        mapOf(TestProducts.PremiumSub to Offers.premiumSub, TestProducts.Lifetime to Offers.lifetime),
        setOf(TestProducts.Coins),
    )
    private val texts = PaywallTexts(
        title = "Save {savings}", subtitle = "{trial} free", features = listOf("Then {renewalPrice} / {renewalPeriod}", "{trial} on us"),
        cta = "Continue for {price}", ctaTrial = "Start {trial} free trial", footer = "No HTML here",
    )
    private val monthlyTrial = PlanSpec(TestProducts.PremiumSub, "monthly", OfferRule(trialDays = 7))
    private val yearlyTrial = PlanSpec(TestProducts.PremiumSub, "yearly", OfferRule(trialDays = 7), savingsVs = "monthly")
    private val lifetime = PlanSpec(TestProducts.Lifetime)

    private fun resolver(settings: BillingSettings = BillingSettings(TestProducts.entries)) = PaywallResolver(settings, application.resources)

    private fun resolve(
        plans: List<PlanSpec>,
        fallback: OfferFallback = OfferFallback.BASE_PLAN,
        selected: Int = 0,
        settings: BillingSettings = BillingSettings(TestProducts.entries),
        state: CatalogState = catalog,
    ) = resolver(settings).resolve(
        ResolvedPaywall("Onboarding", PaywallSpec(plans, selected = selected, onOfferUnavailable = fallback, texts = texts)),
        state,
    )

    private fun PaywallModelState.plans(): List<PaywallPlan> = (this as PaywallModelState.Ready).model.plans

    @Test
    fun `an available offer is chosen and its texts are filled from it`() {
        val plan = resolve(listOf(monthlyTrial)).state.plans().single()
        assertEquals(Offers.trial7, plan.offer)
        assertEquals("monthly", plan.basePlanId)
        assertFalse(plan.offerUnavailable)
        assertEquals(
            PaywallTexts(
                title = null, // no savingsVs
                subtitle = "1 week free",
                features = listOf("Then USD 4.99 / month", "1 week on us"),
                cta = "Start 1 week free trial",
                footer = "No HTML here",
            ),
            plan.texts,
        )
    }

    @Test
    fun `BASE_PLAN shows the base price with normal texts and reports the miss`() {
        val result = resolve(listOf(yearlyTrial))
        val plan = result.state.plans().single()
        assertEquals(Offers.yearlyBase, plan.offer)
        assertTrue(plan.offerUnavailable)
        assertNull(plan.offer!!.freeTrial)
        assertEquals("Continue for USD 39.99", plan.texts.cta)
        assertNull(plan.texts.subtitle)
        assertEquals(listOf("Then USD 39.99 / year"), plan.texts.features)
        assertEquals("Save 33%", plan.texts.title)
        assertEquals(33, plan.savingsPercent)
        assertEquals(
            listOf(PaywallResolver.Unavailable("premium_sub", "yearly", "trialDays:7 policy:prefer_free_trial", null)),
            result.unavailable,
        )
        assertTrue(result.state.plans().none { it.texts.toString().contains("7") })
    }

    @Test
    fun `HIDE_PLAN drops the plan and keeps the selection on the same plan`() {
        val result = resolve(listOf(yearlyTrial, monthlyTrial, lifetime), OfferFallback.HIDE_PLAN, selected = 2)
        val model = (result.state as PaywallModelState.Ready).model
        assertEquals(listOf(monthlyTrial, lifetime), model.plans.map { it.spec })
        assertEquals(1, model.selected)
        assertEquals(1, result.unavailable.size)
        // The selected plan was dropped: the first plan is selected.
        assertEquals(0, (resolve(listOf(yearlyTrial, lifetime), OfferFallback.HIDE_PLAN, selected = 0).state as PaywallModelState.Ready).model.selected)
    }

    @Test
    fun `HIDE_PAYWALL hides the whole paywall`() {
        val result = resolve(listOf(monthlyTrial, yearlyTrial), OfferFallback.HIDE_PAYWALL)
        assertEquals(PaywallModelState.Unavailable(SkipReason.OFFER_UNAVAILABLE), result.state)
        assertEquals(1, result.unavailable.size)
        assertEquals(PaywallModelState.Ready::class, resolve(listOf(monthlyTrial), OfferFallback.HIDE_PAYWALL).state::class)
    }

    @Test
    fun `developer-determined offers need the eligibility hook`() {
        val winback = PlanSpec(TestProducts.PremiumSub, "monthly", OfferRule(ids = listOf("winback")))
        assertTrue(resolve(listOf(winback)).state.plans().single().offerUnavailable)
        val yes = BillingSettings(TestProducts.entries, offerEligibility = OfferEligibility { _, _ -> true })
        assertEquals(Offers.dev, resolve(listOf(winback), settings = yes).state.plans().single().offer)
        val no = BillingSettings(TestProducts.entries, offerEligibility = OfferEligibility { _, _ -> false })
        assertTrue(resolve(listOf(winback), settings = no).state.plans().single().offerUnavailable)
        // The hook gets the offer and its context; other tags never reach it.
        val asked = mutableListOf<String?>()
        val spy = BillingSettings(TestProducts.entries, offerEligibility = OfferEligibility { offer, context -> asked += "${offer.offerId}@${context.paywallId}"; false })
        resolve(listOf(PlanSpec(TestProducts.PremiumSub, "monthly", OfferRule(tags = listOf("trial")))), settings = spy)
        assertEquals(listOf("winback@Onboarding"), asked)
        // Custom tags.
        val custom = BillingSettings(TestProducts.entries, developerDeterminedTags = setOf("intro"))
        assertTrue(resolve(listOf(PlanSpec(TestProducts.PremiumSub, "monthly", OfferRule(ids = listOf("intro")))), settings = custom).state.plans().single().offerUnavailable)
    }

    @Test
    fun `an app selector can only pick among eligible offers`() {
        val foreign = BillingSettings(TestProducts.entries, offerSelector = OfferSelector { _, _, _ -> Offers.trial3 })
        assertTrue(resolve(listOf(monthlyTrial), settings = foreign).state.plans().single().offerUnavailable)
        val vetoed = BillingSettings(TestProducts.entries, offerSelector = OfferSelector { plan, _, _ -> plan.offers.last() })
        assertEquals(Offers.intro, resolve(listOf(monthlyTrial), settings = vetoed).state.plans().single().offer)
    }

    @Test
    fun `one-time products fill only the price`() {
        val plan = resolve(listOf(lifetime)).state.plans().single()
        assertEquals(Offers.lifetime.oneTimeOptions.single(), plan.option)
        assertNull(plan.offer)
        assertEquals(PaywallTexts(cta = "Continue for $59.99", footer = "No HTML here"), plan.texts)
        assertTrue(resolve(listOf(PlanSpec(TestProducts.Lifetime, offer = OfferRule(ids = listOf("sale"))))).state.plans().single().offerUnavailable)
    }

    @Test
    fun `missing products, a missing base plan, loading and failure`() {
        assertEquals(PaywallModelState.Unavailable(SkipReason.NO_PLANS), resolve(listOf(PlanSpec(TestProducts.Coins))).state)
        assertEquals(PaywallModelState.Unavailable(SkipReason.NO_PLANS), resolve(listOf(PlanSpec(TestProducts.PremiumSub, "weekly"))).state)
        assertEquals(listOf(lifetime), resolve(listOf(PlanSpec(TestProducts.Coins), lifetime)).state.plans().map { it.spec })
        assertEquals(PaywallModelState.Loading, resolve(listOf(lifetime), state = CatalogState.Loading).state)
        val error = BillingError.NetworkError()
        assertEquals(PaywallModelState.Failed(error), resolve(listOf(lifetime), state = CatalogState.Failed(error)).state)
    }

    @Test
    fun `the same product twice with different base plans`() {
        val plans = resolve(listOf(PlanSpec(TestProducts.PremiumSub, "monthly"), PlanSpec(TestProducts.PremiumSub, "yearly"))).state.plans()
        assertEquals(listOf(Offers.trial7, Offers.trial3), plans.map { it.offer })
        assertEquals(TestPaywalls.Onboarding.name, "Onboarding")
    }

    @Test
    fun `no plan in any model advertises a trial or intro its offer doesn't have`() {
        val rules = listOf(OfferRule(), OfferRule(trialDays = 7), OfferRule(trialDays = 3), OfferRule(tags = listOf("intro")), OfferRule(ids = listOf("winback")))
        for (basePlan in listOf("monthly", "yearly")) for (rule in rules) {
            val plan = resolve(listOf(PlanSpec(TestProducts.PremiumSub, basePlan, rule))).state.plans().single()
            val offer = plan.offer!!
            assertTrue(offer in Offers.premiumSub.plans.first { it.basePlanId == basePlan }.offers)
            assertFalse("dev offer without the hook", "dev" in offer.offerTags)
            val shown = listOfNotNull(plan.texts.subtitle, plan.texts.cta) + plan.texts.features
            if (offer.freeTrial == null) assertTrue("$basePlan $rule: $shown", shown.none { "free" in it || "week" in it || "day" in it })
        }
    }
}
