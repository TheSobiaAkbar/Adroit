package com.company.admob.config

import kotlin.time.Duration

/**
 * Client-side frequency limits for one placement, counted in memory for the current process.
 *
 * These limits are a user-experience safeguard, not policy enforcement, and don't replace AdMob's server-side
 * frequency capping.
 *
 * @property minInterval minimum time between two shows of this placement. Must not be negative. Ignored while
 *   [counter] is set.
 * @property maxPerSession maximum shows per process lifetime. Must not be negative.
 * @property counter 0 (the default) turns the counter off. N shows an ad at most on every Nth `show` call: with 3,
 *   on the 3rd, 6th, 9th call and so on. A due counter stays due until an ad actually shows. Only interstitial and
 *   rewarded interstitial `show` calls count. Must not be negative.
 */
public data class FrequencyRule(
    val minInterval: Duration = Duration.ZERO,
    val maxPerSession: Int = Int.MAX_VALUE,
    val counter: Int = 0,
)
