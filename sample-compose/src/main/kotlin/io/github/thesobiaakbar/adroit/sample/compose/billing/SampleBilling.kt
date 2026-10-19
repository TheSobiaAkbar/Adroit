package io.github.thesobiaakbar.adroit.sample.compose.billing

import io.github.thesobiaakbar.adroit.billing.BackBehavior
import io.github.thesobiaakbar.adroit.billing.CloseBehavior
import io.github.thesobiaakbar.adroit.billing.OfferPolicy
import io.github.thesobiaakbar.adroit.billing.OfferRule
import io.github.thesobiaakbar.adroit.billing.PaywallDefinition
import io.github.thesobiaakbar.adroit.billing.PaywallSpec
import io.github.thesobiaakbar.adroit.billing.PaywallTemplate
import io.github.thesobiaakbar.adroit.billing.PaywallTexts
import io.github.thesobiaakbar.adroit.billing.PlanSpec
import io.github.thesobiaakbar.adroit.billing.ProductDefinition
import io.github.thesobiaakbar.adroit.billing.ProductSpec
import io.github.thesobiaakbar.adroit.billing.ShowRules
import io.github.thesobiaakbar.adroit.billing.SuccessAction

/*
 * Everything the app sells, and every paywall: the one file an app edits. The same as sample-xml's, so both samples
 * read the same Firebase keys. Each entry's name is its Firebase Remote Config key (project sample-ads-plugin): a JSON
 * value there overrides these defaults, field by field, on the next launch.
 * docs/firebase/sample_billing_remote_config.json has every key, ready to import.
 *
 * The products must exist in Play Console for this app ID (see the reports of B20 and B21); until then, turn on "Demo billing"
 * in Settings (debug builds), which uses made-up products with the same IDs and plans.
 */

/** The Play products. Both premium products unlock `premium`, which also makes the app ad-free. */
enum class SampleProducts(override val defaults: ProductSpec) : ProductDefinition {
    /** Base plans `weekly`, `monthly` (offer tagged `trial`: 3 days free) and `yearly` (offer tagged `intro`). */
    PremiumSub(ProductSpec.subscription("premium_sub", setOf("premium"))),
    PremiumLifetime(ProductSpec.nonConsumable("premium_lifetime", setOf("premium"))),

    /** 100 coins, credited by the Credits screen. */
    Coins100(ProductSpec.consumable("coins_100")),
}

/** The paywalls, by placement: the same products are sold from several of them. */
enum class SamplePaywalls(override val defaults: PaywallSpec) : PaywallDefinition {
    /** After the splash, once per session. The X appears after 3 s; closing it offers [ExitDiscount]. */
    Onboarding(
        PaywallSpec(
            plans = listOf(
                PlanSpec(SampleProducts.PremiumSub, basePlanId = "yearly", badge = "BEST_VALUE", savingsVs = "monthly"),
                PlanSpec(SampleProducts.PremiumSub, basePlanId = "monthly", offer = OfferRule(tags = listOf("trial"))),
                PlanSpec(SampleProducts.PremiumLifetime),
            ),
            close = CloseBehavior(delaySeconds = 3, showCountdown = true, back = BackBehavior.AFTER_DELAY),
            show = ShowRules(maxPerSession = 1),
            exitPaywall = "ExitDiscount",
            texts = PaywallTexts(
                title = "Go Premium",
                features = listOf("No ads, anywhere", "Every level unlocked", "Support the developer"),
            ),
        ),
    ),

    /** The Home screen's "Upgrade" card. */
    HomeUpgrade(
        PaywallSpec(
            plans = listOf(
                PlanSpec(SampleProducts.PremiumSub, basePlanId = "monthly", offer = OfferRule(tags = listOf("trial"))),
                PlanSpec(SampleProducts.PremiumSub, basePlanId = "yearly", savingsVs = "monthly"),
            ),
            template = PaywallTemplate.BOTTOM_SHEET,
            texts = PaywallTexts(title = "Remove ads"),
        ),
    ),

    /**
     * Settings' "Upgrade" button, opened whatever the frequency rules (the user asked): every plan, and the manage
     * link. It opens for premium users too (`hideIfEntitled` empty), so a subscriber can switch to lifetime.
     */
    SettingsUpgrade(
        PaywallSpec(
            plans = listOf(
                PlanSpec(SampleProducts.PremiumSub, basePlanId = "weekly"),
                PlanSpec(SampleProducts.PremiumSub, basePlanId = "monthly", offer = OfferRule(tags = listOf("trial"))),
                PlanSpec(SampleProducts.PremiumSub, basePlanId = "yearly", badge = "BEST_VALUE", savingsVs = "monthly"),
                PlanSpec(SampleProducts.PremiumLifetime),
            ),
            selected = 2,
            show = ShowRules(hideIfEntitled = emptySet()),
            showManage = true,
        ),
    ),

    /** Offered once per session when the user closes [Onboarding]. The X appears after 5 s. */
    ExitDiscount(
        PaywallSpec(
            plans = listOf(
                PlanSpec(
                    SampleProducts.PremiumSub, basePlanId = "yearly",
                    offer = OfferRule(tags = listOf("intro"), policy = OfferPolicy.PREFER_INTRO_PRICE),
                ),
            ),
            template = PaywallTemplate.DIALOG,
            close = CloseBehavior(delaySeconds = 5, showCountdown = true),
            show = ShowRules(maxPerDay = 1),
            texts = PaywallTexts(title = "Wait! Your first year for less"),
        ),
    ),

    /** The Credits screen's own UI, on this paywall's controller: no close button, and it stays open to buy again. */
    CreditsStore(
        PaywallSpec(
            plans = listOf(PlanSpec(SampleProducts.Coins100)),
            template = PaywallTemplate.SINGLE,
            close = CloseBehavior(delaySeconds = CloseBehavior.NO_CLOSE_BUTTON),
            showRestore = false,
            successAction = SuccessAction.STAY_WITH_MESSAGE,
            texts = PaywallTexts(title = "100 coins", cta = "Buy coins"),
        ),
    ),
}
