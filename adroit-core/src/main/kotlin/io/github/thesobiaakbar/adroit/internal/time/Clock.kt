package io.github.thesobiaakbar.adroit.internal.time

import android.os.SystemClock

/** Time source, injectable so expiry, cooldown, and backoff logic can be tested with virtual time. */
internal interface Clock {
    /** Monotonic time since boot, including deep sleep. Use for durations, expiry, and cooldowns. */
    fun elapsedRealtimeMillis(): Long

    /** Wall-clock time. Use only for event timestamps. */
    fun currentTimeMillis(): Long
}

internal object SystemClockImpl : Clock {
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()

    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
