package com.company.admob.internal.reliability

import com.company.admob.AdError
import com.company.admob.config.RetryPolicy
import com.company.admob.internal.config.FormatDefaults
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.port.AppForegroundSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration

/**
 * Schedules load retries with [Backoff], one pending retry per key (for example `"INTERSTITIAL:<adUnitId>"`).
 * Uses `delay` in [scope] (the library scope, main-thread confined), so tests run on virtual time.
 *
 * A retry that comes due while the app is in the background waits for the next foreground instead: no requests are
 * made in the background (architecture §25). Main thread only.
 */
internal class RetryScheduler(
    private val scope: CoroutineScope,
    private val foreground: AppForegroundSource,
    private val log: AdLog,
    private val random: Random = Random.Default,
    private val noFillFloor: Duration = FormatDefaults.NO_FILL_MIN_RETRY_DELAY,
) {
    private val jobs = mutableMapOf<String, Job>()

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
        log.d { "$key: retry $attempt in $delay after ${error.code}" }
        // Registered before it starts: with a zero delay on an immediate dispatcher it could otherwise finish first.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            delay(delay)
            if (!foreground.isInForeground) {
                log.d { "$key: retry deferred until the app returns to the foreground" }
                foreground.foregroundEvents.first()
            }
            // Unregister before running, so a block that schedules the next retry doesn't cancel itself.
            jobs.remove(key)
            block()
        }
        jobs[key] = job
        job.start()
        return delay
    }

    fun isScheduled(key: String): Boolean = jobs.containsKey(key)

    fun cancel(key: String) {
        jobs.remove(key)?.cancel()
    }

    fun cancelAll() {
        val pending = jobs.values.toList()
        jobs.clear()
        pending.forEach { it.cancel() }
    }
}
