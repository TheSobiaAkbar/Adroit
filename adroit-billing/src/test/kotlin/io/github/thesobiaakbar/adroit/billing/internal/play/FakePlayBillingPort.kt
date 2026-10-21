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

    /** Results of the next [queryPurchases] calls, in order; [owned] once empty. */
    val purchasesResults = ArrayDeque<PlayResult<List<PlayPurchase>>>()

    /** The user's purchases per type, returned by [queryPurchases]. */
    val owned = mutableMapOf<PlayProductType, List<PlayPurchase>>()

    /** The `includeSuspended` of each [queryPurchases] call. */
    val includeSuspendedArgs = mutableListOf<Boolean>()

    /** Whether [isFeatureSupported] says yes. */
    var featuresSupported = false

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

    /** Results of the next [launch] calls, in order; [PlayResult.Ok] once empty. */
    val launchResults = ArrayDeque<PlayResult<Unit>>()

    val launches = mutableListOf<LaunchParams>()

    /** Runs inside each [launch], before it returns. */
    var onLaunch: () -> Unit = {}

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
        includeSuspendedArgs += includeSuspended
        return purchasesResults.removeFirstOrNull() ?: PlayResult.Ok(owned[type].orEmpty())
    }

    override suspend fun launch(activity: Activity, params: LaunchParams): PlayResult<Unit> {
        launches += params
        onLaunch()
        return launchResults.removeFirstOrNull() ?: PlayResult.Ok(Unit)
    }

    /** Results of the next [acknowledge] calls, in order; [PlayResult.Ok] once empty. */
    val acknowledgeResults = ArrayDeque<PlayResult<Unit>>()

    /** Tokens passed to [acknowledge], one per call. */
    val acknowledged = mutableListOf<String>()

    /** While set, [acknowledge] waits for it. */
    var acknowledgeGate: CompletableDeferred<Unit>? = null

    /** Results of the next [consume] calls, in order; [PlayResult.Ok] once empty. */
    val consumeResults = ArrayDeque<PlayResult<Unit>>()

    /** Tokens passed to [consume], one per call. */
    val consumed = mutableListOf<String>()

    override suspend fun acknowledge(purchaseToken: String): PlayResult<Unit> {
        acknowledged += purchaseToken
        acknowledgeGate?.await()
        return acknowledgeResults.removeFirstOrNull() ?: PlayResult.Ok(Unit)
    }

    override suspend fun consume(purchaseToken: String): PlayResult<Unit> {
        consumed += purchaseToken
        return consumeResults.removeFirstOrNull() ?: PlayResult.Ok(Unit)
    }

    override fun isFeatureSupported(feature: PlayFeature): PlayResult<Unit> =
        if (featuresSupported) PlayResult.Ok(Unit) else PlayResult.Failed(BillingError.FeatureNotSupported())

    /** What [showInAppMessages] returns: Ok(token) when a message changed a subscription. */
    var inAppMessageResult: PlayResult<String?> = PlayResult.Ok(null)

    var inAppMessageCalls = 0
        private set

    /** When set, [showInAppMessages] waits for it, as Play does until it has an answer. */
    var inAppMessageAnswer: kotlinx.coroutines.CompletableDeferred<PlayResult<String?>>? = null

    override suspend fun showInAppMessages(activity: Activity): PlayResult<String?> {
        inAppMessageCalls++
        inAppMessageAnswer?.let { return it.await() }
        return inAppMessageResult
    }

    override fun endConnection() {
        endConnectionCalls++
    }
}
