package io.github.thesobiaakbar.adroit.internal.banner

import io.github.thesobiaakbar.adroit.AdsState
import io.github.thesobiaakbar.adroit.BannerState
import io.github.thesobiaakbar.adroit.UnavailableReason
import io.github.thesobiaakbar.adroit.config.TestIds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Banners loaded ahead of their view (`AdSlots.load` of a banner), and the banners that take them. */
@RunWith(RobolectricTestRunner::class)
class BannerPreloadTest {
    /** The window's width in dp: what a preload is sized for, and what a full-width banner is. */
    private val windowDp: Int get() = RuntimeEnvironment.getApplication().resources.displayMetrics.let {
        (it.widthPixels / it.density).toInt()
    }

    private fun BannerHarness.preload() {
        preloader.preload(checkNotNull(effective.placement(HOME)))
        run()
    }

    @Test
    fun `a preloaded banner shows at once in the next banner for its placement, without another request`() = runTest {
        val b = BannerHarness(this)
        b.ready()
        b.preload()
        val preload = b.adsSdk.bannerPreloads.single()
        assertEquals(TestIds.unit(7), preload.adUnitId)
        assertEquals("the window less the frame's margins", windowDp - 2 * b.margin, preload.widthDp)
        preload.succeed()
        b.run()

        val handle = b.create()
        b.layout(windowDp)
        assertEquals("no view load of its own", 0, b.loads())
        assertEquals(BannerState.Loaded, handle.state.value)
        val attached = checkNotNull(preload.banner.attachedView)
        assertSame(handle.view, attached.view.parent)
        assertFalse(b.preloader.has(HOME.key))
    }

    @Test
    fun `a banner created while its preload is loading waits for it`() = runTest {
        val b = BannerHarness(this)
        b.ready()
        b.preload()
        val handle = b.create()
        b.layout(windowDp)
        assertEquals("it waits: no second request", 0, b.loads())
        assertEquals(BannerState.Loading, handle.state.value)

        b.adsSdk.bannerPreloads.single().succeed()
        b.run()
        assertEquals(BannerState.Loaded, handle.state.value)
        assertEquals(1, b.adsSdk.bannerPreloads.size)
        assertEquals(0, b.loads())
    }

    @Test
    fun `if the preload fails, the waiting banner loads its own`() = runTest {
        val b = BannerHarness(this)
        b.ready()
        b.preload()
        b.create()
        b.layout(windowDp)
        b.adsSdk.bannerPreloads.single().fail()
        b.run()
        assertEquals(1, b.loads())
    }

    @Test
    fun `a banner of another width loads its own, and the preload that doesn't fit is dropped`() = runTest {
        val b = BannerHarness(this)
        b.ready()
        b.preload()
        val preload = b.adsSdk.bannerPreloads.single()
        preload.succeed()
        b.run()
        b.create()
        b.layout(windowDp / 2)
        assertEquals(1, b.loads())
        assertTrue(preload.banner.destroyedUnshown)
    }

    @Test
    fun `asking again while it's loaded or loading makes no second request`() = runTest {
        val b = BannerHarness(this)
        b.ready()
        b.preload()
        b.preload()
        b.adsSdk.bannerPreloads.single().succeed()
        b.run()
        b.preload()
        assertEquals(1, b.adsSdk.bannerPreloads.size)
    }

    @Test
    fun `nothing preloads before ads can load, and a preload is dropped when ads become unavailable`() = runTest {
        val b = BannerHarness(this)
        b.preload()
        assertEquals(0, b.adsSdk.bannerPreloads.size)

        b.ready()
        b.preload()
        val preload = b.adsSdk.bannerPreloads.single()
        preload.succeed()
        b.run()
        b.readiness.state.value = AdsState.Unavailable(UnavailableReason.AD_FREE_USER)
        b.run()
        assertTrue(preload.banner.destroyedUnshown)
        assertFalse(b.preloader.has(HOME.key))
    }

    @Test
    fun `a banner destroyed while waiting leaves the preload for the next one`() = runTest {
        val b = BannerHarness(this)
        b.ready()
        b.preload()
        val first = b.create()
        b.layout(windowDp)
        first.destroy()
        val preload = b.adsSdk.bannerPreloads.single()
        preload.succeed()
        b.run()
        assertTrue("kept", b.preloader.has(HOME.key))
        assertFalse(preload.banner.destroyedUnshown)

        val second = b.create()
        b.layout(windowDp)
        assertEquals(BannerState.Loaded, second.state.value)
        assertEquals(0, b.loads())
    }
}
