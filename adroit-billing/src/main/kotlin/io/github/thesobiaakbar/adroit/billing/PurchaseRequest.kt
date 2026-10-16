package io.github.thesobiaakbar.adroit.billing

/**
 * Which subscription offer a purchase uses. Only offers Play gives this user can be bought: when the requested one
 * isn't among them, the purchase fails with [BillingError.OfferNotFound] and no other offer is substituted.
 *
 * Stability: new kinds may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public sealed interface OfferSelection {
    /** The offer chosen by the paywall's [OfferRule], or the base plan when there is none. */
    public data object Auto : OfferSelection

    /** The base plan, without any offer. */
    public data object BaseOnly : OfferSelection

    /** The offer with this Play offer ID. */
    public data class Id(public val offerId: String) : OfferSelection

    /** An offer with all of these Play offer tags. */
    public data class Tags(public val tags: List<String>) : OfferSelection
}

/**
 * A purchase to start with `AdroitBilling.purchase`.
 *
 * ```
 * AdroitBilling.purchase(activity, PurchaseRequest(AppProducts.PremiumSub, placement = "settings", basePlanId = "yearly"))
 * ```
 *
 * @property product the product to buy.
 * @property placement where in the app the purchase starts, for example `"onboarding"` or `"home_banner"`: letters,
 *   digits, `_`, `.` and `-`, up to 64. Reported with the purchase's events and attribution.
 * @property basePlanId the subscription base plan; null takes the first one Play returns. Null for a one-time product.
 * @property offer the subscription offer.
 * @property campaignId the campaign the purchase belongs to, if any.
 * @property paywallId the [PaywallDefinition.name] the purchase starts from, if any.
 */
public data class PurchaseRequest @JvmOverloads constructor(
    public val product: ProductDefinition,
    public val placement: String,
    public val basePlanId: String? = null,
    public val offer: OfferSelection = OfferSelection.Auto,
    public val campaignId: String? = null,
    public val paywallId: String? = null,
) {
    init {
        require(PLACEMENT.matches(placement)) {
            "Invalid placement '$placement': letters, digits, '_', '.' and '-', 1 to 64 characters"
        }
        basePlanId?.let { require(PlanSpec.PLAY_ID.matches(it)) { "Invalid base plan ID '$it'" } }
    }

    private companion object {
        val PLACEMENT = Regex("[A-Za-z0-9_.-]{1,64}")
    }
}
