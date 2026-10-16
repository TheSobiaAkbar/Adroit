package io.github.thesobiaakbar.adroit.billing

import android.app.Activity
import androidx.annotation.MainThread
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The Adroit Billing API. Use [AdroitBilling] directly, or inject `BillingClientApi` (bound to [AdroitBilling]) so
 * tests can substitute `FakeBilling` from `adroit-billing-testing`.
 *
 * Threading: flows emit on the main thread. No function throws because of a billing failure; failures are reported
 * through results. Only [purchase] and [openManageSubscriptions] need an Activity, used during the call and never
 * kept; everything else can be called from a ViewModel or any other code.
 *
 * Stability: members may be added in minor versions, always with a default implementation.
 */
public interface BillingClientApi {
    /** What the user owns now. Available synchronously from `initialize` on (saved from the last launch). */
    public val entitlements: StateFlow<EntitlementSnapshot>

    /** The connection to Google Play Billing. */
    public val connection: StateFlow<BillingConnectionState>

    /** The app's products as Play describes them to this user. */
    public val catalog: StateFlow<CatalogState>

    /** [definition] as Play describes it now, or null when the catalog isn't loaded or Play didn't return it. */
    public fun product(definition: ProductDefinition): ProductInfo? =
        (catalog.value as? CatalogState.Ready)?.products?.get(definition)

    /**
     * [definition] as Play describes it, waiting up to [timeout] for the first catalog load. Null when the load fails
     * or takes longer, or when Play didn't return the product.
     */
    public suspend fun awaitProduct(definition: ProductDefinition, timeout: Duration = 5.seconds): ProductInfo? {
        withTimeoutOrNull(timeout) { catalog.first { it !is CatalogState.Loading } }
        return product(definition)
    }

    /** Purchase changes seen outside a waiting [purchase] call: completed pending payments, suspensions and more. */
    public val purchaseUpdates: SharedFlow<PurchaseUpdate>

    /** Bought consumables waiting for the app to credit them and call [ConsumableGrant.confirm]. */
    public val consumables: Flow<ConsumableGrant>

    /**
     * Starts the Play purchase screen for [request] from [activity] and suspends until the purchase is processed,
     * cancelled or failed. Only one purchase runs at a time. If the caller's scope is cancelled first (for example
     * the screen closed), the outcome still arrives on [purchaseUpdates].
     *
     * Call it from a ViewModel's scope rather than `lifecycleScope`: a rotation while Play's sheet is open recreates
     * the Activity and cancels `lifecycleScope`, so the result would then arrive only on [purchaseUpdates].
     */
    @MainThread
    public suspend fun purchase(activity: Activity, request: PurchaseRequest): PurchaseResult

    /** Reads the user's purchases from Play again and reports what was restored. */
    public suspend fun restore(): RestoreResult

    /** Queries products and purchases again, for example after the user says they bought something elsewhere. */
    public suspend fun refresh()

    /**
     * Opens Google Play's subscription management, for [product] or for all of the app's subscriptions. Returns false
     * when no app can open it.
     */
    @MainThread
    public fun openManageSubscriptions(activity: Activity, product: ProductDefinition? = null): Boolean

    /**
     * Sets the app's own account ID of the signed-in user (null when signed out). Only a hash leaves the device.
     * Switching from one account to another clears the saved entitlements and reads them again. Google Play
     * purchases belong to the Google account; binding them to an app account needs the app's backend.
     */
    public fun setAccount(accountId: String?)
}
