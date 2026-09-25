package com.company.admob.internal.banner

import com.company.admob.BannerState
import com.company.admob.UnavailableReason
import com.company.admob.config.TestIds
import com.company.admob.config.remote.AdOverrides
import com.company.admob.config.remote.PlacementOverride
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BannerRemoteOverridesTest {
    private fun BannerHarness.override(overrides: AdOverrides) {
        live.update(effective.withOverrides(overrides))
        run()
    }

    @Test
    fun `a remotely disabled banner is hidden and its ad destroyed, then loads again when re-enabled`() = runTest {
        val b = BannerHarness(this)
        val handle = b.create()
        b.layout(400)
        b.ready()
        b.views.single().simulateLoaded()
        b.run()
        assertEquals(BannerState.Loaded, handle.state.value)

        b.override(AdOverrides(placements = mapOf("home" to PlacementOverride(enabled = false))))

        assertEquals(BannerState.Hidden(UnavailableReason.DISABLED_BY_REMOTE), handle.state.value)
        assertTrue(b.views.single().destroyed)

        b.override(AdOverrides())

        assertEquals(2, b.loads())
        assertEquals(BannerState.Loading, handle.state.value)
    }

    @Test
    fun `a disabled banner format hides existing banners, and new ones start hidden`() = runTest {
        val b = BannerHarness(this)
        val handle = b.create()
        b.layout(400)
        b.ready()
        b.override(AdOverrides(disabledFormats = setOf(com.company.admob.AdFormat.BANNER)))
        assertEquals(BannerState.Hidden(UnavailableReason.DISABLED_BY_REMOTE), handle.state.value)

        val later = b.create()
        b.run()
        assertEquals(BannerState.Hidden(UnavailableReason.DISABLED_BY_REMOTE), later.state.value)
        assertEquals(1, b.loads())
    }

    @Test
    fun `a new ad unit replaces the ad at once`() = runTest {
        val b = BannerHarness(this)
        b.create()
        b.layout(400)
        b.ready()
        val first = b.views.single()

        b.override(AdOverrides(placements = mapOf("home" to PlacementOverride(adUnitId = TestIds.unit(8)))))

        assertTrue(first.destroyed)
        assertEquals(TestIds.unit(8), b.views.last().loadCalls.single().adUnitId)
    }
}
