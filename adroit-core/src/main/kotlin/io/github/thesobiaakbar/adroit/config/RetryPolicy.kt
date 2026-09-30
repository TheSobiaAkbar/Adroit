package io.github.thesobiaakbar.adroit.config

import kotlin.time.Duration

/**
 * Exponential backoff for failed ad loads. The delay before retry `n` (1-based) is
 * `min(initialDelay * multiplier^(n-1), maxDelay)`, randomised by ±[jitterRatio]. After [maxAttempts] failed
 * retries the library stops until the next `preload()`, show attempt, or return to the foreground.
 *
 * Leave a placement's `retry` unset to use the format default (docs/architecture/02_ARCHITECTURE.md §25).
 *
 * @property initialDelay delay before the first retry. Must not be negative.
 * @property multiplier growth factor between retries. Must be at least 1.
 * @property maxDelay upper bound for any single delay. Must not be negative.
 * @property maxAttempts number of retries; 0 disables retrying. Must not be negative.
 * @property jitterRatio random spread applied to each delay, in 0.0..1.0.
 */
public data class RetryPolicy(
    val initialDelay: Duration,
    val multiplier: Double,
    val maxDelay: Duration,
    val maxAttempts: Int,
    val jitterRatio: Double,
)
