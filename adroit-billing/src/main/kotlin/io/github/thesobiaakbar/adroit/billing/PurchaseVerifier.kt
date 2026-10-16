package io.github.thesobiaakbar.adroit.billing

/**
 * Checks each purchase with the app's backend before anything is granted (docs/BILLING_BACKEND.md). Optional:
 * without one, purchases are trusted as Google Play reports them on the device, which a tampered device can fake.
 *
 * The backend verifies [PurchaseToVerify.purchaseToken] with the Google Play Developer API. Its credentials stay on
 * the server; never put them in the app or in Remote Config.
 */
public fun interface PurchaseVerifier {
    /** Verifies [purchase]. Called on the main thread; switch to a background dispatcher for network calls. */
    public suspend fun verify(purchase: PurchaseToVerify): VerificationResult
}

/**
 * A purchase to verify with the app's backend.
 *
 * @property productIds the Play product IDs in the purchase.
 * @property type the kind of product.
 * @property purchaseToken Play's purchase token: send it to the backend only. Never log it; [toString] hides it.
 * @property packageName the app's package name.
 * @property attribution where the purchase came from.
 * @property accountIdHash the hash of the app's account ID set with `AdroitBilling.setAccount`, or null.
 */
public class PurchaseToVerify internal constructor(
    public val productIds: List<String>,
    public val type: ProductType,
    public val purchaseToken: String,
    public val packageName: String,
    public val attribution: Attribution,
    public val accountIdHash: String?,
) {
    override fun toString(): String =
        "PurchaseToVerify(productIds=$productIds, type=$type, purchaseToken=<hidden>, packageName=$packageName, " +
            "placement=${attribution.placement})"
}

/**
 * The backend's answer to [PurchaseVerifier.verify].
 *
 * Stability: new kinds may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public sealed interface VerificationResult {
    /** Valid. [acknowledgedByBackend] true means the backend acknowledged it, so the app doesn't. */
    public data class Verified(public val acknowledgedByBackend: Boolean = false) : VerificationResult

    /** Invalid: nothing is granted and the purchase isn't acknowledged. */
    public data class Rejected(public val reason: String) : VerificationResult

    /** The backend couldn't answer. Nothing new is granted; the purchase is verified again on the next sync. */
    public data class Unreachable(public val reason: String? = null) : VerificationResult
}

/**
 * The app's backend as a source of entitlements (grace periods, account holds, expiries and refunds it learns from
 * Google Play's real-time notifications). Optional: its grants are merged with what Play reports on the device.
 */
public fun interface EntitlementProvider {
    /**
     * The grants the backend knows for the account with [accountIdHash] (null when the app set no account), or null
     * when the backend can't answer, in which case Play's data alone is used. Called on the main thread.
     */
    public suspend fun fetch(accountIdHash: String?): List<EntitlementGrant>?
}
