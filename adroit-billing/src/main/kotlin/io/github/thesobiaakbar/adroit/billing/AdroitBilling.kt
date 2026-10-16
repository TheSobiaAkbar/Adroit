package io.github.thesobiaakbar.adroit.billing

import android.app.Activity
import android.app.Application
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
import kotlinx.coroutines.flow.emptyFlow

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
     * reads the saved entitlements; Play is contacted in the background. Callable from any thread.
     *
     * Later calls are ignored: with equal settings silently, with different ones with an error log; the first
     * settings are kept.
     *
     * Invalid settings (for example a paywall using an undeclared product) throw [IllegalStateException] listing every
     * problem in a debuggable app. In a release app they are logged, and billing stays off (every call reports
     * [BillingError.NotInitialized]) while the app keeps running.
     */
    @JvmStatic
    public fun initialize(application: Application, settings: BillingSettings) {
        synchronized(lock) {
            this.settings?.let {
                if (it != settings) log.log(BillingLogLevel.ERROR, "initialize called again with different settings; kept the first")
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
            runtime = BillingRuntime(portFactory(application), settings, sharedConnection, sharedCatalog, log).also { it.start() }
        }
    }

    override val entitlements: StateFlow<EntitlementSnapshot> = sharedEntitlements.asStateFlow()

    override val connection: StateFlow<BillingConnectionState> = sharedConnection.asStateFlow()

    override val catalog: StateFlow<CatalogState> = sharedCatalog.asStateFlow()

    override val purchaseUpdates: SharedFlow<PurchaseUpdate> = sharedPurchaseUpdates.asSharedFlow()

    override val consumables: Flow<ConsumableGrant> get() = emptyFlow()

    override suspend fun purchase(activity: Activity, request: PurchaseRequest): PurchaseResult {
        notReady("purchase")
        return PurchaseResult.Failed(BillingError.NotInitialized, attribution = null)
    }

    override suspend fun restore(): RestoreResult {
        notReady("restore")
        return RestoreResult.Failed(BillingError.NotInitialized)
    }

    override suspend fun refresh() {
        val runtime = runtime ?: return notReady("refresh")
        runtime.connection.trigger()
        runtime.catalog.load()
    }

    override fun openManageSubscriptions(activity: Activity, product: ProductDefinition?): Boolean {
        notReady("openManageSubscriptions")
        return false
    }

    override fun setAccount(accountId: String?) {
        requestedAccount = accountId
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
        }
    }
}
