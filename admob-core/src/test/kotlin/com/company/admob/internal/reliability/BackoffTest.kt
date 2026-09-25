package com.company.admob.internal.reliability

import com.company.admob.config.RetryPolicy
import com.company.admob.config.adMobConfig
import com.company.admob.internal.config.FormatDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class BackoffTest {
    private val noJitter = FormatDefaults.FULL_SCREEN_RETRY.copy(jitterRatio = 0.0)

    @Test
    fun `the defaults match architecture section 25`() {
        assertEquals(RetryPolicy(5.seconds, 2.0, 120.seconds, 5, 0.2), FormatDefaults.FULL_SCREEN_RETRY)
        assertEquals(RetryPolicy(10.seconds, 2.0, 300.seconds, 3, 0.2), FormatDefaults.APP_OPEN_RETRY)
        assertEquals(RetryPolicy(10.seconds, 2.0, 120.seconds, 3, 0.2), FormatDefaults.NATIVE_RETRY)
        assertEquals(RetryPolicy(15.seconds, 2.0, 120.seconds, 4, 0.2), FormatDefaults.BANNER_RETRY)
        assertEquals(30.seconds, FormatDefaults.NO_FILL_MIN_RETRY_DELAY)
        assertEquals(30.seconds, FormatDefaults.MIN_INTERVAL_BETWEEN_FULL_SCREEN_ADS)
        // The ShowSession watchdog default is advanced.showTimeout.
        assertEquals(5.seconds, adMobConfig { }.advanced.showTimeout)
    }

    @Test
    fun `full-screen defaults without jitter give 5, 10, 20, 40, 80 s`() {
        val delays = (1..5).map { Backoff.delayFor(it, noJitter, Random(0)) }
        assertEquals(listOf(5, 10, 20, 40, 80).map { it.seconds }, delays)
    }

    @Test
    fun `jitter stays within plus or minus 20 percent and actually spreads`() {
        val random = Random(42)
        val samples = List(1_000) { Backoff.delayFor(1, FormatDefaults.FULL_SCREEN_RETRY, random) }
        assertTrue(samples.all { it in 4.seconds..6.seconds })
        assertTrue(samples.minOrNull()!! < 4_500.milliseconds)
        assertTrue(samples.maxOrNull()!! > 5_500.milliseconds)
    }

    @Test
    fun `the delay is capped at maxDelay, even for huge attempts`() {
        assertEquals(120.seconds, Backoff.delayFor(6, noJitter, Random(0)))
        assertEquals(120.seconds, Backoff.delayFor(10_000, noJitter, Random(0)))
    }

    @Test
    fun `the no-fill floor raises short delays only`() {
        assertEquals(30.seconds, Backoff.delayFor(1, noJitter, Random(0), floor = 30.seconds))
        assertEquals(40.seconds, Backoff.delayFor(4, noJitter, Random(0), floor = 30.seconds))
    }

    @Test
    fun `attempts are 1-based`() {
        assertThrows(IllegalArgumentException::class.java) { Backoff.delayFor(0, noJitter, Random(0)) }
    }
}
