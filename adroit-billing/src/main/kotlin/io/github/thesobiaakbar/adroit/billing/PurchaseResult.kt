package io.github.thesobiaakbar.adroit.billing

/**
 * The outcome of `AdroitBilling.purchase`.
 *
 * Stability: new outcomes may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public sealed interface PurchaseResult {
    /** The purchase completed and was processed. [grant] is null for a consumable (see `AdroitBilling.consumables`). */
    public data class Purchased(public val grant: EntitlementGrant?, public val attribution: Attribution) : PurchaseResult

    /** Payment is pending (for example cash at a store). Nothing is granted until Play confirms it. */
    public data class Pending(public val attribution: Attribution) : PurchaseResult

    /** The user closed the Play purchase screen. Not an error. */
    public data class Cancelled(public val attribution: Attribution) : PurchaseResult

    /** The user already owns the product. A sync runs, so its entitlements appear if they were missing. */
    public data class AlreadyOwned(public val attribution: Attribution) : PurchaseResult

    /** The purchase failed. [attribution] is null when it failed before an attempt was recorded. */
    public data class Failed(public val error: BillingError, public val attribution: Attribution?) : PurchaseResult
}

/**
 * A purchase change seen outside a waiting `purchase` call: a pending payment that completed, a purchase finished
 * after its screen was closed, a purchase from another device or the Play Store, a suspended subscription.
 *
 * Stability: new kinds may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public sealed interface PurchaseUpdate {
    /** A purchase completed and was processed. */
    public data class Completed(public val productIds: List<String>, public val attribution: Attribution) : PurchaseUpdate

    /** A purchase is waiting for payment. */
    public data class Pending(public val productIds: List<String>, public val attribution: Attribution) : PurchaseUpdate

    /** A subscription is suspended: the user must fix their payment in Google Play. */
    public data class Suspended(public val productId: String) : PurchaseUpdate

    /** Processing a purchase failed. */
    public data class Failed(public val error: BillingError, public val attribution: Attribution?) : PurchaseUpdate
}

/**
 * The outcome of `AdroitBilling.restore`.
 *
 * Stability: new outcomes may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public sealed interface RestoreResult {
    /** Purchases were found; [newlyActive] holds the entitlements that weren't active before. */
    public data class Restored(public val newlyActive: Set<String>, public val snapshot: EntitlementSnapshot) :
        RestoreResult

    /** Google Play has no purchases of this app's products for this account. */
    public data object NothingToRestore : RestoreResult

    /** The purchases couldn't be read; nothing changed. */
    public data class Failed(public val error: BillingError) : RestoreResult
}

/**
 * A bought consumable, waiting for the app to credit it. Add [quantity] to the user's balance, store it, then call
 * [confirm]; only then is it consumed in Play (and can be bought again). Until confirmed, it is delivered again,
 * after a restart too, so make the credit idempotent by [orderIdHash].
 *
 * @property productId the Play product ID.
 * @property quantity how many units were bought.
 * @property attribution where the purchase came from.
 * @property orderIdHash a stable hash of Play's order ID, to make crediting idempotent.
 *
 * The constructor is public for test doubles such as `FakeBilling`; the library creates the real grants. [onConfirm]
 * runs on [confirm].
 */
public class ConsumableGrant(
    public val productId: String,
    public val quantity: Int,
    public val attribution: Attribution,
    public val orderIdHash: String,
    private val onConfirm: suspend () -> Unit,
) {
    /** Tells the library the app has credited this purchase, so it is consumed. Safe to call more than once. */
    public suspend fun confirm() {
        onConfirm()
    }

    override fun equals(other: Any?): Boolean = other is ConsumableGrant && orderIdHash == other.orderIdHash

    override fun hashCode(): Int = orderIdHash.hashCode()

    override fun toString(): String =
        "ConsumableGrant(productId=$productId, quantity=$quantity, placement=${attribution.placement}, " +
            "orderIdHash=$orderIdHash)"
}
