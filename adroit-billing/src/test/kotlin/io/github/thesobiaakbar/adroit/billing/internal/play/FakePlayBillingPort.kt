package io.github.thesobiaakbar.adroit.billing.internal.play

import android.app.Activity
import io.github.thesobiaakbar.adroit.billing.BillingError
import io.github.thesobiaakbar.adroit.billing.ProductDefinition
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** An in-memory [PlayBillingPort]. Moves to `adroit-billing-testing` in B19. */
internal class FakePlayBillingPort : PlayBillingPort {
    /** Results of the next [connect] calls, in order; [PlayResult.Ok] once empty. */
    val connectResults = ArrayDeque<PlayResult<Unit>>()

    /** While set, [connect] waits for it: a Play service that never answers. */
    var connectGate: CompletableDeferred<Unit>? = null

    var connectCalls = 0
        private set

    var endConnectionCalls = 0
        private set

    /** Results of the next [queryPurchases] calls, in order; an empty list once empty. */
    val purchasesResults = ArrayDeque<PlayResult<List<PlayPurchase>>>()

    var queryPurchasesCalls = 0
        private set

    /** What Play returns per product ID; a declared ID not in here is unfetched. */
    val products = mutableMapOf<String, PlayProduct>()

    /** Results of the next [queryProducts] calls, in order, instead of [products]. */
    val productsResults = ArrayDeque<PlayResult<PlayProducts>>()

    /** While set, [queryProducts] waits for it. */
    var productsGate: CompletableDeferred<Unit>? = null

    var queryProductsCalls = 0
        private set

    private var onDisconnected: () -> Unit = {}
    private val updates = Channel<PlayPurchasesUpdate>(Channel.UNLIMITED)

    override val purchasesUpdates: Flow<PlayPurchasesUpdate> = updates.receiveAsFlow()

    /** Simulates Play's `onBillingServiceDisconnected`. */
    fun disconnect() = onDisconnected()

    fun emit(update: PlayPurchasesUpdate) {
        updates.trySend(update)
    }

    override suspend fun connect(onDisconnected: () -> Unit): PlayResult<Unit> {
        connectCalls++
        this.onDisconnected = onDisconnected
        connectGate?.await()
        return connectResults.removeFirstOrNull() ?: PlayResult.Ok(Unit)
    }

    override suspend fun queryProducts(type: PlayProductType, products: List<ProductDefinition>): PlayResult<PlayProducts> {
        queryProductsCalls++
        productsGate?.await()
        productsResults.removeFirstOrNull()?.let { return it }
        val ids = products.map { it.defaults.productId }
        return PlayResult.Ok(
            PlayProducts(
                ids.mapNotNull { this.products[it] },
                ids.filter { it !in this.products }.associateWith { unfetchedReason(3) },
            ),
        )
    }

    override suspend fun queryPurchases(type: PlayProductType, includeSuspended: Boolean): PlayResult<List<PlayPurchase>> {
        queryPurchasesCalls++
        return purchasesResults.removeFirstOrNull() ?: PlayResult.Ok(emptyList())
    }

    override suspend fun launch(activity: Activity, params: LaunchParams): PlayResult<Unit> = PlayResult.Ok(Unit)

    override suspend fun acknowledge(purchaseToken: String): PlayResult<Unit> = PlayResult.Ok(Unit)

    override suspend fun consume(purchaseToken: String): PlayResult<Unit> = PlayResult.Ok(Unit)

    override fun isFeatureSupported(feature: PlayFeature): PlayResult<Unit> =
        PlayResult.Failed(BillingError.FeatureNotSupported())

    override suspend fun showInAppMessages(activity: Activity): PlayResult<String?> = PlayResult.Ok(null)

    override fun endConnection() {
        endConnectionCalls++
    }
}
