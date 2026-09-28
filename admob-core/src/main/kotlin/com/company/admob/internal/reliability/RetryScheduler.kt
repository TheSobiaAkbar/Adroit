package com.company.admob.internal.reliability

import com.company.admob.AdError
import com.company.admob.config.RetryPolicy
import com.company.admob.internal.config.FormatDefaults
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.port.AppForegroundSource
import com.company.admob.internal.time.Clock
import com.company.admob.network.NetworkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Schedules load retries with [Backoff], one pending retry per key (for example `"INTERSTITIAL:<adUnitId>"`).
 * Uses `delay` in [scope] (the library scope, main-thread confined), so tests run on virtual time.
 *
 * A retry that comes due while the app is in the background waits for the next foreground instead: no requests are
 * made in the background (architecture §25).
 *
 * Without a network (airplane mode, or Wi-Fi and mobile data off) retries pause: a retry scheduled or due offline
 * waits until a network returns, then runs at once, without the rest of its delay, so ads load as soon as they can
 * and no attempt is spent on a request that can't succeed. A network without validated internet (a Wi-Fi sign-in
 * page) doesn't pause retries: that check can be wrong, and the backoff already bounds them. Main thread only.
 */
internal class RetryScheduler(
    private val scope: CoroutineScope,
    private val foreground: AppForegroundSource,
    private val log: AdLog,
    private val random: Random = Random.Default,
    private val noFillFloor: Duration = FormatDefaults.NO_FILL_MIN_RETRY_DELAY,
    /** For [nextRetryIn] only; without one, diagnostics report no retry time. */
    private val clock: Clock? = null,
    /** The device's network ([com.company.admob.network.NetworkMonitor.state]), read lazily; null never pauses. */
    private val network: Lazy<StateFlow<NetworkState>>? = null,
) {
    private val jobs = mutableMapOf<String, Job>()

    /** When each pending retry comes due ([Clock.elapsedRealtimeMillis]), for diagnostics. */
    private val dueAt = mutableMapOf<String, Long>()

    /**
     * Schedules retry [attempt] (1-based) of [block] for [key], replacing any pending retry for that key. Nothing is
     * scheduled when [retryable] is false or [attempt] exceeds `policy.maxAttempts`.
     *
     * @return the delay, or null when nothing was scheduled.
     */
    fun schedule(
        key: String,
        attempt: Int,
        policy: RetryPolicy,
        error: AdError,
        retryable: Boolean,
        block: () -> Unit,
    ): Duration? {
        cancel(key)
        if (!retryable) {
            log.d { "$key: not retrying a non-retryable error (${error.code})" }
            return null
        }
        if (attempt > policy.maxAttempts) {
            log.d { "$key: retries exhausted after ${policy.maxAttempts} attempts" }
            return null
        }
        val floor = if (error is AdError.NoFill) noFillFloor else Duration.ZERO
        val delay = Backoff.delayFor(attempt, policy, random, floor)
        val offline = isOffline()
        log.d {
            if (offline) "$key: retry $attempt when the network returns, after ${error.code}"
            else "$key: retry $attempt in $delay after ${error.code}"
        }
        // Registered before it starts: with a zero delay on an immediate dispatcher it could otherwise finish first.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            if (offline) awaitNetwork() else delay(delay)
            if (!foreground.isInForeground) {
                log.d { "$key: retry deferred until the app returns to the foreground" }
                foreground.foregroundEvents.first()
            }
            if (isOffline()) {
                log.d { "$key: retry paused until the network returns" }
                awaitNetwork()
            }
            // Unregister before running, so a block that schedules the next retry doesn't cancel itself.
            jobs.remove(key)
            dueAt.remove(key)
            block()
        }
        jobs[key] = job
        clock?.let { dueAt[key] = it.elapsedRealtimeMillis() + if (offline) 0 else delay.inWholeMilliseconds }
        job.start()
        return delay
    }

    private fun isOffline(): Boolean = network?.value?.value == NetworkState.Unavailable

    /**
     * Suspends until a network is back, then up to [VALIDATION_GRACE] more while Android checks its internet access
     * (a network that was just joined is briefly ConnectedNoInternet).
     */
    private suspend fun awaitNetwork() {
        val state = network?.value ?: return
        state.first { it != NetworkState.Unavailable }
        withTimeoutOrNull(VALIDATION_GRACE) { state.first { it.isOnline } }
    }

    fun isScheduled(key: String): Boolean = jobs.containsKey(key)

    /** Time until the pending retry for [key] is due (zero once due, while it waits for the foreground), or null. */
    fun nextRetryIn(key: String): Duration? {
        val due = dueAt[key] ?: return null
        val now = clock?.elapsedRealtimeMillis() ?: return null
        return (due - now).coerceAtLeast(0).milliseconds
    }

    fun cancel(key: String) {
        dueAt.remove(key)
        jobs.remove(key)?.cancel()
    }

    fun cancelAll() {
        val pending = jobs.values.toList()
        jobs.clear()
        dueAt.clear()
        pending.forEach { it.cancel() }
    }

    private companion object {
        val VALIDATION_GRACE = 5.seconds
    }
}
