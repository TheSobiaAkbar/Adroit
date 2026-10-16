package io.github.thesobiaakbar.adroit.billing

/**
 * Something that happened in the billing library, for analytics and logs.
 *
 * Every event is client-observed, except [ServerLifecycle], which only the app's [EntitlementProvider] data produces:
 * the client never claims a subscription renewed, expired or was cancelled. No event contains a purchase token, a raw
 * account ID or a formatted price; [PurchaseCompleted] alone carries the price, as micros and a currency code.
 *
 * [name] and [toParams] give a ready analytics event (Firebase limits: names and keys of 40 characters or fewer,
 * string values of 100 or fewer).
 *
 * Stability: new events may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public sealed interface BillingEvent {
    /** When it happened (epoch milliseconds). */
    public val timestampMillis: Long

    /** The analytics event name, for example `"adroit_billing_purchase_completed"`. Stable across versions. */
    public val name: String
        get() = eventName(this)

    /**
     * The analytics parameters: snake_case keys of 40 characters or fewer, values that are a [String] of 100
     * characters or fewer, or a [Long]. Unknown values are left out. The timestamp isn't included.
     */
    public fun toParams(): Map<String, Any> = eventParams(this)

    /** Connecting to Google Play started (on start-up, a retry, or a reconnect). */
    public data class ConnectionStarted(override val timestampMillis: Long) : BillingEvent

    /** The Play connection is up. */
    public data class ConnectionEstablished(override val timestampMillis: Long) : BillingEvent

    /** Connecting to Play failed. */
    public data class ConnectionFailed(public val error: BillingError, override val timestampMillis: Long) : BillingEvent

    /** The products loaded: [count] found, [missing] holds the Play IDs of declared products Play didn't return. */
    public data class CatalogLoaded(
        public val count: Int,
        public val missing: List<String>,
        override val timestampMillis: Long,
    ) : BillingEvent

    /** Loading the products failed. */
    public data class CatalogFailed(public val error: BillingError, override val timestampMillis: Long) : BillingEvent

    /** A paywall became visible. */
    public data class PaywallShown(
        public val paywallId: String,
        public val placement: String,
        public val campaignId: String?,
        override val timestampMillis: Long,
    ) : BillingEvent

    /** A paywall closed after [secondsVisible] seconds on screen. */
    public data class PaywallClosed(
        public val paywallId: String,
        public val placement: String,
        public val campaignId: String?,
        public val reason: PaywallCloseReason,
        public val secondsVisible: Long,
        override val timestampMillis: Long,
    ) : BillingEvent

    /** A paywall's close (X) button appeared, [afterSeconds] seconds after the paywall was shown. */
    public data class PaywallCloseButtonShown(
        public val paywallId: String,
        public val placement: String,
        public val campaignId: String?,
        public val afterSeconds: Long,
        override val timestampMillis: Long,
    ) : BillingEvent

    /** A paywall wasn't shown; [reason] is a stable snake_case code, for example `"frequency_cap"`. */
    public data class PaywallSuppressed(
        public val paywallId: String,
        public val placement: String,
        public val campaignId: String?,
        public val reason: String,
        override val timestampMillis: Long,
    ) : BillingEvent

    /** A plan or offer was shown to the user. */
    public data class OfferShown(
        public val productId: String,
        public val basePlanId: String?,
        public val offerId: String?,
        public val paywallId: String?,
        public val placement: String,
        public val campaignId: String?,
        override val timestampMillis: Long,
    ) : BillingEvent

    /**
     * The [requested] offer isn't available to this user (not eligible, or not in Play); [fallbackOfferId] was used
     * instead, null meaning the base plan's own price.
     */
    public data class OfferUnavailable(
        public val productId: String,
        public val basePlanId: String?,
        public val requested: String,
        public val fallbackOfferId: String?,
        public val paywallId: String?,
        public val placement: String,
        override val timestampMillis: Long,
    ) : BillingEvent

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

    /**
     * A purchase was granted: once per purchase, however often Play reports it.
     *
     * [priceMicros] (price times quantity) and [currency] are what Play's catalog says the attributed plan or option
     * charges at purchase, 0 for a free trial. Both are null when the purchase can't be matched to a loaded price
     * (for example one made in an earlier install); a price is never guessed.
     */
    public data class PurchaseCompleted(
        public val productIds: List<String>,
        public val attribution: Attribution,
        override val timestampMillis: Long,
        public val priceMicros: Long? = null,
        public val currency: String? = null,
    ) : BillingEvent

    /** A newly granted product unlocked [entitlements]. */
    public data class EntitlementActivated(
        public val productId: String,
        public val entitlements: Set<String>,
        public val attribution: Attribution,
        override val timestampMillis: Long,
    ) : BillingEvent

    /** The user no longer has [entitlement]. */
    public data class EntitlementLost(
        public val entitlement: String,
        public val reason: EntitlementLossReason,
        override val timestampMillis: Long,
    ) : BillingEvent

    /** `restore()` started reading the user's purchases. */
    public data class RestoreInitiated(override val timestampMillis: Long) : BillingEvent

    /** `restore()` read the purchases; [restored] holds the entitlements that weren't active before. */
    public data class RestoreCompleted(public val restored: Set<String>, override val timestampMillis: Long) :
        BillingEvent

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

    /** The remote billing configuration was applied; [version] is its `version` value, if it has one. */
    public data class ConfigLoaded(public val version: String?, override val timestampMillis: Long) : BillingEvent

    /** The remote value [key] was invalid and ignored ([reason] says why); the in-code defaults apply. */
    public data class ConfigInvalid(
        public val key: String,
        public val reason: String,
        override val timestampMillis: Long,
    ) : BillingEvent

    /**
     * A subscription's lifecycle changed according to the app's backend ([EntitlementProvider]), between two syncs.
     * Never produced from Play's data on the device.
     */
    public data class ServerLifecycle(
        public val kind: ServerLifecycleKind,
        public val productId: String,
        override val timestampMillis: Long,
    ) : BillingEvent {
        /** Always [GrantSource.BACKEND]. */
        public val source: GrantSource
            get() = GrantSource.BACKEND
    }
}

/** Why a paywall closed. */
public enum class PaywallCloseReason {
    /** The close (X) button. */
    CLOSE_BUTTON,

    /** The system back gesture or button. */
    BACK,

    /** A purchase completed. */
    PURCHASED,

    /** An error closed it. */
    ERROR,
}

/**
 * Why an entitlement was lost. Client-side reasons only: none of them says a subscription expired or was cancelled.
 *
 * Stability: new reasons may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public enum class EntitlementLossReason {
    /** Play (or the backend) no longer reports a product that unlocks it. */
    NOT_OWNED,

    /** Its subscription is suspended: the user must fix their payment. */
    PAYMENT_ISSUE,

    /** No successful sync within the offline grace period. */
    OFFLINE_GRACE_ENDED,

    /** The app set a different account. */
    ACCOUNT_SWITCHED,

    /** The product is still owned, but the remote configuration no longer maps it to this entitlement. */
    CONFIG_CHANGED,
}

/**
 * A change the backend reported between two syncs.
 *
 * Stability: new kinds may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public enum class ServerLifecycleKind {
    /** The subscription entered its grace period (`GRACE_FROM_BACKEND`). */
    GRACE_PERIOD,

    /** The subscription is on hold (the backend reports it `SUSPENDED`). */
    ON_HOLD,

    /** The subscription is active again after a grace period or hold. */
    RECOVERED,

    /** The backend no longer reports the product (it expired, was refunded or revoked: the backend knows which). */
    ENDED,
}

private const val PREFIX = "adroit_billing_"

private const val MAX_VALUE = 100

private fun eventName(event: BillingEvent): String = PREFIX + when (event) {
    is BillingEvent.ConnectionStarted -> "connection_started"
    is BillingEvent.ConnectionEstablished -> "connection_established"
    is BillingEvent.ConnectionFailed -> "connection_failed"
    is BillingEvent.CatalogLoaded -> "catalog_loaded"
    is BillingEvent.CatalogFailed -> "catalog_failed"
    is BillingEvent.PaywallShown -> "paywall_shown"
    is BillingEvent.PaywallClosed -> "paywall_closed"
    is BillingEvent.PaywallCloseButtonShown -> "paywall_close_shown"
    is BillingEvent.PaywallSuppressed -> "paywall_suppressed"
    is BillingEvent.OfferShown -> "offer_shown"
    is BillingEvent.OfferUnavailable -> "offer_unavailable"
    is BillingEvent.PurchaseInitiated -> "purchase_initiated"
    is BillingEvent.PurchaseCancelled -> "purchase_cancelled"
    is BillingEvent.PurchaseFailed -> "purchase_failed"
    is BillingEvent.PurchasePending -> "purchase_pending"
    is BillingEvent.PurchaseVerified -> "purchase_verified"
    is BillingEvent.PurchaseCompleted -> "purchase_completed"
    is BillingEvent.EntitlementActivated -> "entitlement_activated"
    is BillingEvent.EntitlementLost -> "entitlement_lost"
    is BillingEvent.RestoreInitiated -> "restore_initiated"
    is BillingEvent.RestoreCompleted -> "restore_completed"
    is BillingEvent.AcknowledgeFailed -> "acknowledge_failed"
    is BillingEvent.ConsumeFailed -> "consume_failed"
    is BillingEvent.ConfigLoaded -> "config_loaded"
    is BillingEvent.ConfigInvalid -> "config_invalid"
    is BillingEvent.ServerLifecycle -> "server_lifecycle"
}

private fun eventParams(event: BillingEvent): Map<String, Any> {
    val p = LinkedHashMap<String, Any?>()
    fun attribution(a: Attribution?) {
        if (a == null) return
        p["placement"] = a.placement
        p["paywall_id"] = a.paywallId
        p["campaign_id"] = a.campaignId
        p["product_id"] = a.productId
        p["base_plan_id"] = a.basePlanId
        p["offer_id"] = a.offerId
        p["attempt_id"] = a.attemptId
        p["recovered"] = a.recovered.toString()
    }
    fun error(e: BillingError) {
        p["error_code"] = e.code
        p["play_response_code"] = e.playResponseCode?.toLong()
    }
    fun paywall(id: String, placement: String, campaignId: String?) {
        p["paywall_id"] = id
        p["placement"] = placement
        p["campaign_id"] = campaignId
    }
    when (event) {
        is BillingEvent.ConnectionStarted, is BillingEvent.ConnectionEstablished, is BillingEvent.RestoreInitiated -> Unit
        is BillingEvent.ConnectionFailed -> error(event.error)
        is BillingEvent.CatalogLoaded -> {
            p["count"] = event.count.toLong()
            p["missing_count"] = event.missing.size.toLong()
            p["missing"] = event.missing.joinToString(",").ifEmpty { null }
        }
        is BillingEvent.CatalogFailed -> error(event.error)
        is BillingEvent.PaywallShown -> paywall(event.paywallId, event.placement, event.campaignId)
        is BillingEvent.PaywallClosed -> {
            paywall(event.paywallId, event.placement, event.campaignId)
            p["reason"] = event.reason.name.lowercase()
            p["seconds_visible"] = event.secondsVisible
        }
        is BillingEvent.PaywallCloseButtonShown -> {
            paywall(event.paywallId, event.placement, event.campaignId)
            p["after_seconds"] = event.afterSeconds
        }
        is BillingEvent.PaywallSuppressed -> {
            paywall(event.paywallId, event.placement, event.campaignId)
            p["reason"] = event.reason
        }
        is BillingEvent.OfferShown -> {
            p["product_id"] = event.productId
            p["base_plan_id"] = event.basePlanId
            p["offer_id"] = event.offerId
            p["paywall_id"] = event.paywallId
            p["placement"] = event.placement
            p["campaign_id"] = event.campaignId
        }
        is BillingEvent.OfferUnavailable -> {
            p["product_id"] = event.productId
            p["base_plan_id"] = event.basePlanId
            p["requested_offer"] = event.requested
            p["fallback_offer_id"] = event.fallbackOfferId
            p["paywall_id"] = event.paywallId
            p["placement"] = event.placement
        }
        is BillingEvent.PurchaseInitiated -> attribution(event.attribution)
        is BillingEvent.PurchaseCancelled -> attribution(event.attribution)
        is BillingEvent.PurchaseFailed -> {
            attribution(event.attribution)
            error(event.error)
        }
        is BillingEvent.PurchasePending -> {
            attribution(event.attribution)
            p["product_ids"] = event.productIds.joinToString(",")
        }
        is BillingEvent.PurchaseVerified -> attribution(event.attribution)
        is BillingEvent.PurchaseCompleted -> {
            attribution(event.attribution)
            p["product_ids"] = event.productIds.joinToString(",")
            p["price_micros"] = event.priceMicros
            p["currency"] = event.currency
        }
        is BillingEvent.EntitlementActivated -> {
            attribution(event.attribution)
            p["product_id"] = event.productId
            p["entitlements"] = event.entitlements.sorted().joinToString(",")
        }
        is BillingEvent.EntitlementLost -> {
            p["entitlement"] = event.entitlement
            p["reason"] = event.reason.name.lowercase()
        }
        is BillingEvent.RestoreCompleted -> {
            p["restored_count"] = event.restored.size.toLong()
            p["restored"] = event.restored.sorted().joinToString(",").ifEmpty { null }
        }
        is BillingEvent.AcknowledgeFailed -> {
            attribution(event.attribution)
            error(event.error)
        }
        is BillingEvent.ConsumeFailed -> {
            attribution(event.attribution)
            error(event.error)
        }
        is BillingEvent.ConfigLoaded -> p["version"] = event.version
        is BillingEvent.ConfigInvalid -> {
            p["key"] = event.key
            p["reason"] = event.reason
        }
        is BillingEvent.ServerLifecycle -> {
            p["kind"] = event.kind.name.lowercase()
            p["product_id"] = event.productId
            p["source"] = event.source.name.lowercase()
        }
    }
    val out = LinkedHashMap<String, Any>()
    for ((key, value) in p) {
        when (value) {
            null -> Unit
            is String -> out[key] = value.take(MAX_VALUE)
            else -> out[key] = value
        }
    }
    return out
}
