package io.github.thesobiaakbar.adroit.billing

/**
 * The state of one owned product.
 *
 * Stability: new states may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public enum class GrantState {
    /** Owned and in good standing. Grants access. */
    ACTIVE,

    /** A subscription in its grace period after a failed payment, as reported by the app's backend. Grants access. */
    GRACE_FROM_BACKEND,

    /** A subscription Play reports as suspended (payment failed, account hold). No access: ask the user to fix it. */
    SUSPENDED,

    /** A purchase waiting for payment (for example cash at a store). No access yet. */
    PENDING,
}

/** Where a grant's information comes from. */
public enum class GrantSource {
    /** Google Play on this device. */
    CLIENT,

    /** The app's backend, through an [EntitlementProvider]. */
    BACKEND,
}

/**
 * One owned product and what it unlocks.
 *
 * @property productId the Play product ID.
 * @property type the kind of product.
 * @property entitlements what it unlocks while its [state] grants access.
 * @property state the product's state.
 * @property source where this information comes from.
 * @property purchaseTimeMillis when it was bought (epoch milliseconds).
 */
public data class EntitlementGrant(
    public val productId: String,
    public val type: ProductType,
    public val entitlements: Set<String>,
    public val state: GrantState,
    public val source: GrantSource,
    public val purchaseTimeMillis: Long,
) {
    /** Whether this grant gives access now: [GrantState.ACTIVE] or [GrantState.GRACE_FROM_BACKEND]. */
    public val grantsAccess: Boolean
        get() = state == GrantState.ACTIVE || state == GrantState.GRACE_FROM_BACKEND

    /** Whether the user must fix their payment ([GrantState.SUSPENDED]): offer `openManageSubscriptions`. */
    public val needsPaymentFix: Boolean
        get() = state == GrantState.SUSPENDED
}

/**
 * How current an [EntitlementSnapshot] is.
 *
 * Stability: new statuses may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public sealed interface SnapshotStatus {
    /** Confirmed by Google Play (or the backend) at [atMillis] in this process. */
    public data class Fresh(public val atMillis: Long) : SnapshotStatus

    /** Saved from the last confirmation at [sinceMillis]; not yet confirmed in this process. */
    public data class Cached(public val sinceMillis: Long) : SnapshotStatus

    /** Nothing reliable is known: never confirmed, or the cache is too old. Grants nothing. */
    public data object Unknown : SnapshotStatus
}

/**
 * What this user owns now: the single answer to "is the user premium?".
 *
 * Nothing is granted for a pending or suspended purchase, nor while [status] is [SnapshotStatus.Unknown]. Show a
 * neutral state while unknown, rather than treating the user as free for good.
 *
 * @property grants every owned product, including those that don't give access ([EntitlementGrant.grantsAccess]).
 * @property status how current this snapshot is.
 * @property adFreeEntitlements the entitlements that make the user ad-free (see [adFree]).
 */
public data class EntitlementSnapshot(
    public val grants: List<EntitlementGrant>,
    public val status: SnapshotStatus,
    public val adFreeEntitlements: Set<String>,
) {
    /** Every entitlement the user has now. Empty while [status] is [SnapshotStatus.Unknown]. */
    public val active: Set<String>
        get() = if (status == SnapshotStatus.Unknown) {
            emptySet()
        } else {
            grants.filter { it.grantsAccess }.flatMapTo(LinkedHashSet()) { it.entitlements }
        }

    /** Whether the user has [entitlement] now. False while [status] is [SnapshotStatus.Unknown]. */
    public fun has(entitlement: String): Boolean = entitlement in active

    /** Whether the user has any of [adFreeEntitlements]: pass it to `Adroit.setAdFree`. */
    public val adFree: Boolean
        get() = adFreeEntitlements.any(::has)

    /** Whether any grant [needs a payment fix][EntitlementGrant.needsPaymentFix]. */
    public val needsPaymentFix: Boolean
        get() = grants.any { it.needsPaymentFix }

    /** Constants. */
    public companion object {
        /** Nothing known: the value before the first confirmation and without a cache. */
        @JvmField
        public val EMPTY_UNKNOWN: EntitlementSnapshot = EntitlementSnapshot(emptyList(), SnapshotStatus.Unknown, emptySet())
    }
}
