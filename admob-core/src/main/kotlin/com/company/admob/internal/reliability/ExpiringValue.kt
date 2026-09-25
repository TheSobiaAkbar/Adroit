package com.company.admob.internal.reliability

import com.company.admob.internal.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A loaded ad and when it expires, on the monotonic clock. Expired once more than [ttl] has passed since
 * [loadedAtElapsed] (architecture §13: `now - loadedAt > ttl`), so it's still usable exactly at the TTL.
 */
internal class Cached<T>(val value: T, val loadedAtElapsed: Long, val ttl: Duration) {
    val expiresAtElapsed: Long = loadedAtElapsed + ttl.inWholeMilliseconds

    fun isExpired(clock: Clock): Boolean = clock.elapsedRealtimeMillis() > expiresAtElapsed

    fun age(clock: Clock): Duration = (clock.elapsedRealtimeMillis() - loadedAtElapsed).milliseconds

    override fun toString(): String = "Cached(value=$value, loadedAtElapsed=$loadedAtElapsed, ttl=$ttl)"
}
