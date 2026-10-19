package io.github.thesobiaakbar.adroit.billing.testing

import android.app.Activity
import io.github.thesobiaakbar.adroit.billing.Attribution
import io.github.thesobiaakbar.adroit.billing.BillingClientApi
import io.github.thesobiaakbar.adroit.billing.BillingConnectionState
import io.github.thesobiaakbar.adroit.billing.CatalogState
import io.github.thesobiaakbar.adroit.billing.ConsumableGrant
import io.github.thesobiaakbar.adroit.billing.EntitlementGrant
import io.github.thesobiaakbar.adroit.billing.EntitlementSnapshot
import io.github.thesobiaakbar.adroit.billing.GrantSource
import io.github.thesobiaakbar.adroit.billing.GrantState
import io.github.thesobiaakbar.adroit.billing.ProductDefinition
import io.github.thesobiaakbar.adroit.billing.ProductInfo
import io.github.thesobiaakbar.adroit.billing.ProductType
import io.github.thesobiaakbar.adroit.billing.PurchaseRequest
import io.github.thesobiaakbar.adroit.billing.PurchaseResult
import io.github.thesobiaakbar.adroit.billing.PurchaseUpdate
import io.github.thesobiaakbar.adroit.billing.RestoreResult
import io.github.thesobiaakbar.adroit.billing.SnapshotStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * A [BillingClientApi] for host tests: no Google Play, no device and no Robolectric, so ViewModel and repository
 * tests run on the plain JVM. Inject it where the app injects `BillingClientApi`.
 *
 * Set [entitlements], [catalog] and [connection] directly, or use [own]. A purchase returns [nextPurchaseResult]
 * (once), or else succeeds; like the library, `Purchased` and `AlreadyOwned` add the product's entitlements and
 * `Pending` grants nothing. Assert on [purchaseRequests]:
 *
 * ```
 * // Dispatchers.Main is an UnconfinedTestDispatcher, as in any ViewModel test.
 * class PremiumViewModelTest {
 *     private val billing = FakeBilling()
 *
 *     @Test fun `buying from settings unlocks premium`() = runTest {
 *         val vm = PremiumViewModel(billing)
 *         assertFalse(vm.isPremium.value)
 *
 *         vm.buy(Activity())
 *
 *         assertTrue(vm.isPremium.value)
 *         assertEquals("settings", billing.purchaseRequests.single().placement)
 *     }
 *
 *     @Test fun `a pending payment unlocks nothing`() = runTest {
 *         billing.nextPurchaseResult = PurchaseResult.Pending(Attribution.unknown("premium_sub"))
 *         PremiumViewModel(billing).buy(Activity())
 *         assertFalse(billing.entitlements.value.has("premium"))
 *     }
 * }
 * ```
 *
 * **Differs from production:** nothing runs on the main thread or talks to Play; a purchase completes inside the
 * call unless held with [holdNextPurchase]; [purchaseUpdates] carries only what [emitPurchaseUpdate] sends; a
 * consumable is delivered once, whether or not it is confirmed. `Activity()` works as the purchase argument on the
 * plain JVM; the fake never touches it.
 *
 * @param products the catalog, as [CatalogState.Ready] (build them with [TestProducts]).
 */
public class FakeBilling(products: List<ProductInfo> = emptyList()) : BillingClientApi {
    private val lock = Any()
    private val requests = mutableListOf<PurchaseRequest>()
    private val confirmed = mutableListOf<ConsumableGrant>()
    private val held = ArrayDeque<CompletableDeferred<PurchaseResult>>()
    private val updates = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val consumableChannel = Channel<ConsumableGrant>(Channel.UNLIMITED)
    private var orders = 0

    /** What the user owns. Starts confirmed and empty: a free user. */
    override val entitlements: MutableStateFlow<EntitlementSnapshot> =
        MutableStateFlow(EntitlementSnapshot(emptyList(), SnapshotStatus.Fresh(0), emptySet()))

    /** The connection. Starts [BillingConnectionState.Connected]. */
    override val connection: MutableStateFlow<BillingConnectionState> = MutableStateFlow(BillingConnectionState.Connected)

    /** The catalog. Starts ready with the constructor's products. */
    override val catalog: MutableStateFlow<CatalogState> =
        MutableStateFlow(CatalogState.Ready(products.associateBy { it.definition }, emptySet()))

    override val purchaseUpdates: SharedFlow<PurchaseUpdate> = updates.asSharedFlow()

    override val consumables: Flow<ConsumableGrant> = consumableChannel.receiveAsFlow()

    /** The result of the next [purchase], used once. Null (the default) means the purchase succeeds. */
    @Volatile
    public var nextPurchaseResult: PurchaseResult? = null

    /** What [restore] returns. */
    @Volatile
    public var restoreResult: RestoreResult = RestoreResult.NothingToRestore

    /** What [openManageSubscriptions] returns. */
    @Volatile
    public var manageSubscriptionsResult: Boolean = true

    /** The last value passed to [setAccount]. */
    @Volatile
    public var accountId: String? = null
        private set

    /** Every purchase request so far, oldest first, so tests can assert products and placements. */
    public val purchaseRequests: List<PurchaseRequest> get() = synchronized(lock) { requests.toList() }

    /** The consumables the app confirmed with [ConsumableGrant.confirm], oldest first. */
    public val confirmedConsumables: List<ConsumableGrant> get() = synchronized(lock) { confirmed.toList() }

    /** Makes the user own [products] now, replacing what they owned, as a confirmed snapshot. */
    public fun own(vararg products: ProductDefinition) {
        entitlements.value = snapshot(products.map { grant(it, GrantState.ACTIVE) })
    }

    /**
     * Makes the next [purchase] wait for the returned deferred, for example to assert a "purchasing" state. Its
     * result then applies as [nextPurchaseResult] would.
     */
    public fun holdNextPurchase(): CompletableDeferred<PurchaseResult> =
        CompletableDeferred<PurchaseResult>().also { synchronized(lock) { held.addLast(it) } }

    /** Sends [update] to [purchaseUpdates]. Entitlements don't change; set them if the test needs it. */
    public fun emitPurchaseUpdate(update: PurchaseUpdate) {
        updates.tryEmit(update)
    }

    /** Delivers a bought consumable on [consumables], as after a purchase of [product]. */
    public fun emitConsumable(product: ProductDefinition, quantity: Int = 1, placement: String = "test") {
        val id = product.defaults.productId
        val orderIdHash = synchronized(lock) { "order-${++orders}" }
        val attribution = Attribution(null, placement, null, null, id, null, null, recovered = false)
        lateinit var grant: ConsumableGrant
        grant = ConsumableGrant(id, quantity, attribution, orderIdHash) {
            synchronized(lock) { if (grant !in confirmed) confirmed += grant }
        }
        consumableChannel.trySend(grant)
    }

    override suspend fun purchase(activity: Activity, request: PurchaseRequest): PurchaseResult {
        val wait = synchronized(lock) {
            requests += request
            held.removeFirstOrNull()
        }
        val result = wait?.await() ?: nextPurchaseResult.also { nextPurchaseResult = null } ?: success(request)
        apply(request, result)
        return result
    }

    private fun success(request: PurchaseRequest): PurchaseResult.Purchased {
        val attribution = Attribution(
            "attempt-${purchaseRequests.size}", request.placement, request.paywallId, request.campaignId,
            request.product.defaults.productId, request.basePlanId, null, recovered = false,
        )
        val consumable = request.product.defaults.type == ProductType.CONSUMABLE
        return PurchaseResult.Purchased(if (consumable) null else grant(request.product, GrantState.ACTIVE), attribution)
    }

    private fun apply(request: PurchaseRequest, result: PurchaseResult) {
        val product = request.product
        when {
            result is PurchaseResult.Purchased && product.defaults.type == ProductType.CONSUMABLE ->
                emitConsumable(product, placement = request.placement)
            result is PurchaseResult.Purchased -> add(result.grant ?: grant(product, GrantState.ACTIVE))
            result is PurchaseResult.AlreadyOwned -> add(grant(product, GrantState.ACTIVE))
            result is PurchaseResult.Pending -> add(grant(product, GrantState.PENDING))
        }
    }

    private fun add(grant: EntitlementGrant) {
        val current = entitlements.value.grants.filter { it.productId != grant.productId }
        entitlements.value = snapshot(current + grant)
    }

    private fun snapshot(grants: List<EntitlementGrant>) =
        EntitlementSnapshot(grants, SnapshotStatus.Fresh(0), entitlements.value.adFreeEntitlements)

    private fun grant(product: ProductDefinition, state: GrantState) = EntitlementGrant(
        product.defaults.productId, product.defaults.type, product.defaults.entitlements, state, GrantSource.CLIENT, 0,
    )

    override suspend fun restore(): RestoreResult = restoreResult

    override suspend fun refresh() {}

    override fun openManageSubscriptions(activity: Activity, product: ProductDefinition?): Boolean = manageSubscriptionsResult

    override fun setAccount(accountId: String?) {
        this.accountId = accountId
    }
}
