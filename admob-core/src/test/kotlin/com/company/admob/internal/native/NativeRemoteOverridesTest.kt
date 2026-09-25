package com.company.admob.internal.native

import com.company.admob.AdError
import com.company.admob.NativeAdHandleState
import com.company.admob.NativeAdResult
import com.company.admob.UnavailableReason
import com.company.admob.config.TestIds
import com.company.admob.config.remote.AdOverrides
import com.company.admob.config.remote.PlacementOverride
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NativeRemoteOverridesTest {
    private fun TestScope.harness() = NativeHarness(
        this,
        nativeConfig { placements { native(FEED, TestIds.unit(9)) { retry = EXACT_NATIVE_RETRY } } },
    )

    private fun NativeHarness.override(overrides: AdOverrides) {
        live.update(effective.withOverrides(overrides))
        run()
    }

    @Test
    fun `a remotely disabled placement destroys its pool, blocks its live handles, and refuses loads`() = runTest {
        val n = harness()
        n.ready()
        n.load()
        n.run()
        val handle = n.handles().single()
        val pooled = n.ads.last()

        n.override(AdOverrides(placements = mapOf("feed" to PlacementOverride(enabled = false))))

        assertEquals(NativeAdHandleState.Blocked(UnavailableReason.DISABLED_BY_REMOTE), handle.state.value)
        assertTrue(pooled.isDestroyed)
        n.load()
        n.run()
        assertEquals(NativeAdResult.Failed(AdError.Disabled(UnavailableReason.DISABLED_BY_REMOTE)), n.results.last())

        val loadsBefore = n.loads()
        n.override(AdOverrides())
        assertTrue("the pool refills once re-enabled", n.loads() > loadsBefore)
    }

    @Test
    fun `a new ad unit replaces the pool, keeping live handles`() = runTest {
        val n = harness()
        n.ready()
        n.load()
        n.run()
        val handle = n.handles().single()
        val pooled = n.ads.last()

        n.override(AdOverrides(placements = mapOf("feed" to PlacementOverride(adUnitId = TestIds.unit(10)))))

        assertTrue(pooled.isDestroyed)
        assertEquals(NativeAdHandleState.Active, handle.state.value)
        assertEquals(TestIds.unit(10), n.adsSdk.loadCalls.last().adUnitId)
    }
}
