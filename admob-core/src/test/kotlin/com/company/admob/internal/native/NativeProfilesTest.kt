package com.company.admob.internal.native

import com.company.admob.NativePlacement
import com.company.admob.NativeStyle
import com.company.admob.config.TestIds
import com.company.admob.config.remote.AdOverrides
import com.company.admob.config.remote.PlacementOverride
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Native rotation and style (05_AD_PROFILES.md §4.1, §4.5). */
@RunWith(RobolectricTestRunner::class)
class NativeProfilesTest {
    private val ids = listOf(TestIds.unit(1), TestIds.unit(2))

    @Test
    fun `each load takes the next ad unit, and handles report the unit their ad came from`() = runTest {
        val h = NativeHarness(this, nativeConfig { placements { native(FEED, ids) } })
        h.ready()
        repeat(3) { h.load() }
        assertEquals(listOf(ids[0], ids[1], ids[0], ids[1]), h.adsSdk.loadCalls.map { it.adUnitId })
        val loaded = h.events.recent().filterIsInstance<com.company.admob.events.AdEvent.Loaded>().map { it.adUnitId }
        assertEquals(listOf(ids[0], ids[1], ids[0], ids[1]), loaded)
    }

    @Test
    fun `style comes from the placement, a remote override wins, and an unknown placement is MEDIUM`() = runTest {
        val h = NativeHarness(this, nativeConfig { placements { native(FEED, TestIds.unit(1)) { style = NativeStyle.SMALL } } })
        assertEquals(NativeStyle.SMALL, h.api.style(FEED))
        h.live.update(h.live.current.withOverrides(AdOverrides(placements = mapOf("feed" to PlacementOverride(nativeStyle = NativeStyle.LARGE)))))
        assertEquals(NativeStyle.LARGE, h.api.style(FEED))
        assertEquals(NativeStyle.MEDIUM, h.api.style(NativePlacement("other")))
    }
}
