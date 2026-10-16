package io.github.thesobiaakbar.adroit.billing.internal.play

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams.SubscriptionProductReplacementParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.InAppMessageParams
import com.android.billingclient.api.InAppMessageResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import io.github.thesobiaakbar.adroit.billing.BillingError
import io.github.thesobiaakbar.adroit.billing.ProductDefinition
import io.github.thesobiaakbar.adroit.billing.ReplacementMode
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * [PlayBillingPort] over Play Billing Library, the only production implementation and the only code that touches
 * `com.android.billingclient`. It owns the process's single [BillingClient], built from the application context.
 *
 * Every Play call is wrapped, so a Play exception becomes a [BillingError] instead of a crash.
 */
internal class PlayBillingAdapter(appContext: Context) : PlayBillingPort {
    /** Unbounded, so no purchase is lost before the runtime collects; there is a single collector. */
    private val updates = Channel<PlayPurchasesUpdate>(Channel.UNLIMITED)

    /** The last details per product ID: launching a purchase needs Play's own object. */
    private val details = ConcurrentHashMap<String, ProductDetails>()

    private val client: BillingClient = BillingClient.newBuilder(appContext.applicationContext)
        .setListener { result, purchases ->
            val update = when (val outcome = result.outcome()) {
                is PlayResult.Ok -> PlayPurchasesUpdate.Purchased(purchases.orEmpty().map { it.toPlayPurchase() })
                is PlayResult.Cancelled -> PlayPurchasesUpdate.Cancelled
                is PlayResult.Failed -> PlayPurchasesUpdate.Failed(outcome.error)
            }
            updates.trySend(update)
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().enablePrepaidPlans().build())
        .enableAutoServiceReconnection()
        .build()

    override val purchasesUpdates: Flow<PlayPurchasesUpdate> = updates.receiveAsFlow()

    override suspend fun connect(onDisconnected: () -> Unit): PlayResult<Unit> = play { continuation ->
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) = continuation.resumeOnce(result.outcome())

            override fun onBillingServiceDisconnected() = onDisconnected()
        })
    }

    override suspend fun queryProducts(type: PlayProductType, products: List<ProductDefinition>): PlayResult<PlayProducts> {
        val byId = products.associateBy { it.defaults.productId }
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            byId.keys.map { QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(type.play).build() },
        ).build()
        return play { continuation ->
            client.queryProductDetailsAsync(params) { result, found ->
                continuation.resumeOnce(
                    result.outcome().map {
                        val playProducts = found.productDetailsList.mapNotNull { product ->
                            details[product.productId] = product
                            byId[product.productId]?.let { product.toPlayProduct(it) }
                        }
                        val reasons = found.unfetchedProductList.associate { it.productId to unfetchedReason(it.statusCode) }
                        val returned = playProducts.map { it.info.productId }.toSet()
                        PlayProducts(playProducts, (byId.keys - returned).associateWith { reasons[it] ?: "not returned" })
                    },
                )
            }
        }
    }

    override suspend fun queryPurchases(type: PlayProductType, includeSuspended: Boolean): PlayResult<List<PlayPurchase>> {
        val params = QueryPurchasesParams.newBuilder().setProductType(type.play)
            .includeSuspendedSubscriptions(includeSuspended).build()
        return play { continuation ->
            client.queryPurchasesAsync(params) { result, purchases ->
                continuation.resumeOnce(result.outcome().map { purchases.map { it.toPlayPurchase() } })
            }
        }
    }

    override suspend fun launch(activity: Activity, params: LaunchParams): PlayResult<Unit> = guard {
        val product = details[params.productId]
            ?: return@guard PlayResult.Failed(BillingError.ProductNotFound(params.productId))
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .setOfferToken(params.offerToken)
        val flow = BillingFlowParams.newBuilder()
        params.replacement?.let {
            // The mode goes on the product (PBL 8.1+); SubscriptionUpdateParams' own mode setter is deprecated.
            productParams.setSubscriptionProductReplacementParams(
                SubscriptionProductReplacementParams.newBuilder()
                    .setOldProductId(it.oldProductId)
                    .setReplacementMode(it.mode.play)
                    .build(),
            )
            flow.setSubscriptionUpdateParams(
                BillingFlowParams.SubscriptionUpdateParams.newBuilder().setOldPurchaseToken(it.oldPurchaseToken).build(),
            )
        }
        flow.setProductDetailsParamsList(listOf(productParams.build()))
        params.obfuscatedAccountId?.let { flow.setObfuscatedAccountId(it) }
        params.obfuscatedProfileId?.let { flow.setObfuscatedProfileId(it) }
        client.launchBillingFlow(activity, flow.build()).outcome(params.productId)
    }

    override suspend fun acknowledge(purchaseToken: String): PlayResult<Unit> = play { continuation ->
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseToken).build()
        client.acknowledgePurchase(params) { continuation.resumeOnce(it.outcome()) }
    }

    override suspend fun consume(purchaseToken: String): PlayResult<Unit> = play { continuation ->
        val params = ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build()
        client.consumeAsync(params) { result, _ -> continuation.resumeOnce(result.outcome()) }
    }

    override fun isFeatureSupported(feature: PlayFeature): PlayResult<Unit> = guard {
        val name = when (feature) {
            PlayFeature.SUBSCRIPTIONS -> BillingClient.FeatureType.SUBSCRIPTIONS
            PlayFeature.SUBSCRIPTIONS_UPDATE -> BillingClient.FeatureType.SUBSCRIPTIONS_UPDATE
            PlayFeature.IN_APP_MESSAGING -> BillingClient.FeatureType.IN_APP_MESSAGING
            PlayFeature.INCLUDE_SUSPENDED_SUBSCRIPTIONS -> BillingClient.FeatureType.INCLUDE_SUSPENDED_SUBSCRIPTIONS
        }
        client.isFeatureSupported(name).outcome()
    }

    override suspend fun showInAppMessages(activity: Activity): PlayResult<String?> = play { continuation ->
        val params = InAppMessageParams.newBuilder()
            .addInAppMessageCategoryToShow(InAppMessageParams.InAppMessageCategoryId.TRANSACTIONAL).build()
        val shown = client.showInAppMessages(activity, params) { result ->
            val updated = result.responseCode == InAppMessageResult.InAppMessageResponseCode.SUBSCRIPTION_STATUS_UPDATED
            continuation.resumeOnce(PlayResult.Ok(result.purchaseToken.takeIf { updated }))
        }
        (shown.outcome() as? PlayResult.Failed)?.let { continuation.resumeOnce(it) }
    }

    override fun endConnection() {
        runCatching { client.endConnection() }
    }

    private val ReplacementMode.play: Int
        get() = when (this) {
            ReplacementMode.WITH_TIME_PRORATION -> SubscriptionProductReplacementParams.ReplacementMode.WITH_TIME_PRORATION
            ReplacementMode.CHARGE_PRORATED_PRICE -> SubscriptionProductReplacementParams.ReplacementMode.CHARGE_PRORATED_PRICE
            ReplacementMode.CHARGE_FULL_PRICE -> SubscriptionProductReplacementParams.ReplacementMode.CHARGE_FULL_PRICE
            ReplacementMode.WITHOUT_PRORATION -> SubscriptionProductReplacementParams.ReplacementMode.WITHOUT_PRORATION
            ReplacementMode.DEFERRED -> SubscriptionProductReplacementParams.ReplacementMode.DEFERRED
            ReplacementMode.KEEP_EXISTING -> SubscriptionProductReplacementParams.ReplacementMode.KEEP_EXISTING
        }

    private val PlayProductType.play: String
        get() = when (this) {
            PlayProductType.INAPP -> BillingClient.ProductType.INAPP
            PlayProductType.SUBS -> BillingClient.ProductType.SUBS
        }

    /** Runs a callback-style Play call. A callback after cancellation, or a second one, is ignored. */
    private suspend fun <T> play(start: (CancellableContinuation<PlayResult<T>>) -> Unit): PlayResult<T> =
        suspendCancellableCoroutine { continuation ->
            try {
                start(continuation)
            } catch (e: RuntimeException) {
                continuation.resumeOnce(PlayResult.Failed(BillingError.Unknown(debugMessage = e.toString())))
            }
        }

    private fun <T> CancellableContinuation<T>.resumeOnce(value: T) {
        if (isActive) resume(value)
    }

    private inline fun <T> guard(block: () -> PlayResult<T>): PlayResult<T> = try {
        block()
    } catch (e: RuntimeException) {
        PlayResult.Failed(BillingError.Unknown(debugMessage = e.toString()))
    }
}
