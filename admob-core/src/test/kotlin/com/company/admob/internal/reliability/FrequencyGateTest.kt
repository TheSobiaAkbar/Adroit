package com.company.admob.internal.reliability

import com.company.admob.AdFormat
import com.company.admob.AdPlacement
import com.company.admob.AppOpenPlacement
import com.company.admob.InterstitialPlacement
import com.company.admob.RewardedInterstitialPlacement
import com.company.admob.RewardedPlacement
import com.company.admob.config.FrequencyRule
import com.company.admob.fakes.FakeClock
import com.company.admob.internal.reliability.FrequencyDecision.Allowed
import com.company.admob.internal.reliability.FrequencyDecision.Capped
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class FrequencyGateTest {
    private val clock = FakeClock()
    private val levelEnd = InterstitialPlacement("level_end")
    private val menu = InterstitialPlacement("menu")
    private val extraLife = RewardedPlacement("extra_life")
    private val bonus = RewardedInterstitialPlacement("bonus")
    private val resume = AppOpenPlacement("resume")

    private fun gate(
        rules: Map<AdPlacement, FrequencyRule> = emptyMap(),
        global: Duration = Duration.ZERO,
        includeRewarded: Boolean = false,
    ) = FrequencyGate(clock, { rules[it] ?: FrequencyRule() }, global, FrequencyGate.globalFormats(includeRewarded))

    @Test
    fun `per-placement interval, with the remaining time as retryAfter`() {
        val gate = gate(mapOf(levelEnd to FrequencyRule(minInterval = 60.seconds)))
        assertEquals(Allowed, gate.check(levelEnd))
        gate.recordShown(levelEnd)

        assertEquals(Capped(60.seconds), gate.check(levelEnd))
        clock.advance(20.seconds)
        assertEquals(Capped(40.seconds), gate.check(levelEnd))
        assertEquals("other placements are unaffected", Allowed, gate.check(menu))
        clock.advance(40.seconds)
        assertEquals(Allowed, gate.check(levelEnd))
    }

    @Test
    fun `session cap`() {
        val gate = gate(mapOf(levelEnd to FrequencyRule(maxPerSession = 2), menu to FrequencyRule(maxPerSession = 0)))
        gate.recordShown(levelEnd)
        assertEquals(Allowed, gate.check(levelEnd))
        gate.recordShown(levelEnd)
        assertEquals(Capped(Duration.INFINITE), gate.check(levelEnd))
        assertEquals(Capped(Duration.INFINITE), gate.check(menu))
    }

    @Test
    fun `the global interval spans interstitial, rewarded interstitial and app open`() {
        val gate = gate(global = 30.seconds)
        gate.recordShown(levelEnd)

        assertEquals(Capped(30.seconds), gate.check(menu))
        assertEquals(Capped(30.seconds), gate.check(bonus))
        assertEquals(Capped(30.seconds), gate.check(resume))
        clock.advance(30.seconds)
        assertEquals(Allowed, gate.check(menu))
    }

    @Test
    fun `rewarded is exempt by default, both ways`() {
        val gate = gate(global = 30.seconds)
        gate.recordShown(extraLife)
        assertEquals("a rewarded show doesn't start the global interval", Allowed, gate.check(levelEnd))
        gate.recordShown(levelEnd)
        assertEquals("rewarded isn't checked against it", Allowed, gate.check(extraLife))
    }

    @Test
    fun `rewarded joins the global interval when opted in`() {
        val gate = gate(global = 30.seconds, includeRewarded = true)
        gate.recordShown(extraLife)
        assertEquals(Capped(30.seconds), gate.check(levelEnd))
        assertEquals(Capped(30.seconds), gate.check(extraLife))
    }

    @Test
    fun `retryAfter is the longest of the blocking waits`() {
        val gate = gate(mapOf(levelEnd to FrequencyRule(minInterval = 60.seconds)), global = 30.seconds)
        gate.recordShown(levelEnd)
        assertEquals(Capped(60.seconds), gate.check(levelEnd))
        clock.advance(10.seconds)
        assertEquals(Capped(20.seconds), gate.check(menu))
    }

    @Test
    fun `default global formats`() {
        assertEquals("Allowed", Allowed.toString())
        assertEquals(
            setOf(AdFormat.INTERSTITIAL, AdFormat.REWARDED_INTERSTITIAL, AdFormat.APP_OPEN),
            FrequencyGate.globalFormats(includeRewarded = false),
        )
        val defaultGate = FrequencyGate(clock, { FrequencyRule() }, 30.seconds)
        defaultGate.recordShown(extraLife)
        assertEquals(Allowed, defaultGate.check(levelEnd))
    }
}
