package io.github.thesobiaakbar.adroit.billing.internal.offers

import io.github.thesobiaakbar.adroit.billing.OfferContext
import io.github.thesobiaakbar.adroit.billing.OfferInfo
import io.github.thesobiaakbar.adroit.billing.OfferPolicy
import io.github.thesobiaakbar.adroit.billing.OfferRule
import io.github.thesobiaakbar.adroit.billing.SubscriptionPlan
import io.github.thesobiaakbar.adroit.billing.TestProducts
import io.github.thesobiaakbar.adroit.billing.internal.paywall.Offers
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultOfferSelectorTest {
    private val context = OfferContext(TestProducts.PremiumSub, "Onboarding")

    private class Case(val name: String, val plan: SubscriptionPlan, val rule: OfferRule, val expected: OfferInfo?)

    private val noTrials = SubscriptionPlan("monthly", listOf(Offers.monthlyBase, Offers.intro))

    private val cases = listOf(
        Case("ids: the first listed ID Play gave", Offers.monthly, OfferRule(ids = listOf("missing", "intro", "trial-7d")), Offers.intro),
        Case("ids: none given", Offers.monthly, OfferRule(ids = listOf("missing"), policy = OfferPolicy.BASE_ONLY), null),
        Case("ids win over tags", Offers.monthly, OfferRule(ids = listOf("intro"), tags = listOf("trial")), Offers.intro),
        Case("tags: all must match", Offers.monthly, OfferRule(tags = listOf("trial")), Offers.trial7),
        Case("tags: one missing", Offers.monthly, OfferRule(tags = listOf("trial", "spring")), null),
        Case("trialDays 7 = P1W", Offers.monthly, OfferRule(trialDays = 7), Offers.trial7),
        Case("trialDays 7, Play has 3 days", Offers.yearly, OfferRule(trialDays = 7), null),
        Case("trialDays 3", Offers.yearly, OfferRule(trialDays = 3), Offers.trial3),
        Case("trialDays never matches a month", Offers.monthly, OfferRule(trialDays = 30), null),
        Case("trial used before: Play left it out", noTrials, OfferRule(trialDays = 7), null),
        Case("tags and trialDays together", Offers.monthly, OfferRule(tags = listOf("trial"), trialDays = 7), Offers.trial7),
        Case("PREFER_FREE_TRIAL", Offers.monthly, OfferRule(policy = OfferPolicy.PREFER_FREE_TRIAL), Offers.trial7),
        Case("PREFER_FREE_TRIAL without a trial", noTrials, OfferRule(policy = OfferPolicy.PREFER_FREE_TRIAL), Offers.monthlyBase),
        Case("PREFER_INTRO_PRICE", Offers.monthly, OfferRule(policy = OfferPolicy.PREFER_INTRO_PRICE), Offers.intro),
        Case("PREFER_INTRO_PRICE without one", Offers.yearly, OfferRule(policy = OfferPolicy.PREFER_INTRO_PRICE), Offers.yearlyBase),
        Case("LOWEST_FIRST_PAYMENT: a tie goes to Play's order", Offers.monthly, OfferRule(policy = OfferPolicy.LOWEST_FIRST_PAYMENT), Offers.trial7),
        Case("LOWEST_FIRST_PAYMENT: intro under base", noTrials, OfferRule(policy = OfferPolicy.LOWEST_FIRST_PAYMENT), Offers.intro),
        Case("BASE_ONLY", Offers.monthly, OfferRule(policy = OfferPolicy.BASE_ONLY), Offers.monthlyBase),
        Case("BASE_ONLY among filtered offers", Offers.monthly, OfferRule(tags = listOf("intro"), policy = OfferPolicy.BASE_ONLY), Offers.intro),
        Case("a policy among filtered offers", Offers.monthly, OfferRule(tags = listOf("trial"), policy = OfferPolicy.LOWEST_FIRST_PAYMENT), Offers.trial7),
    )

    @Test
    fun `every selection path and policy`() {
        for (case in cases) assertEquals(case.name, case.expected, DefaultOfferSelector.select(case.plan, case.rule, context))
    }
}
