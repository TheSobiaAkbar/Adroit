package io.github.thesobiaakbar.adroit.billing.internal

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.thesobiaakbar.adroit.billing.BillingConnectionState
import io.github.thesobiaakbar.adroit.billing.BillingSettings
import io.github.thesobiaakbar.adroit.billing.CatalogState
import io.github.thesobiaakbar.adroit.billing.internal.catalog.CatalogRepository
import io.github.thesobiaakbar.adroit.billing.internal.play.ConnectionManager
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayBillingPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Everything billing runs once initialized, created once per process by `AdroitBilling.initialize`. Purchases and
 * entitlements join it in B06-B08.
 */
internal class BillingRuntime(
    port: PlayBillingPort,
    settings: BillingSettings,
    private val connectionState: MutableStateFlow<BillingConnectionState>,
    catalogState: MutableStateFlow<CatalogState>,
    log: BillingLog,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val connection = ConnectionManager(port, scope, connectionState, log)

    val catalog = CatalogRepository(
        connection, settings.products, catalogState, scope, log, maxAgeMillis = settings.catalogMaxAgeHours * HOUR_MILLIS,
    )

    /**
     * Reconnects, and reloads an old catalog, when the app returns to the foreground; also fires once on registering
     * in a started app.
     */
    private val foreground = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            connection.trigger()
            if (connectionState.value == BillingConnectionState.Connected) catalog.loadIfStale()
        }
    }

    fun start() {
        scope.launch {
            ProcessLifecycleOwner.get().lifecycle.addObserver(foreground)
            connection.trigger()
        }
        scope.launch {
            connectionState.collect { if (it == BillingConnectionState.Connected) catalog.loadIfStale() }
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
