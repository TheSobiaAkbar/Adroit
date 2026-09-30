package io.github.thesobiaakbar.adroit.internal.reliability

import io.github.thesobiaakbar.adroit.fakes.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class CachedTest {
    @Test
    fun `usable exactly at the TTL, expired one millisecond later`() {
        val clock = FakeClock(elapsedRealtimeMillis = 1_000)
        val cached = Cached("ad", loadedAtElapsed = clock.elapsedRealtimeMillis(), ttl = 55.minutes)
        assertEquals(1_000 + 55 * 60_000L, cached.expiresAtElapsed)
        assertFalse(cached.isExpired(clock))

        clock.advance(55.minutes)
        assertFalse(cached.isExpired(clock))
        assertEquals(55.minutes, cached.age(clock))

        clock.advance(1.milliseconds)
        assertTrue(cached.isExpired(clock))
        assertEquals("Cached(value=ad, loadedAtElapsed=1000, ttl=55m)", cached.toString())
        assertEquals("ad", cached.value)
        assertEquals(1_000L, cached.loadedAtElapsed)
        assertEquals(55.minutes, cached.ttl)
    }
}
