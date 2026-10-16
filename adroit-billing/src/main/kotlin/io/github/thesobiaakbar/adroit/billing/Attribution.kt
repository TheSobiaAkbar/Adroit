package io.github.thesobiaakbar.adroit.billing

/**
 * Where a purchase came from. Each field is a separate concept: one Play product can be bought from any number of
 * placements, paywalls and campaigns.
 *
 * Kept on the device (and passed to events and the app's [PurchaseVerifier]); never attached to the Play
 * transaction. When a purchase can't be matched to the attempt that started it (for example one made in an earlier
 * install), [placement] is [UNKNOWN_PLACEMENT] and [recovered] is true: a placement is never guessed.
 *
 * @property attemptId a random ID of the purchase attempt, or null when no attempt matched.
 * @property placement the app's placement key, for example `"onboarding"`, or [UNKNOWN_PLACEMENT].
 * @property paywallId the [PaywallDefinition.name] the purchase was made from, if any.
 * @property campaignId the campaign ID, if any.
 * @property productId the Play product ID.
 * @property basePlanId the subscription base plan ID, if any.
 * @property offerId the Play offer ID, if any.
 * @property recovered true when this was rebuilt after the fact (a restart, or a purchase seen on a sync).
 */
public data class Attribution(
    public val attemptId: String?,
    public val placement: String,
    public val paywallId: String?,
    public val campaignId: String?,
    public val productId: String,
    public val basePlanId: String?,
    public val offerId: String?,
    public val recovered: Boolean,
) {
    /** Constants and factories. */
    public companion object {
        /** The placement of a purchase that matched no recorded attempt. */
        public const val UNKNOWN_PLACEMENT: String = "unknown"

        /** The attribution of a purchase of [productId] that matched no recorded attempt. */
        @JvmStatic
        public fun unknown(productId: String): Attribution =
            Attribution(null, UNKNOWN_PLACEMENT, null, null, productId, null, null, recovered = true)
    }
}
