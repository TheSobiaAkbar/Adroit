package com.company.admob.internal.native

import com.company.admob.AdError
import com.company.admob.AdFormat
import com.company.admob.AdsState
import com.company.admob.NativeAdHandleState
import com.company.admob.NativeAdResult
import com.company.admob.NativePlacement
import com.company.admob.UnavailableReason
import com.company.admob.config.PreloadStrategy
import com.company.admob.config.TestIds
import com.company.admob.events.AdEvent
import com.company.admob.fakes.FakeAdsSdk.ScriptedLoad
import com.company.admob.fakes.FakeLoadedNativeAd
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
class NativeLoaderTest {
    private fun kotlinx.coroutines.test.TestScope.pooled(size: Int = 1, preload: PreloadStrategy = PreloadStrategy.AFTER_READY) =
        NativeHarness(
            this,
            nativeConfig {
                placements {
                    native(FEED, TestIds.unit(9)) {
                        retry = EXACT_NATIVE_RETRY
                        poolSize = size
                        this.preload = preload
                    }
                }
            },
        )

    private fun NativeHarness.events(): List<AdEvent.PlacementEvent> =
        events.recent().filterIsInstance<AdEvent.PlacementEvent>().filter { it.format == AdFormat.NATIVE }

    @Test
    fun `nothing loads before Ready, then the pool fills to its size, one request at a time`() = runTest {
        val n = pooled(size = 3)
        assertEquals(0, n.loads())
        n.ready()
        assertEquals(3, n.loads())
        assertEquals(3, n.engine.pooledCount(FEED))
        assertTrue("every request is for a single ad", n.adsSdk.loadCalls.all { it.format == AdFormat.NATIVE })
        assertEquals("one ad per request", n.loads(), n.ads.size)
    }

    @Test
    fun `a pool hit is served without an SDK call, then the pool refills`() = runTest {
        val n = pooled()
        n.ready()
        val pooledAd = n.ads.single()
        n.adsSdk.defaultLoad = ScriptedLoad.Pending
        n.load()

        val handle = n.handles().single()
        assertEquals(pooledAd, handle.ad)
        assertEquals("only the refill reached the SDK", 2, n.loads())
    }

    @Test
    fun `an empty pool - the caller gets the next load`() = runTest {
        val n = pooled(preload = PreloadStrategy.NONE)
        n.ready()
        assertEquals(0, n.loads())
        n.load()
        assertEquals(1, n.loads())
        assertEquals(n.ads.single(), n.handles().single().ad)
        assertEquals("NONE never refills", 1, n.loads())
    }

    @Test
    fun `a failed load fails the waiting caller, retries only while the pool is below target`() = runTest {
        val n = pooled(preload = PreloadStrategy.NONE)
        n.ready()
        n.adsSdk.defaultLoad = ScriptedLoad.Failure(AdError.NoFill(), retryable = true)
        n.load()
        assertEquals(listOf<NativeAdResult>(NativeAdResult.Failed(AdError.NoFill())), n.results)
        n.run(10.minutes.inWholeMilliseconds)
        assertEquals("nothing wanted the pool: no retry", 1, n.loads())

        val p = pooled(size = 2)
        p.adsSdk.defaultLoad = ScriptedLoad.Failure(AdError.NetworkError(), retryable = true)
        p.ready()
        assertEquals(1, p.loads())
        p.adsSdk.defaultLoad = ScriptedLoad.Success()
        p.run(10_000)
        assertEquals("retried after 10 s, then filled", 3, p.loads())
        assertEquals(2, p.engine.pooledCount(FEED))
    }

    @Test
    fun `ON_DEMAND - nothing at Ready, then the first request turns refilling on`() = runTest {
        val n = pooled(preload = PreloadStrategy.ON_DEMAND)
        n.ready()
        assertEquals(0, n.loads())
        n.load()
        assertEquals(1, n.handles().size)
        assertEquals("the first load, then the refill", 2, n.loads())
        assertEquals(1, n.engine.pooledCount(FEED))
    }

    @Test
    fun `NONE - preload fills once, and a handout isn't refilled`() = runTest {
        val n = pooled(size = 2, preload = PreloadStrategy.NONE)
        n.ready()
        n.engine.preload(FEED)
        n.run()
        assertEquals(2, n.engine.pooledCount(FEED))
        n.load()
        n.load()
        assertEquals(2, n.handles().size)
        assertEquals(2, n.loads())
        assertEquals(0, n.engine.pooledCount(FEED))
    }

    @Test
    fun `pooled ads expire after 55 min - destroyed, reported, refilled`() = runTest {
        val n = pooled()
        n.ready()
        val stale = n.ads.single()
        n.run(55.minutes.inWholeMilliseconds + 1)
        assertEquals(1, stale.destroyCount)
        assertEquals(1, n.events().count { it is AdEvent.Expired })
        assertEquals(2, n.loads())
        assertEquals(1, n.engine.pooledCount(FEED))
    }

    @Test
    fun `expired while in the background - dropped at the next load instead`() = runTest {
        val n = pooled()
        n.ready()
        val stale = n.ads.single()
        n.foreground.moveToBackground()
        n.run(60.minutes.inWholeMilliseconds)
        assertEquals(0, stale.destroyCount)
        n.load()
        assertEquals(1, stale.destroyCount)
        assertTrue(n.handles().single().ad !== stale)
    }

    @Test
    fun `blocked - pool destroyed, live handles blocked, waiters failed, late loads destroyed`() = runTest {
        val n = pooled(size = 2)
        n.ready()
        n.load()
        val handle = n.handles().single()
        n.adsSdk.defaultLoad = ScriptedLoad.Pending
        n.run()
        val pendingLoads = n.loads()

        n.readiness.state.value = AdsState.Unavailable(UnavailableReason.AD_FREE_USER)
        n.engine.onAdsBlocked(UnavailableReason.AD_FREE_USER)
        n.run()

        assertEquals(0, n.engine.pooledCount(FEED))
        assertEquals(NativeAdHandleState.Blocked(UnavailableReason.AD_FREE_USER), handle.state.value)
        assertTrue(n.ads.all { it.destroyCount == 1 })
        n.load()
        assertEquals(NativeAdResult.Failed(AdError.Disabled(UnavailableReason.AD_FREE_USER)), n.results.last())
        assertEquals("no request while blocked", pendingLoads, n.loads())

        handle.destroy()
        assertEquals(NativeAdHandleState.Destroyed, handle.state.value)
        assertEquals("destroyed once", 1, (handle.ad as FakeLoadedNativeAd).destroyCount)
    }

    @Test
    fun `every SDK ad is destroyed exactly once - handout, expiry, block and stale paths`() = runTest {
        val n = pooled(size = 2)
        n.ready() // ad0, ad1 pooled
        n.load() // ad0 handed out; ad2 refills the pool
        n.handles().single().destroy()
        n.run(55.minutes.inWholeMilliseconds + 1) // ad1 and ad2 expire; ad3 and ad4 refill

        n.adsSdk.enqueueLoad(TestIds.unit(9), ScriptedLoad.Success(), delayMs = 5_000)
        n.adsSdk.enqueueLoad(TestIds.unit(9), ScriptedLoad.Success(), delayMs = 5_000)
        n.load()
        n.load() // ad3 and ad4 handed out; the refills arrive 5 s apart
        n.run(5_000) // ad5 pooled, ad6 in flight
        n.engine.onAdsBlocked(UnavailableReason.CONSENT_NOT_OBTAINED) // ad5 destroyed, ad3 and ad4 blocked
        n.run(10_000) // ad6 arrives stale and is destroyed
        n.handles().forEach { it.destroy() }

        assertEquals(7, n.ads.size)
        n.ads.forEachIndexed { index, ad -> assertEquals("ad$index", 1, ad.destroyCount) }
    }

    @Test
    fun `an ad nobody wants any more - destroyed on arrival`() = runTest {
        val n = pooled(preload = PreloadStrategy.NONE)
        n.ready()
        n.adsSdk.enqueueLoad(TestIds.unit(9), ScriptedLoad.Success(), delayMs = 1_000)
        val withdraw = n.engine.load(FEED) { n.results += it }
        n.run()
        withdraw()
        n.run(1_000)
        assertTrue(n.results.isEmpty())
        assertEquals(1, n.ads.single().destroyCount)
    }

    @Test
    fun `suspend load returns the handle, a cancelled caller's ad is destroyed or pooled, never leaked`() = runTest {
        val n = pooled(preload = PreloadStrategy.NONE)
        n.ready()
        val result = async { n.api.load(FEED) }
        n.run()
        assertTrue(result.await() is NativeAdResult.Loaded)

        n.adsSdk.defaultLoad = ScriptedLoad.Pending
        val cancelled = async { n.api.load(FEED) }
        n.run()
        cancelled.cancel()
        n.run()
        assertEquals(2, n.loads())
    }

    @Test
    fun `not ready, unknown or disabled placement - Failed at once`() = runTest {
        val n = NativeHarness(
            this,
            nativeConfig {
                placements {
                    native(FEED, TestIds.unit(9))
                    native(NativePlacement("off"), TestIds.unit(10)) { enabled = false }
                }
            },
        )
        n.load()
        n.ready()
        n.load(NativePlacement("nope"))
        n.load(NativePlacement("off"))
        assertEquals(AdError.NotInitialized, (n.results[0] as NativeAdResult.Failed).error)
        assertTrue((n.results[1] as NativeAdResult.Failed).error is AdError.InvalidConfiguration)
        assertEquals(AdError.Disabled(UnavailableReason.DISABLED_BY_CONFIG), (n.results[2] as NativeAdResult.Failed).error)
    }

    @Test
    fun `load events - requested, loaded, failed with attempt and retry`() = runTest {
        val n = pooled()
        n.adsSdk.enqueueLoad(TestIds.unit(9), ScriptedLoad.Failure(AdError.NetworkError(), retryable = true))
        n.ready()
        n.run(10_000)
        assertEquals(
            listOf("LoadRequested", "LoadFailed", "LoadRequested", "Loaded"),
            n.events().map { it::class.simpleName },
        )
        val failed = n.events().filterIsInstance<AdEvent.LoadFailed>().single()
        assertEquals(1, failed.attempt)
        assertTrue(failed.willRetry)
        assertTrue(n.events().all { it.placementKey == FEED.key })
    }
}
