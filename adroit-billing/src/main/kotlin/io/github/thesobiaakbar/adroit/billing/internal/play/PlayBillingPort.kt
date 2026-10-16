package io.github.thesobiaakbar.adroit.billing.internal.play

import android.app.Activity
import io.github.thesobiaakbar.adroit.billing.BillingError
import io.github.thesobiaakbar.adroit.billing.ProductDefinition
import io.github.thesobiaakbar.adroit.billing.ProductInfo
import io.github.thesobiaakbar.adroit.billing.ProductType
import io.github.thesobiaakbar.adroit.billing.internal.tokenHash
import kotlinx.coroutines.flow.Flow

/**
 * The library's only view of Google Play Billing (06_BILLING_ARCHITECTURE §2). Parameters and results are Play-free,
 * so the runtime runs in JVM tests against a fake. [PlayBillingAdapter] is the production implementation.
 *
 * Calls other than [connect] expect a connected client; [ConnectionManager.call] makes sure of that. Suspend calls
 * resume on the caller's dispatcher, and a callback arriving after cancellation is dropped.
 */
internal interface PlayBillingPort {
    /** Purchases from the purchase flow and from outside the app (pending purchases completing, Play Store buys). */
    val purchasesUpdates: Flow<PlayPurchasesUpdate>

    /** Connects once. [onDisconnected] runs each time the service later disconnects. */
    suspend fun connect(onDisconnected: () -> Unit): PlayResult<Unit>

    /** Play's details of [products], all of [type]. Products Play doesn't return are in [PlayProducts.unfetched]. */
    suspend fun queryProducts(type: PlayProductType, products: List<ProductDefinition>): PlayResult<PlayProducts>

    /** The user's current purchases of [type]; suspended subscriptions only with [includeSuspended]. */
    suspend fun queryPurchases(type: PlayProductType, includeSuspended: Boolean): PlayResult<List<PlayPurchase>>

    /** Opens Play's purchase sheet. The outcome arrives on [purchasesUpdates]. [activity] isn't kept. */
    suspend fun launch(activity: Activity, params: LaunchParams): PlayResult<Unit>

    suspend fun acknowledge(purchaseToken: String): PlayResult<Unit>

    suspend fun consume(purchaseToken: String): PlayResult<Unit>

    /** Ok when the Play Store supports [feature], else [BillingError.FeatureNotSupported]. */
    fun isFeatureSupported(feature: PlayFeature): PlayResult<Unit>

    /** Shows Play's transactional messages (for example a declined renewal). Ok(token) when a subscription changed. */
    suspend fun showInAppMessages(activity: Activity): PlayResult<String?>

    /** Ends the connection. Only for tests; the client otherwise lives as long as the process. */
    fun endConnection()
}

/** A Play call's outcome. A cancelled purchase sheet is [Cancelled], not an error. */
internal sealed interface PlayResult<out T> {
    data class Ok<out T>(val value: T) : PlayResult<T>

    data object Cancelled : PlayResult<Nothing>

    data class Failed(val error: BillingError) : PlayResult<Nothing>
}

internal inline fun <T, R> PlayResult<T>.map(transform: (T) -> R): PlayResult<R> = when (this) {
    is PlayResult.Ok -> PlayResult.Ok(transform(value))
    is PlayResult.Cancelled -> this
    is PlayResult.Failed -> this
}

/** Play's two product types: one-time products are `INAPP`, whether consumable or not. */
internal enum class PlayProductType { INAPP, SUBS }

internal val ProductType.playType: PlayProductType
    get() = if (this == ProductType.SUBSCRIPTION) PlayProductType.SUBS else PlayProductType.INAPP

internal enum class PlayFeature { SUBSCRIPTIONS, SUBSCRIPTIONS_UPDATE, IN_APP_MESSAGING, INCLUDE_SUSPENDED_SUBSCRIPTIONS }

/** The found products, and the declared products Play didn't return: product ID to Play's reason. */
internal data class PlayProducts(val products: List<PlayProduct>, val unfetched: Map<String, String>)

/**
 * A product with the offer tokens its offers are bought with, which never leave the library. Keys are
 * [OfferKey]s: base plan and offer for a subscription, purchase option and offer for a one-time product.
 */
internal data class PlayProduct(val info: ProductInfo, val offerTokens: Map<OfferKey, String>)

internal data class OfferKey(val planOrOptionId: String?, val offerId: String?)

/** What to buy: one offer, by its token from [PlayProduct.offerTokens]. */
internal data class LaunchParams(
    val productId: String,
    val offerToken: String,
    val obfuscatedAccountId: String? = null,
    val obfuscatedProfileId: String? = null,
)

/** One purchase from Play. [toString] shows a hash of the token, never the token. */
internal data class PlayPurchase(
    val products: List<String>,
    val purchaseToken: String,
    val orderId: String?,
    val purchaseTimeMillis: Long,
    val state: PlayPurchaseState,
    val quantity: Int,
    val isAcknowledged: Boolean,
    val isAutoRenewing: Boolean,
    val isSuspended: Boolean,
    val obfuscatedAccountId: String?,
    val obfuscatedProfileId: String?,
    val originalJson: String,
    val signature: String,
) {
    override fun toString(): String =
        "PlayPurchase(products=$products, token#=${tokenHash(purchaseToken)}, state=$state, " +
            "acknowledged=$isAcknowledged, autoRenewing=$isAutoRenewing, suspended=$isSuspended)"
}

internal enum class PlayPurchaseState { PURCHASED, PENDING, UNSPECIFIED }

/** A purchase flow's or Play's own report of purchases. */
internal sealed interface PlayPurchasesUpdate {
    data class Purchased(val purchases: List<PlayPurchase>) : PlayPurchasesUpdate

    data object Cancelled : PlayPurchasesUpdate

    data class Failed(val error: BillingError) : PlayPurchasesUpdate
}
