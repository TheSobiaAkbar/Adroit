package com.company.admob.internal.fullscreen

import com.company.admob.AdAvailability
import com.company.admob.AdError
import com.company.admob.AdsState
import com.company.admob.UnavailableReason
import com.company.admob.config.PreloadStrategy
import com.company.admob.config.TestIds
import com.company.admob.events.AdEvent
import com.company.admob.fakes.FakeAdsSdk.ScriptedLoad
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FullScreenLoadTest {
    private fun onDemand() = engineConfig {
        placements {
            interstitial(LEVEL_END, TestIds.unit(1)) { preload = PreloadStrategy.ON_DEMAND; retry = EXACT_RETRY }
        }
    }

    @Test
    fun `preload before Ready makes no request, then a single load once Ready`() = runTest {
        val h = EngineHarness(this, onDemand())
        h.engine.preload(LEVEL_END)
        h.engine.preload(LEVEL_END)
        h.run(10_000)
        assertTrue(h.adsSdk.loadCalls.isEmpty())

        h.ready()
        assertEquals(1, h.adsSdk.loadCalls.size)
        assertEquals(AdAvailability.Ready, h.engine.availability(LEVEL_END).value)
        assertTrue(h.engine.isReady(LEVEL_END))
    }

    @Test
    fun `AFTER_READY placements load when Ready, staggered by 250 ms`() = runTest {
        val h = EngineHarness(this)
        val loadTimes = mutableListOf<Long>()
        h.adsSdk.onLoad = { loadTimes += h.clock.elapsedRealtimeMillis() }
        h.ready()
        h.run(250)

        assertEquals(listOf(h.adUnit(LEVEL_END), h.adUnit(MENU)), h.adsSdk.loadCalls.map { it.adUnitId })
        assertEquals(listOf(0L, 250L), loadTimes)
    }

    @Test
    fun `only one load in flight per slot`() = runTest {
        val h = EngineHarness(this, onDemand())
        h.adsSdk.defaultLoad = ScriptedLoad.Pending
        h.ready()
        h.engine.preload(LEVEL_END)
        h.engine.preload(LEVEL_END)
        h.run(1_000)

        assertEquals(1, h.adsSdk.loadCalls.size)
        assertEquals(AdAvailability.Loading, h.engine.availability(LEVEL_END).value)
    }

    @Test
    fun `placements sharing an ad unit share one load and one cached ad`() = runTest {
        val h = EngineHarness(
            this,
            engineConfig {
                placements {
                    interstitial(LEVEL_END, TestIds.unit(1))
                    interstitial(MENU, TestIds.unit(1))
                }
            },
        )
        h.ready()
        h.run(1_000)

        assertEquals(1, h.adsSdk.loadCalls.size)
        assertSame(h.engine.availability(LEVEL_END), h.engine.availability(MENU))
        assertTrue(h.engine.isReady(MENU))
    }

    @Test
    fun `NoFill retries no sooner than 30 s`() = runTest {
        val h = EngineHarness(this, onDemand())
        h.adsSdk.enqueueLoad(h.adUnit(LEVEL_END), ScriptedLoad.Failure(AdError.NoFill(), retryable = true))
        h.ready()
        h.engine.preload(LEVEL_END)
        h.run()
        assertEquals(AdAvailability.Failed(AdError.NoFill()), h.engine.availability(LEVEL_END).value)

        h.run(29_999)
        assertEquals(1, h.adsSdk.loadCalls.size)
        h.run(1)
        assertEquals(2, h.adsSdk.loadCalls.size)
        assertEquals(AdAvailability.Ready, h.engine.availability(LEVEL_END).value)
    }

    @Test
    fun `network errors retry after 5, 10 and 20 s`() = runTest {
        val h = EngineHarness(this, onDemand())
        val unit = h.adUnit(LEVEL_END)
        repeat(3) { h.adsSdk.enqueueLoad(unit, ScriptedLoad.Failure(AdError.NetworkError(), retryable = true)) }
        val loadTimes = mutableListOf<Long>()
        h.adsSdk.onLoad = { loadTimes += h.clock.elapsedRealtimeMillis() }
        h.ready()
        h.engine.preload(LEVEL_END)
        h.run()
        h.run(5_000)
        h.run(10_000)
        h.run(20_000)

        assertEquals(listOf(0L, 5_000L, 15_000L, 35_000L), loadTimes)
        assertEquals(AdAvailability.Ready, h.engine.availability(LEVEL_END).value)
        val failures = h.events.recent().filterIsInstance<AdEvent.LoadFailed>()
        assertEquals(listOf(1, 2, 3), failures.map { it.attempt })
        assertTrue(failures.all { it.willRetry })
    }

    @Test
    fun `a non-retryable error isn't retried`() = runTest {
        val h = EngineHarness(this, onDemand())
        h.adsSdk.defaultLoad = ScriptedLoad.Failure(AdError.LoadFailed(), retryable = false)
        h.ready()
        h.engine.preload(LEVEL_END)
        h.run(600_000)

        assertEquals(1, h.adsSdk.loadCalls.size)
        assertFalse(h.events.recent().filterIsInstance<AdEvent.LoadFailed>().single().willRetry)
    }

    @Test
    fun `exhausted retries stay Failed until an explicit preload resets them`() = runTest {
        val h = EngineHarness(
            this,
            engineConfig {
                placements {
                    interstitial(LEVEL_END, TestIds.unit(1)) {
                        preload = PreloadStrategy.ON_DEMAND
                        retry = EXACT_RETRY.copy(maxAttempts = 2)
                    }
                }
            },
        )
        h.adsSdk.defaultLoad = ScriptedLoad.Failure(AdError.NetworkError(), retryable = true)
        h.ready()
        h.engine.preload(LEVEL_END)
        h.run()
        h.run(600_000)
        assertEquals("1 load + 2 retries", 3, h.adsSdk.loadCalls.size)
        assertTrue(h.engine.availability(LEVEL_END).value is AdAvailability.Failed)

        h.adsSdk.defaultLoad = ScriptedLoad.Success()
        h.engine.preload(LEVEL_END)
        h.run()
        assertEquals(4, h.adsSdk.loadCalls.size)
        assertEquals(AdAvailability.Ready, h.engine.availability(LEVEL_END).value)
    }

    @Test
    fun `no ad request is ever made unless readiness allows it`() = runTest {
        val h = EngineHarness(this, onDemand())
        h.adsSdk.onLoad = { if (!h.readiness.canLoad()) fail("load requested in ${h.readiness.state.value}") }
        h.adsSdk.enqueueLoad(h.adUnit(LEVEL_END), ScriptedLoad.Failure(AdError.NetworkError(), retryable = true))

        h.engine.preload(LEVEL_END)
        h.show()
        h.run(60_000)
        h.ready()
        h.engine.preload(LEVEL_END)
        h.run()
        assertEquals("the first load failed and scheduled a retry", 1, h.adsSdk.loadCalls.size)

        // The library leaves Ready without a block notification; the due retry must not request.
        h.readiness.state.value = AdsState.InitializingSdk
        h.run(60_000)
        h.show()
        h.run()
        assertEquals(1, h.adsSdk.loadCalls.size)

        h.readiness.state.value = AdsState.Unavailable(UnavailableReason.CONSENT_NOT_OBTAINED)
        h.engine.onAdsBlocked(UnavailableReason.CONSENT_NOT_OBTAINED)
        h.engine.preload(LEVEL_END)
        h.run(600_000)
        assertEquals(1, h.adsSdk.loadCalls.size)
    }

    @Test
    fun `a cached ad expires after its TTL in the foreground, and a new one loads`() = runTest {
        val h = EngineHarness(this, onDemand())
        h.ready()
        h.engine.preload(LEVEL_END)
        h.run()
        val first = h.lastAd()

        h.run(55 * 60_000L)
        assertEquals("usable exactly at the TTL", 1, h.adsSdk.loadCalls.size)
        h.run(1)
        assertTrue(first.released)
        assertEquals(2, h.adsSdk.loadCalls.size)
        assertEquals(1, h.events.recent().filterIsInstance<AdEvent.Expired>().size)
    }

    @Test
    fun `clear drops the cached ad and cancels the retry`() = runTest {
        val h = EngineHarness(this, onDemand())
        h.ready()
        h.engine.preload(LEVEL_END)
        h.run()
        val ad = h.lastAd()
        h.engine.clear(LEVEL_END)
        assertTrue(ad.released)
        assertEquals(AdAvailability.Unavailable, h.engine.availability(LEVEL_END).value)

        h.adsSdk.defaultLoad = ScriptedLoad.Failure(AdError.NetworkError(), retryable = true)
        h.engine.preload(LEVEL_END)
        h.run()
        h.engine.clear(LEVEL_END)
        h.run(600_000)
        assertEquals(2, h.adsSdk.loadCalls.size)
    }

    @Test
    fun `unknown and disabled placements`() = runTest {
        val h = EngineHarness(
            this,
            engineConfig { placements { interstitial(LEVEL_END, TestIds.unit(1)) { enabled = false } } },
        )
        h.ready()
        assertEquals(AdAvailability.Disabled, h.engine.availability(LEVEL_END).value)
        assertEquals(AdAvailability.Unavailable, h.engine.availability(MENU).value)
        h.engine.preload(LEVEL_END)
        h.engine.preload(MENU)
        h.run()
        assertTrue(h.adsSdk.loadCalls.isEmpty())
        assertFalse(h.engine.isReady(MENU))
    }
}
