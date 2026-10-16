package io.github.thesobiaakbar.adroit.billing

/** Products and paywalls shared by the tests, declared the way an app does. */
enum class TestProducts(override val defaults: ProductSpec) : ProductDefinition {
    PremiumSub(ProductSpec.subscription("premium_sub", setOf("premium", "ad_free"))),
    Lifetime(ProductSpec.nonConsumable("premium_lifetime", setOf("premium", "ad_free"))),
    Coins(ProductSpec.consumable("coins_100")),
}

enum class TestPaywalls(override val defaults: PaywallSpec) : PaywallDefinition {
    Onboarding(
        PaywallSpec(
            plans = listOf(PlanSpec(TestProducts.PremiumSub, basePlanId = "yearly")),
            close = CloseBehavior(delaySeconds = 3),
            exitPaywall = "ExitDiscount",
        ),
    ),
    ExitDiscount(PaywallSpec(plans = listOf(PlanSpec(TestProducts.PremiumSub, offer = OfferRule(tags = listOf("discount")))))),
    Credits(PaywallSpec(plans = listOf(PlanSpec(TestProducts.Coins)))),
}
