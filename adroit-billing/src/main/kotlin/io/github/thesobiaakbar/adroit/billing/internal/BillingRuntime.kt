package io.github.thesobiaakbar.adroit.billing.internal

import android.app.Activity
import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.thesobiaakbar.adroit.billing.BillingConnectionState
import io.github.thesobiaakbar.adroit.billing.BillingSettings
import io.github.thesobiaakbar.adroit.billing.CatalogState
import io.github.thesobiaakbar.adroit.billing.ConsumableGrant
import io.github.thesobiaakbar.adroit.billing.EntitlementSnapshot
import io.github.thesobiaakbar.adroit.billing.PurchaseUpdate
import io.github.thesobiaakbar.adroit.billing.internal.attribution.AttemptStore
import io.github.thesobiaakbar.adroit.billing.internal.catalog.CatalogRepository
import io.github.thesobiaakbar.adroit.billing.internal.entitlement.EntitlementCache
import io.github.thesobiaakbar.adroit.billing.internal.entitlement.EntitlementEngine
import io.github.thesobiaakbar.adroit.billing.internal.events.EventBus
import io.github.thesobiaakbar.adroit.billing.internal.play.ConnectionManager
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayBillingPort
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayResult
import io.github.thesobiaakbar.adroit.billing.internal.purchase.PurchaseLauncher
import io.github.thesobiaakbar.adroit.billing.internal.purchase.PurchaseProcessor
import io.github.thesobiaakbar.adroit.billing.internal.purchase.TokenLedger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Everything billing runs once initialized, created once per process by `AdroitBilling.initialize`. Creating it reads
 * the saved entitlements into `entitlementState` synchronously; Play is contacted from [start] on.
 */
internal class BillingRuntime(
    application: Application,
    private val port: PlayBillingPort,
    private val settings: BillingSettings,
    private val connectionState: MutableStateFlow<BillingConnectionState>,
    catalogState: MutableStateFlow<CatalogState>,
    purchaseUpdates: MutableSharedFlow<PurchaseUpdate>,
    consumables: MutableStateFlow<List<ConsumableGrant>>,
    entitlementState: MutableStateFlow<EntitlementSnapshot>,
    log: BillingLog,
    private val account: () -> String?,
    debuggable: Boolean,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val connection = ConnectionManager(port, scope, connectionState, log)

    val catalog = CatalogRepository(
        connection, settings.products, catalogState, scope, log, maxAgeMillis = settings.catalogMaxAgeHours * HOUR_MILLIS,
    )

    private val events = EventBus(settings.events, log)

    private val attempts = AttemptStore(application)

    private val ledger = TokenLedger(application)

    private val processor = PurchaseProcessor(
        connection, settings, ledger, purchaseUpdates, consumables, events, log, application.packageName, account,
    )

    val entitlements = EntitlementEngine(
        connection, settings, processor, ledger, attempts, EntitlementCache(application), entitlementState, events, scope,
        log, account,
    )

    val purchases = PurchaseLauncher(
        connection, catalog, catalogState, settings, attempts, purchaseUpdates, events, log, account, debuggable,
        processor, entitlementState, entitlements::afterPurchase,
    )

    /**
     * Reconnects, or reloads an old catalog and syncs purchases, when the app returns to the foreground; also fires
     * once on registering in a started app.
     */
    private val foreground = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            connection.trigger()
            if (connectionState.value != BillingConnectionState.Connected) return
            catalog.loadIfStale()
            entitlements.onForeground()
        }
    }

    fun start() {
        entitlements.setAccount(account())
        scope.launch {
            ProcessLifecycleOwner.get().lifecycle.addObserver(foreground)
            connection.trigger()
        }
        scope.launch {
            connectionState.collect {
                if (it != BillingConnectionState.Connected) return@collect
                catalog.loadIfStale()
                entitlements.requestSync()
            }
        }
        scope.launch { settings.config.updates.collect { entitlements.publish() } }
        scope.launch { purchases.handle(port.purchasesUpdates) }
    }

    /** Shows Play's transactional messages over [activity]; syncs when one changed a subscription. */
    fun showInAppMessages(activity: Activity) {
        scope.launch {
            val shown = connection.call("showInAppMessages") { showInAppMessages(activity) }
            if (shown is PlayResult.Ok && shown.value != null) entitlements.requestSync()
        }
    }

    fun shutdown() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(foreground)
        connection.shutdown()
        scope.cancel()
    }

    private companion object {
        const val HOUR_MILLIS = 3_600_000L
    }
}
