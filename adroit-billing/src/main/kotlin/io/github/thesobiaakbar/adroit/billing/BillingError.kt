package io.github.thesobiaakbar.adroit.billing

/**
 * A stable, Play-independent description of why a billing operation failed. A user cancelling is not an error (see
 * [PurchaseResult.Cancelled]).
 *
 * Every subtype has a stable [code] for analytics. Where Google Play reported the failure, [playResponseCode] and
 * [debugMessage] carry its raw information for debugging. Nothing here contains personal data or purchase tokens.
 *
 * Stability: new subtypes may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public sealed interface BillingError {
    /** Stable, snake_case identifier for analytics and logs, for example `"network_error"`. */
    public val code: String

    /** Whether the same operation may succeed if tried again later (for example after the network returns). */
    public val isRetryable: Boolean

    /** Play's `BillingResponseCode`, when Play reported this error. */
    public val playResponseCode: Int? get() = null

    /** Play's or the library's debug message. Not localized; don't show it to users. */
    public val debugMessage: String? get() = null

    /** `AdroitBilling.initialize()` has not been called. */
    public data object NotInitialized : BillingError {
        override val code: String get() = "not_initialized"
        override val isRetryable: Boolean get() = false
        override val debugMessage: String get() = "Call AdroitBilling.initialize(application, settings) first"
    }

    /** Billing isn't available on this device or account (no Play Store, a blocked store, an unsupported country). */
    public data class Unavailable(
        override val playResponseCode: Int? = null,
        override val debugMessage: String? = null,
    ) : BillingError {
        override val code: String get() = "billing_unavailable"
        override val isRetryable: Boolean get() = false
    }

    /** The Play Store on this device doesn't support the requested feature. */
    public data class FeatureNotSupported(
        override val playResponseCode: Int? = null,
        override val debugMessage: String? = null,
    ) : BillingError {
        override val code: String get() = "feature_not_supported"
        override val isRetryable: Boolean get() = false
    }

    /** The connection to the Play Store service was lost or couldn't be made in time. */
    public data class ServiceDisconnected(
        override val playResponseCode: Int? = null,
        override val debugMessage: String? = null,
    ) : BillingError {
        override val code: String get() = "service_disconnected"
        override val isRetryable: Boolean get() = true
    }

    /** A network problem between the device and Google Play. */
    public data class NetworkError(
        override val playResponseCode: Int? = null,
        override val debugMessage: String? = null,
    ) : BillingError {
        override val code: String get() = "network_error"
        override val isRetryable: Boolean get() = true
    }

    /** Play doesn't know the product, or it isn't active, or isn't available to this user. */
    public data class ProductNotFound(public val productId: String) : BillingError {
        override val code: String get() = "product_not_found"
        override val isRetryable: Boolean get() = false
        override val debugMessage: String get() = "Product '$productId' not returned by Play"
    }

    /** The requested base plan or offer isn't among those Play gives this user. No other offer is substituted. */
    public data class OfferNotFound(
        public val productId: String,
        public val basePlanId: String?,
        public val offerId: String?,
    ) : BillingError {
        override val code: String get() = "offer_not_found"
        override val isRetryable: Boolean get() = false
        override val debugMessage: String get() = "No eligible offer $offerId on $productId/$basePlanId"
    }

    /** The user already owns the item. */
    public data class ItemAlreadyOwned(
        override val playResponseCode: Int? = null,
        override val debugMessage: String? = null,
    ) : BillingError {
        override val code: String get() = "item_already_owned"
        override val isRetryable: Boolean get() = false
    }

    /** The user doesn't own the item (for example a plan change from a subscription they don't have). */
    public data class ItemNotOwned(
        override val playResponseCode: Int? = null,
        override val debugMessage: String? = null,
    ) : BillingError {
        override val code: String get() = "item_not_owned"
        override val isRetryable: Boolean get() = false
    }

    /** The request was invalid: a bug or a Play Console setup problem. [debugMessage] says which. */
    public data class DeveloperError(
        override val playResponseCode: Int? = null,
        override val debugMessage: String? = null,
    ) : BillingError {
        override val code: String get() = "developer_error"
        override val isRetryable: Boolean get() = false
    }

    /** The payment was declined, for example for insufficient funds. The user can try another payment method. */
    public data class PaymentDeclined(
        override val playResponseCode: Int? = null,
        override val debugMessage: String? = null,
    ) : BillingError {
        override val code: String get() = "payment_declined"
        override val isRetryable: Boolean get() = true
    }

    /** Play says the user isn't eligible for the requested offer. */
    public data class UserIneligible(
        override val playResponseCode: Int? = null,
        override val debugMessage: String? = null,
    ) : BillingError {
        override val code: String get() = "user_ineligible"
        override val isRetryable: Boolean get() = false
    }

    /** The app's [PurchaseVerifier] rejected the purchase. Nothing was granted. */
    public data class VerificationFailed(public val reason: String) : BillingError {
        override val code: String get() = "verification_failed"
        override val isRetryable: Boolean get() = false
        override val debugMessage: String get() = reason
    }

    /** Acknowledging the purchase failed; it is tried again on the next sync. */
    public data class AcknowledgeFailed(
        override val playResponseCode: Int? = null,
        override val debugMessage: String? = null,
    ) : BillingError {
        override val code: String get() = "acknowledge_failed"
        override val isRetryable: Boolean get() = true
    }

    /** Consuming the purchase failed; it is tried again on the next sync. */
    public data class ConsumeFailed(
        override val playResponseCode: Int? = null,
        override val debugMessage: String? = null,
    ) : BillingError {
        override val code: String get() = "consume_failed"
        override val isRetryable: Boolean get() = true
    }

    /** The app's backend couldn't be reached or answered with an error. */
    public data class BackendError(override val debugMessage: String? = null) : BillingError {
        override val code: String get() = "backend_error"
        override val isRetryable: Boolean get() = true
    }

    /** The Activity passed in is finishing or destroyed. */
    public data object NoActivity : BillingError {
        override val code: String get() = "no_activity"
        override val isRetryable: Boolean get() = false
        override val debugMessage: String get() = "The Activity is finishing or destroyed"
    }

    /** Another purchase is in progress. Only one purchase runs at a time. */
    public data object PurchaseInProgress : BillingError {
        override val code: String get() = "purchase_in_progress"
        override val isRetryable: Boolean get() = true
        override val debugMessage: String get() = "Another purchase is in progress"
    }

    /** Any other failure; see [playResponseCode] and [debugMessage]. */
    public data class Unknown(
        override val playResponseCode: Int? = null,
        override val debugMessage: String? = null,
    ) : BillingError {
        override val code: String get() = "unknown"
        override val isRetryable: Boolean get() = false
    }
}
