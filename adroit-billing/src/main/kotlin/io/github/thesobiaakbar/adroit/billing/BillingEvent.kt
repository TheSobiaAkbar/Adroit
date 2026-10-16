package io.github.thesobiaakbar.adroit.billing

/**
 * Something that happened in the billing library, for analytics and logs. The events themselves arrive with the
 * stages that emit them (docs/implementation-prompts/billing/B10_events_and_logging.md).
 *
 * No event contains a purchase token, a raw account ID or other personal data.
 *
 * Stability: new events may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public sealed interface BillingEvent {
    /** When it happened (epoch milliseconds). */
    public val timestampMillis: Long

    /** A purchase attempt was recorded and Play's purchase sheet is being opened. */
    public data class PurchaseInitiated(public val attribution: Attribution, override val timestampMillis: Long) :
        BillingEvent

    /** The user closed Play's purchase sheet. */
    public data class PurchaseCancelled(public val attribution: Attribution, override val timestampMillis: Long) :
        BillingEvent

    /** A purchase failed; [attribution] is null when it failed before an attempt was recorded. */
    public data class PurchaseFailed(
        public val error: BillingError,
        public val attribution: Attribution?,
        override val timestampMillis: Long,
    ) : BillingEvent

    /** A purchase is waiting for payment; nothing is granted yet. */
    public data class PurchasePending(
        public val productIds: List<String>,
        public val attribution: Attribution,
        override val timestampMillis: Long,
    ) : BillingEvent

    /** The app's [PurchaseVerifier] accepted a purchase. */
    public data class PurchaseVerified(public val attribution: Attribution, override val timestampMillis: Long) :
        BillingEvent

    /** A purchase was granted: once per purchase, however often Play reports it. */
    public data class PurchaseCompleted(
        public val productIds: List<String>,
        public val attribution: Attribution,
        override val timestampMillis: Long,
    ) : BillingEvent

    /** A newly granted product unlocked [entitlements]. */
    public data class EntitlementActivated(
        public val productId: String,
        public val entitlements: Set<String>,
        public val attribution: Attribution,
        override val timestampMillis: Long,
    ) : BillingEvent

    /** Acknowledging a granted purchase failed; it is tried again on the next sync. */
    public data class AcknowledgeFailed(
        public val error: BillingError,
        public val attribution: Attribution,
        override val timestampMillis: Long,
    ) : BillingEvent

    /** Consuming a confirmed consumable failed; it is tried again on the next sync. */
    public data class ConsumeFailed(
        public val error: BillingError,
        public val attribution: Attribution,
        override val timestampMillis: Long,
    ) : BillingEvent

    /** `restore()` started reading the user's purchases. */
    public data class RestoreInitiated(override val timestampMillis: Long) : BillingEvent

    /** `restore()` read the purchases; [restored] holds the entitlements that weren't active before. */
    public data class RestoreCompleted(public val restored: Set<String>, override val timestampMillis: Long) :
        BillingEvent
}

/** Receives every [BillingEvent] on the main thread. A listener that throws is logged and never breaks billing. */
public fun interface BillingEventListener {
    /** Called for each event. */
    public fun onEvent(event: BillingEvent)
}
