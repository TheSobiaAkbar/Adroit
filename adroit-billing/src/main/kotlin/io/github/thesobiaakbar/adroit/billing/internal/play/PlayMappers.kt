package io.github.thesobiaakbar.adroit.billing.internal.play

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.OnPurchasesUpdatedSubResponseCode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.UnfetchedProduct
import io.github.thesobiaakbar.adroit.billing.BillingError
import io.github.thesobiaakbar.adroit.billing.BillingPeriod
import io.github.thesobiaakbar.adroit.billing.OfferInfo
import io.github.thesobiaakbar.adroit.billing.OneTimeOption
import io.github.thesobiaakbar.adroit.billing.Price
import io.github.thesobiaakbar.adroit.billing.PricingPhase
import io.github.thesobiaakbar.adroit.billing.ProductDefinition
import io.github.thesobiaakbar.adroit.billing.ProductInfo
import io.github.thesobiaakbar.adroit.billing.Recurrence
import io.github.thesobiaakbar.adroit.billing.SubscriptionPlan

/**
 * Play's response codes as [PlayResult]s. Sub-response codes win over the response code, whatever it is:
 *
 * | Play                                          | Result                                         |
 * |-----------------------------------------------|------------------------------------------------|
 * | `OK`                                          | [PlayResult.Ok]                                |
 * | `USER_CANCELED`                               | [PlayResult.Cancelled]                         |
 * | `SERVICE_UNAVAILABLE`, `NETWORK_ERROR`        | [BillingError.NetworkError] (retried)          |
 * | `SERVICE_DISCONNECTED`, `SERVICE_TIMEOUT`     | [BillingError.ServiceDisconnected] (retried)   |
 * | `BILLING_UNAVAILABLE`                         | [BillingError.Unavailable]                     |
 * | `FEATURE_NOT_SUPPORTED`                       | [BillingError.FeatureNotSupported]             |
 * | `ITEM_UNAVAILABLE`                            | [BillingError.ProductNotFound] with [productId], else [BillingError.Unknown] |
 * | `ITEM_ALREADY_OWNED`                          | [BillingError.ItemAlreadyOwned]                |
 * | `ITEM_NOT_OWNED`                              | [BillingError.ItemNotOwned]                    |
 * | `DEVELOPER_ERROR`                             | [BillingError.DeveloperError]                  |
 * | `ERROR`, any other code                       | [BillingError.Unknown]                         |
 * | sub `PAYMENT_DECLINED_DUE_TO_INSUFFICIENT_FUNDS` | [BillingError.PaymentDeclined]              |
 * | sub `USER_INELIGIBLE`                         | [BillingError.UserIneligible]                  |
 */
@Suppress("DEPRECATION") // SERVICE_TIMEOUT: deprecated, still mapped for old Play Store versions.
internal fun playOutcome(code: Int, subCode: Int, debugMessage: String?, productId: String? = null): PlayResult<Unit> {
    val message = debugMessage?.takeIf { it.isNotEmpty() }
    when (subCode) {
        OnPurchasesUpdatedSubResponseCode.PAYMENT_DECLINED_DUE_TO_INSUFFICIENT_FUNDS ->
            return PlayResult.Failed(BillingError.PaymentDeclined(code, message))
        OnPurchasesUpdatedSubResponseCode.USER_INELIGIBLE ->
            return PlayResult.Failed(BillingError.UserIneligible(code, message))
    }
    val error = when (code) {
        BillingResponseCode.OK -> return PlayResult.Ok(Unit)
        BillingResponseCode.USER_CANCELED -> return PlayResult.Cancelled
        BillingResponseCode.SERVICE_UNAVAILABLE, BillingResponseCode.NETWORK_ERROR -> BillingError.NetworkError(code, message)
        BillingResponseCode.SERVICE_DISCONNECTED, BillingResponseCode.SERVICE_TIMEOUT ->
            BillingError.ServiceDisconnected(code, message)
        BillingResponseCode.BILLING_UNAVAILABLE -> BillingError.Unavailable(code, message)
        BillingResponseCode.FEATURE_NOT_SUPPORTED -> BillingError.FeatureNotSupported(code, message)
        BillingResponseCode.ITEM_UNAVAILABLE ->
            if (productId != null) BillingError.ProductNotFound(productId) else BillingError.Unknown(code, message)
        BillingResponseCode.ITEM_ALREADY_OWNED -> BillingError.ItemAlreadyOwned(code, message)
        BillingResponseCode.ITEM_NOT_OWNED -> BillingError.ItemNotOwned(code, message)
        BillingResponseCode.DEVELOPER_ERROR -> BillingError.DeveloperError(code, message)
        else -> BillingError.Unknown(code, message)
    }
    return PlayResult.Failed(error)
}

internal fun BillingResult.outcome(productId: String? = null): PlayResult<Unit> =
    playOutcome(responseCode, onPurchasesUpdatedSubResponseCode, debugMessage, productId)

/** Why Play didn't return a product, from `UnfetchedProduct.getStatusCode`. */
internal fun unfetchedReason(statusCode: Int): String = when (statusCode) {
    UnfetchedProduct.StatusCode.INVALID_PRODUCT_ID_FORMAT -> "invalid product ID format"
    UnfetchedProduct.StatusCode.PRODUCT_NOT_FOUND -> "not found in Play Console, or inactive"
    UnfetchedProduct.StatusCode.NO_ELIGIBLE_OFFER -> "no offer this user is eligible for"
    else -> "unknown reason"
} + " (status $statusCode)"

internal fun ProductDetails.toPlayProduct(definition: ProductDefinition): PlayProduct {
    val tokens = mutableMapOf<OfferKey, String>()
    val oneTime = (oneTimePurchaseOfferDetailsList ?: listOfNotNull(oneTimePurchaseOfferDetails)).map { option ->
        option.offerToken?.let { tokens[OfferKey(option.purchaseOptionId, option.offerId)] = it }
        OneTimeOption(
            purchaseOptionId = option.purchaseOptionId,
            offerId = option.offerId,
            offerTags = option.offerTags.orEmpty(),
            price = Price(option.formattedPrice, option.priceAmountMicros, option.priceCurrencyCode),
        )
    }
    val plans = subscriptionOfferDetails.orEmpty().groupBy { it.basePlanId }.map { (basePlanId, offers) ->
        SubscriptionPlan(
            basePlanId = basePlanId,
            offers = offers.map { offer ->
                tokens[OfferKey(basePlanId, offer.offerId)] = offer.offerToken
                OfferInfo(
                    basePlanId = basePlanId,
                    offerId = offer.offerId,
                    offerTags = offer.offerTags.orEmpty(),
                    phases = offer.pricingPhases.pricingPhaseList.map { it.toPricingPhase() },
                )
            },
        )
    }
    val info = ProductInfo(
        definition = definition,
        productId = productId,
        type = definition.defaults.type,
        title = title,
        displayName = name,
        description = description,
        oneTimeOptions = oneTime,
        plans = plans,
    )
    return PlayProduct(info, tokens)
}

private fun ProductDetails.PricingPhase.toPricingPhase() = PricingPhase(
    price = Price(formattedPrice, priceAmountMicros, priceCurrencyCode),
    billingPeriod = billingPeriod,
    period = BillingPeriod.parse(billingPeriod),
    billingCycleCount = billingCycleCount,
    recurrence = when (recurrenceMode) {
        ProductDetails.RecurrenceMode.INFINITE_RECURRING -> Recurrence.INFINITE
        ProductDetails.RecurrenceMode.FINITE_RECURRING -> Recurrence.FINITE
        else -> Recurrence.NON_RECURRING
    },
)

internal fun Purchase.toPlayPurchase() = PlayPurchase(
    products = products,
    purchaseToken = purchaseToken,
    orderId = orderId,
    purchaseTimeMillis = purchaseTime,
    state = when (purchaseState) {
        Purchase.PurchaseState.PURCHASED -> PlayPurchaseState.PURCHASED
        Purchase.PurchaseState.PENDING -> PlayPurchaseState.PENDING
        else -> PlayPurchaseState.UNSPECIFIED
    },
    quantity = quantity,
    isAcknowledged = isAcknowledged,
    isAutoRenewing = isAutoRenewing,
    isSuspended = isSuspended,
    obfuscatedAccountId = accountIdentifiers?.obfuscatedAccountId,
    obfuscatedProfileId = accountIdentifiers?.obfuscatedProfileId,
    originalJson = originalJson,
    signature = signature,
)
