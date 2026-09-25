package com.company.admob.fakes

import com.company.admob.internal.time.Clock
import kotlin.time.Duration

/** A [Clock] that only moves when the test calls [advance]. Both clocks move together. */
internal class FakeClock(
    private var elapsedRealtimeMillis: Long = 0,
    private var currentTimeMillis: Long = 1_700_000_000_000,
) : Clock {
    override fun elapsedRealtimeMillis(): Long = elapsedRealtimeMillis

    override fun currentTimeMillis(): Long = currentTimeMillis

    fun advance(duration: Duration) {
        require(!duration.isNegative()) { "A clock can't go backwards" }
        elapsedRealtimeMillis += duration.inWholeMilliseconds
        currentTimeMillis += duration.inWholeMilliseconds
    }
}
