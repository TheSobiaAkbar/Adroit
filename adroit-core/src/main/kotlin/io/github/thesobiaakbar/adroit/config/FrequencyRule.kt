package io.github.thesobiaakbar.adroit.config

import io.github.thesobiaakbar.adroit.internal.config.CounterPlan
import kotlin.time.Duration

/**
 * Client-side frequency limits for one placement, counted in memory for the current process.
 *
 * These limits are a user-experience safeguard, not policy enforcement, and don't replace AdMob's server-side
 * frequency capping.
 *
 * A regular class, not a data class (ADR-011): it gained [counter] in 0.2.0 and may gain more limits, so it has no
 * `copy` or `componentN` for a new property to break. Two rules are equal when all their limits are equal.
 *
 * @property minInterval minimum time between two shows of this placement. Must not be negative. Ignored while
 *   [counter] is set.
 * @property maxPerSession maximum shows per process lifetime. Must not be negative.
 * @property counter 0 (the default) turns the counter off. N shows an ad at most on every Nth `show` call: with 3,
 *   on the 3rd, 6th, 9th call and so on. A due counter stays due until an ad actually shows. Only interstitial and
 *   rewarded interstitial `show` calls count. Must not be negative. An ad profile can set more (several max counters,
 *   a start, a step; docs/AD_PROFILES.md, "Counter").
 */
public class FrequencyRule(
    public val minInterval: Duration = Duration.ZERO,
    public val maxPerSession: Int = Int.MAX_VALUE,
    public val counter: Int = 0,
) {
    /**
     * An ad profile's full counter settings (several max counters, start, step…; docs/AD_PROFILES.md, "Counter"). Set
     * only by the library, right after construction; null means [counter] alone.
     */
    internal var counterPlan: CounterPlan? = null

    /** The counter in force: [counterPlan], or [counter] as its only max counter. */
    internal val plan: CounterPlan get() = counterPlan ?: CounterPlan(maxes = listOf(counter.toDouble()))

    override fun equals(other: Any?): Boolean =
        other is FrequencyRule && other.minInterval == minInterval && other.maxPerSession == maxPerSession &&
            other.counter == counter && other.counterPlan == counterPlan

    override fun hashCode(): Int =
        ((minInterval.hashCode() * 31 + maxPerSession) * 31 + counter) * 31 + counterPlan.hashCode()

    override fun toString(): String =
        "FrequencyRule(minInterval=$minInterval, maxPerSession=$maxPerSession, counter=$counter)"
}
