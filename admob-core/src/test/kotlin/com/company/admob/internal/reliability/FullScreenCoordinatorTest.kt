package com.company.admob.internal.reliability

import com.company.admob.fakes.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class FullScreenCoordinatorTest {
    private val clock = FakeClock(elapsedRealtimeMillis = 1_000)
    private val coordinator = FullScreenCoordinator(clock)

    @Test
    fun `a single owner at a time`() {
        assertFalse(coordinator.isShowing)
        val token = coordinator.tryAcquire("interstitial:level_end")!!
        assertTrue(coordinator.isShowing)
        assertEquals("interstitial:level_end", coordinator.currentOwner)
        assertEquals("Token(interstitial:level_end)", token.toString())
        assertNull(coordinator.tryAcquire("rewarded:extra_life"))

        clock.advance(3.seconds)
        assertTrue(coordinator.release(token))
        assertFalse(coordinator.isShowing)
        assertNull(coordinator.currentOwner)
        assertEquals(4_000L, coordinator.lastFullScreenEndedAtElapsed)
    }

    @Test
    fun `a stale token can't release a newer session, even with the same owner name`() {
        val old = coordinator.tryAcquire("interstitial:level_end")!!
        assertTrue(coordinator.release(old))
        assertFalse("a repeated release is ignored", coordinator.release(old))

        val current = coordinator.tryAcquire("interstitial:level_end")!!
        assertFalse(coordinator.release(old))
        assertTrue(coordinator.isShowing)
        assertTrue(coordinator.release(current))
    }

    @Test
    fun `click timestamps`() {
        assertNull(coordinator.lastClickAtElapsed)
        assertNull(coordinator.lastFullScreenEndedAtElapsed)
        clock.advance(2.seconds)
        coordinator.markClicked()
        assertEquals(3_000L, coordinator.lastClickAtElapsed)
    }
}
