package com.company.admob.internal.reliability

import com.company.admob.AdFormat
import com.company.admob.AdPlacement
import com.company.admob.config.FrequencyRule
import com.company.admob.internal.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal sealed interface FrequencyDecision {
    data object Allowed : FrequencyDecision

    /** Blocked. [retryAfter] is [Duration.INFINITE] when the session cap is reached. */
    data class Capped(val retryAfter: Duration) : FrequencyDecision
}

/**
 * Client-side frequency limits (architecture §20.6): per placement ([ruleFor]: minimum interval and session cap), and
 * a global minimum interval across [globalFormats]. In memory, per process, on the monotonic clock. A UX safeguard,
 * not policy enforcement. Main thread only.
 *
 * Shows are counted with [recordShown] when the ad is actually shown, never when it's requested.
 */
internal class FrequencyGate(
    private val clock: Clock,
    private val ruleFor: (AdPlacement) -> FrequencyRule,
    private val globalInterval: Duration,
    private val globalFormats: Set<AdFormat> = globalFormats(includeRewarded = false),
) {
    private class Record(var count: Int, var lastShownAtElapsed: Long)

    private val records = mutableMapOf<AdPlacement, Record>()
    private var lastGlobalShownAtElapsed: Long? = null

    fun check(placement: AdPlacement): FrequencyDecision {
        val rule = ruleFor(placement)
        val record = records[placement]
        if ((record?.count ?: 0) >= rule.maxPerSession) return FrequencyDecision.Capped(Duration.INFINITE)

        var wait = Duration.ZERO
        if (record != null) wait = maxOf(wait, remaining(rule.minInterval, record.lastShownAtElapsed))
        val lastGlobal = lastGlobalShownAtElapsed
        if (lastGlobal != null && placement.format in globalFormats) {
            wait = maxOf(wait, remaining(globalInterval, lastGlobal))
        }
        return if (wait.isPositive()) FrequencyDecision.Capped(wait) else FrequencyDecision.Allowed
    }

    fun recordShown(placement: AdPlacement) {
        val now = clock.elapsedRealtimeMillis()
        val record = records.getOrPut(placement) { Record(0, now) }
        record.count++
        record.lastShownAtElapsed = now
        if (placement.format in globalFormats) lastGlobalShownAtElapsed = now
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
