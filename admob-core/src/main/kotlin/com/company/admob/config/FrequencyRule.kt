package com.company.admob.config

import kotlin.time.Duration

/**
 * Client-side frequency limits for one placement, counted in memory for the current process.
 *
 * These limits are a user-experience safeguard, not policy enforcement, and don't replace AdMob's server-side
 * frequency capping.
 *
 * @property minInterval minimum time between two shows of this placement. Must not be negative.
 * @property maxPerSession maximum shows per process lifetime. Must not be negative.
 */
public data class FrequencyRule(
    val minInterval: Duration = Duration.ZERO,
    val maxPerSession: Int = Int.MAX_VALUE,
)
