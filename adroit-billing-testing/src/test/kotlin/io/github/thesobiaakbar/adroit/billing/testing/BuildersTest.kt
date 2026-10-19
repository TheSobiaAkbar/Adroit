package io.github.thesobiaakbar.adroit.billing.testing

import android.app.Activity
import io.github.thesobiaakbar.adroit.billing.BillingPeriod
import io.github.thesobiaakbar.adroit.billing.OfferPolicy
import io.github.thesobiaakbar.adroit.billing.OfferRule
import io.github.thesobiaakbar.adroit.billing.PaywallDefinition
import io.github.thesobiaakbar.adroit.billing.PaywallSpec
import io.github.thesobiaakbar.adroit.billing.PlanSpec
import io.github.thesobiaakbar.adroit.billing.ProductDefinition
import io.github.thesobiaakbar.adroit.billing.ProductSpec
import io.github.thesobiaakbar.adroit.billing.PurchaseRequest
import io.github.thesobiaakbar.adroit.billing.PurchaseResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildersTest {
    private enum class Products(override val defaults: ProductSpec) : ProductDefinition {
        Sub(ProductSpec.subscription("premium_sub", setOf("premium"))),
        Coins(ProductSpec.consumable("coins_100")),
    }

    private class Paywall(override val defaults: PaywallSpec) : PaywallDefinition {
        override val name: String = "Onboarding"
    }

    private val sub = TestProducts.subscription(Products.Sub) {
        basePlan("monthly") {
            trial("P7D")
            recurring("P1M", "$4.99", 4_990_000, "USD")
        }
        basePlan("yearly") {
            offer("intro-half", tags = listOf("discount")) { intro("P1Y", "$14.99", 14_990_000, "USD") }
            recurring("P1Y", "$29.99", 29_990_000, "USD")
        }
    }

    @Test
    fun `a subscription has Play's shape - offers with their phases, then the base offer`() {
        val monthly = sub.plans.first { it.basePlanId == "monthly" }

        assertEquals(listOf("intro", null), monthly.offers.map { it.offerId })
        assertEquals(BillingPeriod(days = 7), monthly.offers[0].freeTrial)
        assertEquals("Free", monthly.offers[0].phases[0].price.formatted)
        assertEquals(4_990_000L, monthly.basePhase?.price?.micros)
        assertEquals(listOf("discount"), sub.plans[1].offers[0].offerTags)
        assertEquals("$14.99", sub.plans[1].offers[0].firstPayment?.price?.formatted)
    }

    @Test
    fun `a one-time product has one purchase option`() {
        val coins = TestProducts.oneTime(Products.Coins, "$0.99", 990_000, "USD")

        assertEquals("coins_100", coins.productId)
        assertEquals("$0.99", coins.oneTimeOptions.single().price.formatted)
        assertTrue(coins.plans.isEmpty())
    }

    @Test
    fun `the paywall model picks offers by id, tags, trial or base, and leaves out missing products`() {
        val spec = PaywallSpec(
            plans = listOf(
                PlanSpec(Products.Sub, basePlanId = "monthly"),
                PlanSpec(Products.Sub, basePlanId = "yearly", offer = OfferRule(tags = listOf("discount"))),
                PlanSpec(Products.Sub, basePlanId = "monthly", offer = OfferRule(policy = OfferPolicy.BASE_ONLY)),
                PlanSpec(Products.Coins),
            ),
        )

        val model = TestPaywalls.model(Paywall(spec), listOf(sub), selected = 1)

        assertEquals("Onboarding", model.name)
        assertEquals(listOf("intro", "intro-half", null), model.plans.map { it.offerId })
        assertEquals(1, model.selected)
    }

    @Test
    fun `the catalog serves the products, and a consumable purchase is delivered for crediting`() = runTest {
        val coins = TestProducts.oneTime(Products.Coins, "$0.99", 990_000, "USD")
        val billing = FakeBilling(listOf(sub, coins))
        assertEquals(sub, billing.product(Products.Sub))

        val result = billing.purchase(Activity(), PurchaseRequest(Products.Coins, "shop"))

        assertNull((result as PurchaseResult.Purchased).grant)
        val grant = billing.consumables.first()
        assertEquals("shop", grant.attribution.placement)
        grant.confirm()
        grant.confirm()
        assertEquals(listOf(grant), billing.confirmedConsumables)
        assertTrue(billing.entitlements.value.active.isEmpty())
    }
}
