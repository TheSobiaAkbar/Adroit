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
 * @property replace the active subscription this purchase replaces (a plan change). Required when the user already has
 *   an active subscription sharing an entitlement with [product]: without it the purchase fails with
 *   [BillingError.DeveloperError] instead of starting a second subscription.
 */
public data class PurchaseRequest @JvmOverloads constructor(
    public val product: ProductDefinition,
    public val placement: String,
    public val basePlanId: String? = null,
    public val offer: OfferSelection = OfferSelection.Auto,
    public val campaignId: String? = null,
    public val paywallId: String? = null,
    public val replace: SubscriptionReplacement? = null,
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

/**
 * A plan change: the new subscription replaces [oldProduct], which must be active. Works for another base plan of the
 * same product (monthly to yearly) and for another product (basic to pro). Play checks whether [mode] is allowed for
 * the change and fails with [BillingError.DeveloperError], carrying Play's message, when it isn't.
 */
public data class SubscriptionReplacement(public val oldProduct: ProductDefinition, public val mode: ReplacementMode)

/**
 * When and how Play bills a plan change, mirroring Play's `SubscriptionProductReplacementParams.ReplacementMode`.
 *
 * Stability: new modes may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public enum class ReplacementMode {
    /** Changes now; the unused time of the old plan is credited towards the new one. */
    WITH_TIME_PRORATION,

    /** Changes now and charges the price difference for the rest of the current period. Upgrades only. */
    CHARGE_PRORATED_PRICE,

    /** Changes now and charges the new plan's full price; the unused time of the old plan is credited. */
    CHARGE_FULL_PRICE,

    /** Changes now; the new price is charged from the next renewal. */
    WITHOUT_PRORATION,

    /**
     * Changes at the next renewal. Until then the old plan stays in the entitlements, and the new one appears when
     * Play reports it.
     */
    DEFERRED,

    /** Keeps the old item's billing schedule (subscriptions with add-ons). */
    KEEP_EXISTING,
}
