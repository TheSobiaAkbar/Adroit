package io.github.thesobiaakbar.adroit.billing

/**
 * The connection to the Google Play Billing service.
 *
 * Stability: new states may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public sealed interface BillingConnectionState {
    /** Not connected, before the first connection or after the app stopped it. */
    public data object Disconnected : BillingConnectionState

    /** Connecting. */
    public data object Connecting : BillingConnectionState

    /** Connected: products and purchases can be queried. */
    public data object Connected : BillingConnectionState

    /** Billing isn't available on this device or account; not retried until the app returns to the foreground. */
    public data class Unavailable(public val error: BillingError) : BillingConnectionState

    /** Connecting failed; retried at [retryAtMillis] (epoch milliseconds) or, when null, on the next trigger. */
    public data class Error(public val error: BillingError, public val retryAtMillis: Long?) : BillingConnectionState
}

/**
 * The app's products as Google Play describes them to this user.
 *
 * Stability: new states may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public sealed interface CatalogState {
    /** Not loaded yet. */
    public data object Loading : CatalogState

    /**
     * Loaded. [missing] holds the declared products Play didn't return (not created, inactive, or unavailable to this
     * user); they are never shown with an invented price.
     */
    public data class Ready(
        public val products: Map<ProductDefinition, ProductInfo>,
        public val missing: Set<ProductDefinition>,
    ) : CatalogState

    /** Loading failed and nothing was loaded before. */
    public data class Failed(public val error: BillingError) : CatalogState
}
