package com.company.admob.fakes

import com.company.admob.internal.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler

/** A [Clock] that follows [scheduler]'s virtual time, so `delay`-based scheduling and clock reads stay in sync. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class TestClock(
    private val scheduler: TestCoroutineScheduler,
    private val wallClockBase: Long = 1_700_000_000_000,
) : Clock {
    override fun elapsedRealtimeMillis(): Long = scheduler.currentTime

    override fun currentTimeMillis(): Long = wallClockBase + scheduler.currentTime
}
