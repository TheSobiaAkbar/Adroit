package io.github.thesobiaakbar.adroit.billing.internal.paywall

import io.github.thesobiaakbar.adroit.billing.OfferInfo
import io.github.thesobiaakbar.adroit.billing.OneTimeOption
import io.github.thesobiaakbar.adroit.billing.Phases.base
import io.github.thesobiaakbar.adroit.billing.Phases.free
import io.github.thesobiaakbar.adroit.billing.Phases.phase
import io.github.thesobiaakbar.adroit.billing.Price
import io.github.thesobiaakbar.adroit.billing.ProductInfo
import io.github.thesobiaakbar.adroit.billing.ProductType
import io.github.thesobiaakbar.adroit.billing.Recurrence
import io.github.thesobiaakbar.adroit.billing.SubscriptionPlan
import io.github.thesobiaakbar.adroit.billing.TestProducts

/** Play-like offers for the paywall tests, in Play's order. */
object Offers {
    private fun offer(plan: String, id: String?, tags: List<String>, vararg phases: io.github.thesobiaakbar.adroit.billing.PricingPhase) =
        OfferInfo(plan, id, tags, phases.toList())

    val monthlyBase = offer("monthly", null, emptyList(), base("P1M", 4_990_000))
    val trial7 = offer("monthly", "trial-7d", listOf("trial"), free("P1W"), base("P1M", 4_990_000))
    val intro = offer("monthly", "intro", listOf("intro"), phase("P1M", 990_000, Recurrence.FINITE, cycles = 3), base("P1M", 4_990_000))
    val dev = offer("monthly", "winback", listOf("dev", "trial"), free("P1M"), base("P1M", 4_990_000))
    val monthly = SubscriptionPlan("monthly", listOf(monthlyBase, trial7, intro, dev))

    val yearlyBase = offer("yearly", null, emptyList(), base("P1Y", 39_990_000))
    val trial3 = offer("yearly", "trial-3d", listOf("trial"), free("P3D"), base("P1Y", 39_990_000))
    val yearly = SubscriptionPlan("yearly", listOf(yearlyBase, trial3))

    val premiumSub = ProductInfo(TestProducts.PremiumSub, "premium_sub", ProductType.SUBSCRIPTION, "Premium", "Premium", "", emptyList(), listOf(monthly, yearly))
    val lifetime = ProductInfo(
        TestProducts.Lifetime, "premium_lifetime", ProductType.NON_CONSUMABLE, "Lifetime", "Lifetime", "",
        listOf(OneTimeOption(null, null, emptyList(), Price("$59.99", 59_990_000, "USD"))), emptyList(),
    )
    val coins = ProductInfo(
        TestProducts.Coins, "coins_100", ProductType.CONSUMABLE, "Coins", "Coins", "",
        listOf(OneTimeOption(null, null, emptyList(), Price("$0.99", 990_000, "USD"))), emptyList(),
    )
}
