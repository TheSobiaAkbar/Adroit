package io.github.thesobiaakbar.adroit.billing.internal

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.thesobiaakbar.adroit.billing.BillingConnectionState
import io.github.thesobiaakbar.adroit.billing.internal.play.ConnectionManager
import io.github.thesobiaakbar.adroit.billing.internal.play.PlayBillingPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Everything billing runs once initialized, created once per process by `AdroitBilling.initialize`. Purchases,
 * catalog and entitlements join it in B05-B08.
 */
internal class BillingRuntime(
    port: PlayBillingPort,
    connectionState: MutableStateFlow<BillingConnectionState>,
    log: BillingLog,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val connection = ConnectionManager(port, scope, connectionState, log)

    /** Reconnects when the app returns to the foreground; also fires once on registering in a started app. */
    private val foreground = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) = connection.trigger()
    }

    fun start() {
        scope.launch {
            ProcessLifecycleOwner.get().lifecycle.addObserver(foreground)
            connection.trigger()
        }
    }

    fun shutdown() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(foreground)
        connection.shutdown()
        scope.cancel()
    }
}
