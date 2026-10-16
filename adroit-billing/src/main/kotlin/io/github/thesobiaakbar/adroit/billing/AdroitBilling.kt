package io.github.thesobiaakbar.adroit.billing

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.content.pm.ApplicationInfo
import androidx.annotation.VisibleForTesting
import io.github.thesobiaakbar.adroit.billing.internal.BillingLog
import io.github.thesobiaakbar.adroit.billing.internal.BillingRuntime
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayBillingAdapter
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayBillingPort
import io.github.thesobiaakbar.adroit.billing.internal.validate
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * The process-wide entry point to Adroit Billing.
 *
 * Call [initialize] in `Application.onCreate()`; everything else is described on [BillingClientApi]. For dependency
 * injection, bind [BillingClientApi] to this object.
 *
 * [entitlements], [connection], [catalog] and [purchaseUpdates] are the same instances before and after
 * initialization, so they can be collected at any time.
 */
public object AdroitBilling : BillingClientApi {
    internal val log: BillingLog = BillingLog(BillingLogLevel.WARN)

    internal val sharedEntitlements: MutableStateFlow<EntitlementSnapshot> =
        MutableStateFlow(EntitlementSnapshot.EMPTY_UNKNOWN)

    internal val sharedConnection: MutableStateFlow<BillingConnectionState> =
        MutableStateFlow(BillingConnectionState.Disconnected)

    internal val sharedCatalog: MutableStateFlow<CatalogState> = MutableStateFlow(CatalogState.Loading)

    internal val sharedPurchaseUpdates: MutableSharedFlow<PurchaseUpdate> =
        MutableSharedFlow(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Bought consumables the app hasn't confirmed yet. */
    internal val sharedConsumables: MutableStateFlow<List<ConsumableGrant>> = MutableStateFlow(emptyList())

    /** The settings passed to the first valid [initialize], or null before it. */
    @Volatile
    internal var settings: BillingSettings? = null
        private set

    /** The last value passed to [setAccount], so an early call is applied once the runtime starts. */
    @Volatile
    internal var requestedAccount: String? = null

    /** The running library, from the first valid [initialize] on. */
    @Volatile
    internal var runtime: BillingRuntime? = null
        private set

    /** Builds the Play port; tests replace it with a fake. */
    @VisibleForTesting
    internal var portFactory: (Application) -> PlayBillingPort = ::PlayBillingAdapter

    private val lock = Any()

    /**
     * Sets up the library. Call it once, in `Application.onCreate()`. Cheap and synchronous: it checks [settings] and
     * reads the saved entitlements, so [entitlements] holds them when it returns; Play is contacted in the background.
     * Callable from any thread.
     *
     * A later call with equal settings does nothing. One with different settings throws [IllegalStateException] in a
     * debuggable app; in a release app it is logged and the first settings are kept.
     *
     * Invalid settings (for example a paywall using an undeclared product) throw [IllegalStateException] listing every
     * problem in a debuggable app. In a release app they are logged, and billing stays off (every call reports
     * [BillingError.NotInitialized]) while the app keeps running.
     */
    @JvmStatic
    public fun initialize(application: Application, settings: BillingSettings) {
        synchronized(lock) {
            this.settings?.let {
                if (it == settings) return
                val message = "initialize called again with different settings; kept the first"
                check(!application.isDebuggable()) { message }
                log.log(BillingLogLevel.ERROR, message)
                return
            }
            log.minLevel = settings.logLevel
            val problems = validate(settings)
            if (problems.isNotEmpty()) {
                val message = "Invalid BillingSettings:\n" + problems.joinToString("\n") { "  - $it" }
                check(!application.isDebuggable()) { message }
                log.log(BillingLogLevel.ERROR, "$message\nBilling stays off.")
                return
            }
            this.settings = settings
            runtime = BillingRuntime(
                application, portFactory(application), settings, sharedConnection, sharedCatalog, sharedPurchaseUpdates,
                sharedConsumables, sharedEntitlements, log,
                account = { requestedAccount }, debuggable = application.isDebuggable(),
            ).also { it.start() }
        }
    }

    override val entitlements: StateFlow<EntitlementSnapshot> = sharedEntitlements.asStateFlow()

    override val connection: StateFlow<BillingConnectionState> = sharedConnection.asStateFlow()

    override val catalog: StateFlow<CatalogState> = sharedCatalog.asStateFlow()

    override val purchaseUpdates: SharedFlow<PurchaseUpdate> = sharedPurchaseUpdates.asSharedFlow()

    /** Each collector gets every unconfirmed grant once, the ones already waiting first. */
    override val consumables: Flow<ConsumableGrant> = flow {
        val delivered = HashSet<ConsumableGrant>()
        sharedConsumables.collect { grants -> grants.filter(delivered::add).forEach { emit(it) } }
    }

    override suspend fun purchase(activity: Activity, request: PurchaseRequest): PurchaseResult {
        val runtime = runtime ?: return PurchaseResult.Failed(BillingError.NotInitialized, attribution = null)
            .also { notReady("purchase") }
        return runtime.purchases.purchase(activity, request)
    }

    override suspend fun restore(): RestoreResult {
        val runtime = runtime ?: return RestoreResult.Failed(BillingError.NotInitialized).also { notReady("restore") }
        return runtime.entitlements.restore()
    }

    override suspend fun refresh() {
        val runtime = runtime ?: return notReady("refresh")
        runtime.connection.trigger()
        runtime.catalog.load()
        runtime.entitlements.sync()
    }

    override fun openManageSubscriptions(activity: Activity, product: ProductDefinition?): Boolean = try {
        activity.startActivity(Intent(Intent.ACTION_VIEW, manageSubscriptionsUri(activity.packageName, product?.defaults?.productId)))
        true
    } catch (e: ActivityNotFoundException) {
        log.log(BillingLogLevel.WARN, "No app can open Play's subscription management: $e")
        false
    }

    override fun showInAppMessages(activity: Activity) {
        val runtime = runtime ?: return notReady("showInAppMessages")
        runtime.showInAppMessages(activity)
    }

    override fun setAccount(accountId: String?) {
        requestedAccount = accountId
        runtime?.entitlements?.setAccount(accountId)
    }

    private fun notReady(call: String) {
        log.log(BillingLogLevel.ERROR, "$call: ${BillingError.NotInitialized.debugMessage}")
    }

    private fun Application.isDebuggable(): Boolean = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** Stops the runtime and ends the Play connection. The only place the connection is ever ended. */
    @VisibleForTesting
    internal fun shutdownForTests() {
        synchronized(lock) {
            runtime?.shutdown()
            runtime = null
        }
    }

    /** Forgets [initialize] and every shared value, so each test starts clean. */
    @VisibleForTesting
    internal fun resetForTests() {
        synchronized(lock) {
            shutdownForTests()
            portFactory = ::PlayBillingAdapter
            settings = null
            requestedAccount = null
            log.minLevel = BillingLogLevel.WARN
            sharedEntitlements.value = EntitlementSnapshot.EMPTY_UNKNOWN
            sharedConnection.value = BillingConnectionState.Disconnected
            sharedCatalog.value = CatalogState.Loading
            sharedConsumables.value = emptyList()
        }
    }
}

/** Play's subscription management, for [productId] or, when null, for all of [packageName]'s subscriptions. */
@SuppressLint("UseKtx")
internal fun manageSubscriptionsUri(packageName: String, productId: String?): Uri =
    Uri.parse("https://play.google.com/store/account/subscriptions").buildUpon()
        .apply { productId?.let { appendQueryParameter("sku", it) } }
        .appendQueryParameter("package", packageName)
        .build()
