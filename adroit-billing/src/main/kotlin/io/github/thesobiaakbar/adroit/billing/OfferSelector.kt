package io.github.thesobiaakbar.adroit.billing

/**
 * Where an offer is being chosen.
 *
 * @property product the product of the plan.
 * @property paywallId the [PaywallDefinition.name] of the paywall, or null outside a paywall.
 */
public data class OfferContext(public val product: ProductDefinition, public val paywallId: String?)

/**
 * Chooses a paywall plan's offer, in place of the library's [OfferRule] logic (`BillingSettings.offerSelector`).
 *
 * [select] gets only the offers this user may buy: Play's offers for [SubscriptionPlan] minus developer-determined
 * ones that [OfferEligibility] refused. Returning an offer that isn't in [SubscriptionPlan.offers], or null, counts
 * as "the offer isn't available" and applies the paywall's `onOfferUnavailable`. Called on the main thread; keep it
 * fast.
 */
public fun interface OfferSelector {
    /** The offer to show for [plan] under [rule], or null when none fits. */
    public fun select(plan: SubscriptionPlan, rule: OfferRule, context: OfferContext): OfferInfo?
}

/**
 * Decides whether this user may get a *developer-determined* offer: one whose tags include one of
 * `BillingSettings.developerDeterminedTags`. Play returns those offers to every user, so without this hook they are
 * never shown. Called on the main thread; keep it fast.
 */
public fun interface OfferEligibility {
    /** True when [offer] may be shown and bought by this user. */
    public fun isEligible(offer: OfferInfo, context: OfferContext): Boolean
}
