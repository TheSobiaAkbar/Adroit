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
}

/** Receives every [BillingEvent] on the main thread. A listener that throws is logged and never breaks billing. */
public fun interface BillingEventListener {
    /** Called for each event. */
    public fun onEvent(event: BillingEvent)
}
