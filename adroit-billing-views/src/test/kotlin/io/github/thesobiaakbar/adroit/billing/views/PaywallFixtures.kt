package io.github.thesobiaakbar.adroit.billing.views

import io.github.thesobiaakbar.adroit.billing.CloseBehavior
import io.github.thesobiaakbar.adroit.billing.PaywallDefinition
import io.github.thesobiaakbar.adroit.billing.PaywallSpec
import io.github.thesobiaakbar.adroit.billing.PaywallTemplate
import io.github.thesobiaakbar.adroit.billing.PaywallTexts
import io.github.thesobiaakbar.adroit.billing.PlanSpec
import io.github.thesobiaakbar.adroit.billing.ProductDefinition
import io.github.thesobiaakbar.adroit.billing.ProductSpec
import io.github.thesobiaakbar.adroit.billing.SuccessAction
import io.github.thesobiaakbar.adroit.billing.testing.TestPaywalls
import io.github.thesobiaakbar.adroit.billing.testing.TestProducts

enum class TestProduct(override val defaults: ProductSpec) : ProductDefinition {
    Premium(ProductSpec.subscription("premium_sub", setOf("premium"))),
}

class TestPaywall(override val defaults: PaywallSpec, override val name: String = "Onboarding") : PaywallDefinition

/** Play-like data: a monthly plan with a 7-day trial and a yearly plan without one. */
object Fixtures {
    private val product = TestProducts.subscription(TestProduct.Premium) {
        basePlan("monthly") {
            offer("trial") { trial("P7D") }
            recurring("P1M", "\$4.99", 4_990_000, "USD")
        }
        basePlan("yearly") { recurring("P1Y", "\$29.99", 29_990_000, "USD") }
    }

    fun spec(
        close: CloseBehavior = CloseBehavior(),
        template: PaywallTemplate = PaywallTemplate.PLANS,
        successAction: SuccessAction = SuccessAction.CLOSE,
        texts: PaywallTexts = PaywallTexts(title = "Go premium", features = listOf("No ads", "All filters")),
    ) = PaywallSpec(
        plans = listOf(
            PlanSpec(TestProduct.Premium, basePlanId = "monthly"),
            PlanSpec(TestProduct.Premium, basePlanId = "yearly", badge = "BEST_VALUE"),
        ),
        template = template,
        close = close,
        texts = texts,
        successAction = successAction,
    )

    fun model(spec: PaywallSpec) = TestPaywalls.model(TestPaywall(spec), listOf(product))
}
