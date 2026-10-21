package io.github.thesobiaakbar.adroit.docs.guides.iap

import io.github.thesobiaakbar.adroit.billing.BackBehavior
import io.github.thesobiaakbar.adroit.billing.CloseBehavior
import io.github.thesobiaakbar.adroit.billing.OfferRule
import io.github.thesobiaakbar.adroit.billing.PaywallDefinition
import io.github.thesobiaakbar.adroit.billing.PaywallSpec
import io.github.thesobiaakbar.adroit.billing.PaywallTexts
import io.github.thesobiaakbar.adroit.billing.PlanSpec
import io.github.thesobiaakbar.adroit.billing.ProductDefinition
import io.github.thesobiaakbar.adroit.billing.ProductSpec

// The billing enums of docs/guides/IAP_*.md, step 4: an app used only by subscribers.

// region guide-iap-enum
/** Everything the app sells. The IDs are the product IDs in Play Console. */
enum class AppProducts(override val defaults: ProductSpec) : ProductDefinition {
    /** A subscription with base plans "monthly" and "yearly" in Play Console. */
    PremiumSub(ProductSpec.subscription("premium_sub", entitlements = setOf("premium"))),
    PremiumLifetime(ProductSpec.nonConsumable("premium_lifetime", entitlements = setOf("premium"))),
}

/** Every paywall. Each name is also its Firebase parameter: a JSON value there changes it without a release. */
enum class AppPaywalls(override val defaults: PaywallSpec) : PaywallDefinition {
    /** The hard paywall at launch: no close button, and back doesn't close it. Buy, restore, or leave the app. */
    Subscribe(
        PaywallSpec(
            plans = listOf(
                PlanSpec(AppProducts.PremiumSub, basePlanId = "yearly", badge = "BEST_VALUE", savingsVs = "monthly"),
                PlanSpec(AppProducts.PremiumSub, basePlanId = "monthly", offer = OfferRule(tags = listOf("trial"))),
                PlanSpec(AppProducts.PremiumLifetime),
            ),
            close = CloseBehavior(delaySeconds = -1, back = BackBehavior.NEVER),
            texts = PaywallTexts(title = "Subscribe to continue", features = listOf("Every feature", "Cancel anytime")),
        ),
    ),

    /** Opened from Settings, to change plans. */
    Settings(
        PaywallSpec(
            plans = listOf(
                PlanSpec(AppProducts.PremiumSub, basePlanId = "monthly"),
                PlanSpec(AppProducts.PremiumSub, basePlanId = "yearly", savingsVs = "monthly"),
                PlanSpec(AppProducts.PremiumLifetime),
            ),
            showManage = true,
        ),
    ),
}
// endregion
