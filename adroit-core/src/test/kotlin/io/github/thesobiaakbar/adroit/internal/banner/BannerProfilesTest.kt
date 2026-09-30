package io.github.thesobiaakbar.adroit.internal.banner

import io.github.thesobiaakbar.adroit.BannerOptions
import io.github.thesobiaakbar.adroit.BannerSize
import io.github.thesobiaakbar.adroit.CollapsibleBannerPosition
import io.github.thesobiaakbar.adroit.config.AdEnvironment
import io.github.thesobiaakbar.adroit.config.TestIds
import io.github.thesobiaakbar.adroit.config.adMobConfig
import io.github.thesobiaakbar.adroit.config.remote.AdOverrides
import io.github.thesobiaakbar.adroit.config.remote.PlacementOverride
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Banner size from the placement (and remote style), collapsible first loads, and rotation (05_AD_PROFILES.md). */
@RunWith(RobolectricTestRunner::class)
class BannerProfilesTest {
    private val collapsibleBottom = BannerSize.Collapsible(CollapsibleBannerPosition.BOTTOM)

    private fun kotlinx.coroutines.test.TestScope.harness(
        size: BannerSize = BannerSize.AnchoredAdaptive,
        ids: List<String> = listOf(TestIds.unit(7)),
    ) = BannerHarness(
        this,
        adMobConfig {
            environment = AdEnvironment.STAGING
            testing {
                useDemoAdUnits = false
                testDeviceIds += "TEST-DEVICE"
            }
            placements { banner(HOME, ids) { this.size = size; retry = EXACT_BANNER_RETRY } }
        },
    ).apply { ready() }

    @Test
    fun `without a size in the code, the placement's size is used, and a size in the code wins`() = runTest {
        val h = harness(size = BannerSize.MediumRectangle)
        h.create()
        h.create(BannerOptions(size = BannerSize.Standard))
        h.layout(400)
        assertEquals(listOf(BannerSize.MediumRectangle, BannerSize.Standard), h.views.map { it.loadCalls.single().size })
    }

    @Test
    fun `a remote style applies to banners created afterwards`() = runTest {
        val h = harness()
        h.create()
        h.layout(400)
        h.live.update(h.live.current.withOverrides(AdOverrides(placements = mapOf("home" to PlacementOverride(bannerSize = BannerSize.Standard)))))
        h.create()
        h.layout(400)
        assertEquals(listOf(BannerSize.AnchoredAdaptive, BannerSize.Standard), h.views.map { it.loadCalls.first().size })
    }

    @Test
    fun `a collapsible banner asks for the collapsible format on its first load only`() = runTest {
        val h = harness(size = collapsibleBottom)
        h.create()
        h.layout(400)
        val view = h.views.single()
        assertEquals(CollapsibleBannerPosition.BOTTOM, view.loadCalls.single().extras.collapsible)
        view.simulateFailedToLoad()
        h.run(40_000) // the retry (at least 30 s after a no-fill) reloads the same view
        assertEquals(2, view.loadCalls.size)
        assertNull(view.loadCalls[1].extras.collapsible)
        assertEquals(collapsibleBottom, view.loadCalls[1].size)
    }

    @Test
    fun `a non-collapsible banner never sends the collapsible extra`() = runTest {
        val h = harness()
        h.create()
        h.layout(400)
        assertNull(h.views.single().loadCalls.single().extras.collapsible)
    }

    @Test
    fun `each new banner load takes the next ad unit of the list`() = runTest {
        val ids = listOf(TestIds.unit(7), TestIds.unit(8))
        val h = harness(ids = ids)
        repeat(3) { h.create() }
        h.layout(400)
        assertEquals(ids + ids.first(), h.views.map { it.loadCalls.single().adUnitId })
    }
}
