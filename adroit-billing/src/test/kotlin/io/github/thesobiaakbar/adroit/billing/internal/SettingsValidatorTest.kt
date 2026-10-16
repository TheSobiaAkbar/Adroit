package io.github.thesobiaakbar.adroit.billing.internal

import io.github.thesobiaakbar.adroit.billing.BillingSettings
import io.github.thesobiaakbar.adroit.billing.PaywallDefinition
import io.github.thesobiaakbar.adroit.billing.PaywallSpec
import io.github.thesobiaakbar.adroit.billing.PlanSpec
import io.github.thesobiaakbar.adroit.billing.ProductDefinition
import io.github.thesobiaakbar.adroit.billing.ProductSpec
import io.github.thesobiaakbar.adroit.billing.TestPaywalls
import io.github.thesobiaakbar.adroit.billing.TestProducts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsValidatorTest {
    private class Product(override val name: String, override val defaults: ProductSpec) : ProductDefinition

    private class Paywall(override val name: String, override val defaults: PaywallSpec) : PaywallDefinition

    private fun problems(settings: BillingSettings): List<String> = validate(settings)

    private fun assertProblem(fragment: String, settings: BillingSettings) {
        val found = problems(settings)
        assertTrue("Expected a problem containing '$fragment', got $found", found.any { fragment in it })
    }

    @Test
    fun `valid settings have no problems`() {
        assertEquals(emptyList<String>(), problems(BillingSettings(TestProducts.entries, TestPaywalls.entries)))
    }

    @Test
    fun `no products is a problem`() {
        assertProblem("products is empty", BillingSettings(emptyList()))
    }

    @Test
    fun `duplicate product names and Play IDs are problems`() {
        val a = Product("Premium", ProductSpec.subscription("premium_sub", setOf("premium")))
        val b = Product("Premium", ProductSpec.subscription("other_sub", setOf("premium")))
        val c = Product("Other", ProductSpec.nonConsumable("premium_sub", setOf("premium")))
        assertProblem("Product name 'Premium'", BillingSettings(listOf(a, b)))
        assertProblem("Play product ID 'premium_sub'", BillingSettings(listOf(a, c)))
    }

    @Test
    fun `invalid names are problems`() {
        assertProblem("Invalid name 'has space'", BillingSettings(listOf(Product("has space", ProductSpec.consumable("c")))))
    }

    @Test
    fun `a paywall using an undeclared product is a problem`() {
        val settings = BillingSettings(listOf(TestProducts.Coins), listOf(TestPaywalls.Credits, paywall(TestProducts.Lifetime)))
        assertProblem("uses 'Lifetime', which isn't in products", settings)
    }

    @Test
    fun `a base plan on a one-time product is a problem`() {
        val wrong = Paywall("Wrong", PaywallSpec(listOf(PlanSpec(TestProducts.Coins, basePlanId = "monthly"))))
        assertProblem("sets a base plan on 'Coins'", BillingSettings(TestProducts.entries, listOf(wrong)))
    }

    @Test
    fun `duplicate paywalls, empty plans and an out-of-range selection are problems`() {
        val empty = Paywall("Empty", PaywallSpec(emptyList()))
        val outOfRange = Paywall("Range", PaywallSpec(listOf(PlanSpec(TestProducts.Coins)), selected = 1))
        assertProblem("Paywall name 'Empty'", BillingSettings(TestProducts.entries, listOf(empty, empty)))
        assertProblem("'Empty' has no plans", BillingSettings(TestProducts.entries, listOf(empty)))
        assertProblem("selects plan 1 but has 1", BillingSettings(TestProducts.entries, listOf(outOfRange)))
    }

    @Test
    fun `an unknown or self exit paywall is a problem`() {
        val unknown = Paywall("A", PaywallSpec(listOf(PlanSpec(TestProducts.Coins)), exitPaywall = "Missing"))
        val self = Paywall("B", PaywallSpec(listOf(PlanSpec(TestProducts.Coins)), exitPaywall = "B"))
        assertProblem("exitPaywall 'Missing'", BillingSettings(TestProducts.entries, listOf(unknown)))
        assertProblem("'B' is its own exitPaywall", BillingSettings(TestProducts.entries, listOf(self)))
    }

    private fun paywall(product: ProductDefinition) = Paywall("Uses${product.name}", PaywallSpec(listOf(PlanSpec(product))))
}
