package io.github.thesobiaakbar.adroit.internal.reliability

import io.github.thesobiaakbar.adroit.AdFormat
import io.github.thesobiaakbar.adroit.AdPlacement
import io.github.thesobiaakbar.adroit.config.FrequencyRule
import io.github.thesobiaakbar.adroit.internal.time.Clock
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
    private val triggers = mutableMapOf<AdPlacement, Int>()
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
        val counting = countTrigger && rule.counter > 0 && !splash
        if (counting) {
            val count = (triggers[placement] ?: 0) + 1
            triggers[placement] = count
            if (count < rule.counter) return FrequencyDecision.CounterNotReached(rule.counter - count)
        }
        if (placement.format == AdFormat.INTERSTITIAL && !splash && interstitialsShown >= maxInterstitials()) {
            return FrequencyDecision.Capped(Duration.INFINITE)
        }
        val record = records[placement]
        if ((record?.count ?: 0) >= rule.maxPerSession) return FrequencyDecision.Capped(Duration.INFINITE)

        var wait = Duration.ZERO
        if (record != null && !counting) wait = maxOf(wait, remaining(rule.minInterval, record.lastShownAtElapsed))
        val lastGlobal = lastGlobalShownAtElapsed
        if (lastGlobal != null && placement.format in globalFormats) {
            wait = maxOf(wait, remaining(globalInterval(), lastGlobal))
        }
        return if (wait.isPositive()) FrequencyDecision.Capped(wait) else FrequencyDecision.Allowed
    }

    /** Shows of [placement] counted this session, for diagnostics. */
    fun showsThisSession(placement: AdPlacement): Int = records[placement]?.count ?: 0

    /** Show calls counted toward [placement]'s counter since its last show, for diagnostics. */
    fun triggersSinceShow(placement: AdPlacement): Int = triggers[placement] ?: 0

    fun recordShown(placement: AdPlacement) {
        val now = clock.elapsedRealtimeMillis()
        val record = records.getOrPut(placement) { Record(0, now) }
        record.count++
        record.lastShownAtElapsed = now
        triggers.remove(placement)
        if (placement.format in globalFormats) lastGlobalShownAtElapsed = now
        if (placement.format == AdFormat.INTERSTITIAL && !isSplash(placement)) interstitialsShown++
    }

    private fun remaining(interval: Duration, since: Long): Duration =
        interval - (clock.elapsedRealtimeMillis() - since).milliseconds

    companion object {
        /** Formats sharing the global interval. Rewarded joins only when the host opts in, since the user asks for it. */
        fun globalFormats(includeRewarded: Boolean): Set<AdFormat> =
            setOf(AdFormat.INTERSTITIAL, AdFormat.REWARDED_INTERSTITIAL, AdFormat.APP_OPEN) +
                if (includeRewarded) setOf(AdFormat.REWARDED) else emptySet()
    }
}
