package io.github.thesobiaakbar.adroit.internal.reliability

import io.github.thesobiaakbar.adroit.AdFormat
import io.github.thesobiaakbar.adroit.AdPlacement
import io.github.thesobiaakbar.adroit.config.FrequencyRule
import io.github.thesobiaakbar.adroit.internal.config.CounterPlan
import io.github.thesobiaakbar.adroit.internal.time.Clock
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal sealed interface FrequencyDecision {
    data object Allowed : FrequencyDecision

    /** Blocked. [retryAfter] is [Duration.INFINITE] when a session cap is reached. */
    data class Capped(val retryAfter: Duration) : FrequencyDecision

    /** The placement's counter isn't due: [remaining] more show calls are needed. */
    data class CounterNotReached(val remaining: Int) : FrequencyDecision
}

/**
 * Client-side frequency limits (architecture §20.6, 05_AD_PROFILES.md §4.2–4.3): per placement ([ruleFor]: counter,
 * minimum interval and session cap), an overall cap on interstitials ([maxInterstitials], splash placements not
 * counted), and a global minimum interval across [globalFormats]. Everything is read at every [check], so remote
 * overrides apply to the next decision. In memory, per process, on the monotonic clock. A UX safeguard, not policy
 * enforcement. Main thread only.
 *
 * Shows are counted with [recordShown] when the ad is actually shown, never when it's requested.
 *
 * The counter ([CounterPlan]) starts at its `start`, gains its `step` at every counted call, and is due at the current
 * max counter. [recordShown] puts it back to 0 and moves to the next max counter; [recordNotShown] puts it back to 0
 * only with `resetOnFail`.
 */
internal class FrequencyGate(
    private val clock: Clock,
    private val ruleFor: (AdPlacement) -> FrequencyRule,
    private val globalInterval: () -> Duration,
    private val globalFormats: Set<AdFormat> = globalFormats(includeRewarded = false),
    private val isSplash: (AdPlacement) -> Boolean = { false },
    private val maxInterstitials: () -> Int = { Int.MAX_VALUE },
) {
    private class Record(var count: Int, var lastShownAtElapsed: Long)

    private val records = mutableMapOf<AdPlacement, Record>()
    /** A placement's counter: its [value], and which max counter it's heading for ([index], wrapping around). */
    private class Counter(var value: Double, var index: Int = 0)

    private val counters = mutableMapOf<AdPlacement, Counter>()
    private var lastGlobalShownAtElapsed: Long? = null
    private var interstitialsShown = 0

    /**
     * Whether [placement] may show now. With [countTrigger] (an interstitial or rewarded interstitial `show` call),
     * the call first counts toward the placement's counter. Order: counter, overall interstitial cap, the
     * placement's session cap, its interval (ignored while a counter is set), then the global interval.
     */
    fun check(placement: AdPlacement, countTrigger: Boolean = false): FrequencyDecision {
        val rule = ruleFor(placement)
        val splash = isSplash(placement)
        val plan = rule.plan
        val counted = usesCounter(placement, plan)
        if (countTrigger && counted) {
            val counter = counterOf(placement, plan)
            counter.value += plan.stepValue
            val remaining = remainingCalls(counter, plan)
            if (remaining > 0) return FrequencyDecision.CounterNotReached(remaining)
        }
        if (placement.format == AdFormat.INTERSTITIAL && !splash && interstitialsShown >= maxInterstitials()) {
            return FrequencyDecision.Capped(Duration.INFINITE)
        }
        val record = records[placement]
        if ((record?.count ?: 0) >= rule.maxPerSession) return FrequencyDecision.Capped(Duration.INFINITE)

        var wait = Duration.ZERO
        if (record != null && !counted) wait = maxOf(wait, remaining(rule.minInterval, record.lastShownAtElapsed))
        val lastGlobal = lastGlobalShownAtElapsed
        if (lastGlobal != null && placement.format in globalFormats) {
            wait = maxOf(wait, remaining(globalInterval(), lastGlobal))
        }
        return if (wait.isPositive()) FrequencyDecision.Capped(wait) else FrequencyDecision.Allowed
    }

    /** Shows of [placement] counted this session, for diagnostics. */
    fun showsThisSession(placement: AdPlacement): Int = records[placement]?.count ?: 0

    /** How many more counted calls [placement] needs before it's due (0 = due now); null when it has no counter. */
    fun remainingCalls(placement: AdPlacement): Int? {
        val plan = ruleFor(placement).plan
        return if (usesCounter(placement, plan)) remainingCalls(counterOf(placement, plan), plan) else null
    }

    /**
     * For an ad on a timer (an interval and no counter): how long until [placement] may show again, zero when it may
     * now. Null when it has no timer.
     */
    fun timerRemaining(placement: AdPlacement): Duration? {
        val rule = ruleFor(placement)
        if (!rule.minInterval.isPositive() || usesCounter(placement, rule.plan)) return null
        val record = records[placement] ?: return Duration.ZERO
        return maxOf(Duration.ZERO, remaining(rule.minInterval, record.lastShownAtElapsed))
    }

    /** A show of [placement] ended without an ad: with `resetOnFail`, a due counter starts again from 0. */
    fun recordNotShown(placement: AdPlacement) {
        val plan = ruleFor(placement).plan
        val counter = counters[placement] ?: return
        if (plan.resetOnFail == true && usesCounter(placement, plan) && remainingCalls(counter, plan) == 0) counter.value = 0.0
    }

    fun recordShown(placement: AdPlacement) {
        val now = clock.elapsedRealtimeMillis()
        val record = records.getOrPut(placement) { Record(0, now) }
        record.count++
        record.lastShownAtElapsed = now
        counters[placement]?.let {
            it.value = 0.0
            it.index++
        }
        if (placement.format in globalFormats) lastGlobalShownAtElapsed = now
        if (placement.format == AdFormat.INTERSTITIAL && !isSplash(placement)) interstitialsShown++
    }

    /** Only interstitial and rewarded interstitial calls are counted, and never the splash ad's. */
    private fun usesCounter(placement: AdPlacement, plan: CounterPlan): Boolean =
        plan.isOn && !isSplash(placement) &&
            (placement.format == AdFormat.INTERSTITIAL || placement.format == AdFormat.REWARDED_INTERSTITIAL)

    private fun counterOf(placement: AdPlacement, plan: CounterPlan): Counter =
        counters.getOrPut(placement) { Counter(plan.startValue) }

    private fun remainingCalls(counter: Counter, plan: CounterPlan): Int {
        val max = plan.thresholds[counter.index % plan.thresholds.size]
        return maxOf(0, ceil((max - counter.value) / plan.stepValue - EPSILON).toInt())
    }

    private fun remaining(interval: Duration, since: Long): Duration =
        interval - (clock.elapsedRealtimeMillis() - since).milliseconds

    companion object {
        /** Absorbs the rounding of fractional steps: ten steps of 0.1 reach a max counter of 1. */
        private const val EPSILON = 1e-9

        /** Formats sharing the global interval. Rewarded joins only when the host opts in, since the user asks for it. */
        fun globalFormats(includeRewarded: Boolean): Set<AdFormat> =
            setOf(AdFormat.INTERSTITIAL, AdFormat.REWARDED_INTERSTITIAL, AdFormat.APP_OPEN) +
                if (includeRewarded) setOf(AdFormat.REWARDED) else emptySet()
    }
}
