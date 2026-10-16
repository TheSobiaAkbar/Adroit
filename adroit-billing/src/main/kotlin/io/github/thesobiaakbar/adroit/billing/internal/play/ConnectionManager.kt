package io.github.thesobiaakbar.adroit.billing.internal.play

import io.github.thesobiaakbar.adroit.billing.BillingConnectionState
import io.github.thesobiaakbar.adroit.billing.BillingError
import io.github.thesobiaakbar.adroit.billing.BillingLogLevel
import io.github.thesobiaakbar.adroit.billing.internal.BillingLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps the single Play connection up and publishes it in [state].
 *
 * Connecting starts on a trigger ([trigger]: start-up, the app coming to the foreground, `refresh()`). A
 * [BillingError.ServiceDisconnected] or [BillingError.NetworkError] is retried after 1, 2, 4, 8 and 16 s; after that,
 * and after any other error, nothing happens until the next trigger. [BillingError.Unavailable] (no Play Store, a
 * blocked store) becomes [BillingConnectionState.Unavailable]. A disconnect reconnects at once.
 *
 * Every Play call goes through [call], which waits for the connection but never longer than [callTimeoutMillis].
 */
internal class ConnectionManager(
    private val port: PlayBillingPort,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<BillingConnectionState>,
    private val log: BillingLog,
    private val now: () -> Long = System::currentTimeMillis,
    private val callTimeoutMillis: Long = CALL_TIMEOUT_MILLIS,
) {
    private var connecting: Job? = null

    /** Connects unless connected or already connecting (which includes waiting for a retry). */
    @Synchronized
    fun trigger() {
        if (connecting?.isActive == true || state.value == BillingConnectionState.Connected) return
        connecting = scope.launch { connectWithRetries() }
    }

    /**
     * Runs [block] once connected. If it fails with [BillingError.ServiceDisconnected], reconnects and runs it once
     * more. Returns [BillingError.ServiceDisconnected] when no connection comes within [callTimeoutMillis], or the
     * connection's own error when connecting has given up.
     */
    suspend fun <T> call(name: String, block: suspend PlayBillingPort.() -> PlayResult<T>): PlayResult<T> {
        awaitConnected()?.let { return PlayResult.Failed(it) }
        val first = port.block()
        if ((first as? PlayResult.Failed)?.error !is BillingError.ServiceDisconnected) return first
        log.log(BillingLogLevel.INFO, "$name: service disconnected, reconnecting and trying once more")
        onDisconnected()
        awaitConnected()?.let { return PlayResult.Failed(it) }
        return port.block()
    }

    /** Stops connecting and ends the connection. Only for tests. */
    @Synchronized
    fun shutdown() {
        connecting?.cancel()
        port.endConnection()
        state.value = BillingConnectionState.Disconnected
    }

    /** Null once connected; otherwise why there is no connection. */
    private suspend fun awaitConnected(): BillingError? =
        when (val settled = withTimeoutOrNull(callTimeoutMillis) { state.first { it.isSettled } }) {
            BillingConnectionState.Connected -> null
            is BillingConnectionState.Unavailable -> settled.error
            is BillingConnectionState.Error -> settled.error
            else -> BillingError.ServiceDisconnected(debugMessage = "Not connected after $callTimeoutMillis ms")
        }

    private val BillingConnectionState.isSettled: Boolean
        get() = this == BillingConnectionState.Connected || this is BillingConnectionState.Unavailable ||
            (this is BillingConnectionState.Error && retryAtMillis == null)

    private suspend fun connectWithRetries() {
        repeat(MAX_RETRIES + 1) { attempt ->
            state.value = BillingConnectionState.Connecting
            val error = when (val result = port.connect(::onDisconnected)) {
                is PlayResult.Ok -> {
                    log.log(BillingLogLevel.INFO, "Connected to Play")
                    state.value = BillingConnectionState.Connected
                    return
                }
                is PlayResult.Failed -> result.error
                is PlayResult.Cancelled -> BillingError.Unknown(debugMessage = "Connecting was cancelled")
            }
            log.log(BillingLogLevel.WARN, "Connecting to Play failed: ${error.code} ${error.debugMessage.orEmpty()}")
            if (error is BillingError.Unavailable) {
                state.value = BillingConnectionState.Unavailable(error)
                return
            }
            if (!error.retriesConnection || attempt == MAX_RETRIES) {
                state.value = BillingConnectionState.Error(error, retryAtMillis = null)
                return
            }
            val wait = FIRST_RETRY_MILLIS shl attempt
            state.value = BillingConnectionState.Error(error, retryAtMillis = now() + wait)
            delay(wait)
        }
    }

    private fun onDisconnected() {
        synchronized(this) {
            if (connecting?.isActive != true) state.value = BillingConnectionState.Disconnected
        }
        trigger()
    }

    private val BillingError.retriesConnection: Boolean
        get() = this is BillingError.ServiceDisconnected || this is BillingError.NetworkError

    companion object {
        const val CALL_TIMEOUT_MILLIS: Long = 10_000
        const val FIRST_RETRY_MILLIS: Long = 1_000
        const val MAX_RETRIES: Int = 5
    }
}
