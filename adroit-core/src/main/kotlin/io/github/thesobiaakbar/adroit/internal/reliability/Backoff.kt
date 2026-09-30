package io.github.thesobiaakbar.adroit.internal.reliability

import io.github.thesobiaakbar.adroit.config.RetryPolicy
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Retry delays (architecture §25). Pure. */
internal object Backoff {
    /**
     * The delay before retry [attempt] (1-based): `min(initialDelay * multiplier^(attempt-1), maxDelay)`, spread by
     * ±`jitterRatio` using [random], and never below [floor] (the no-fill minimum, when the failure was a no-fill).
     */
    fun delayFor(attempt: Int, policy: RetryPolicy, random: Random, floor: Duration = Duration.ZERO): Duration {
        require(attempt >= 1) { "attempt is 1-based, was $attempt" }
        val initialMs = policy.initialDelay.inWholeMilliseconds.toDouble()
        val maxMs = policy.maxDelay.inWholeMilliseconds.toDouble()
        // Double arithmetic: a large attempt overflows to infinity, which the cap then bounds.
        val baseMs = minOf(initialMs * policy.multiplier.pow(attempt - 1), maxMs)
        val jitter = if (policy.jitterRatio > 0) random.nextDouble(-policy.jitterRatio, policy.jitterRatio) else 0.0
        val delay = (baseMs * (1 + jitter)).toLong().milliseconds
        return maxOf(delay, floor)
    }
}
