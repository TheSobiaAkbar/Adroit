package com.company.admob.internal.reliability

import com.company.admob.AdFormat

/**
 * Ad unit rotation (05_AD_PROFILES.md §4.1): each load request for a list of ad units takes the next one, and after
 * the last one the first again. The position is kept per (format, list), so placements with the same list share it,
 * and a changed list starts from its first ID. In memory only: it restarts with the process and is never stored.
 * Main thread only.
 */
internal class AdUnitRotation {
    /** One pick: [adUnitId], at 1-based [position] of [count]. */
    class Pick(val adUnitId: String, val position: Int, val count: Int) {
        /** "2 of 3" for logs, or empty when the list has a single ID. */
        val describe: String get() = if (count > 1) " (ID $position of $count)" else ""
    }

    private val next = mutableMapOf<Pair<AdFormat, List<String>>, Int>()

    /** Returns the ad unit to request now from [adUnitIds] (never empty), and advances the position. */
    fun next(format: AdFormat, adUnitIds: List<String>): Pick {
        val key = format to adUnitIds
        val index = (next[key] ?: 0) % adUnitIds.size
        next[key] = (index + 1) % adUnitIds.size
        return Pick(adUnitIds[index], index + 1, adUnitIds.size)
    }
}
