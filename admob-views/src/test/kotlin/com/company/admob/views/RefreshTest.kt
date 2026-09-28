package com.company.admob.views

import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import com.company.admob.AdError
import com.company.admob.BannerState
import com.company.admob.views.internal.ViewsRuntime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

/** One request per ad view, and refresh (PROMPT 29D). */
@OptIn(com.company.admob.InternalCompanyAdMobApi::class)
@RunWith(RobolectricTestRunner::class)
class RefreshTest {
    private lateinit var client: TestAdsClient
    private lateinit var controller: ActivityController<FragmentActivity>
    private lateinit var root: FrameLayout

    @Before
    fun setUp() {
        client = TestAdsClient().install()
        ViewsRuntime.showsLoadingOverride = { true }
        controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        root = FrameLayout(controller.get()).also { controller.get().setContentView(it) }
    }

    @After
    fun tearDown() {
        ViewsRuntime.showsLoadingOverride = null
        TestAdsClient.uninstall()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun nativeView(): AdNativeView =
        AdNativeView(controller.get()).also {
            it.setPlacement(FEED)
            root.addView(it)
            idle()
        }

    private fun hasShimmer(view: android.view.ViewGroup) = (0 until view.childCount).any { view.getChildAt(it) is AdShimmerView }

    // --- Native view ---

    @Test
    fun `a native view never requests a second ad while it has one or one is loading`() {
        client.native.deliverImmediately = false
        val view = AdNativeView(controller.get()).also { it.setPlacement(FEED) }
        root.addView(view)
        view.load()
        view.load()
        assertEquals("loading: no second request", 1, client.native.loadCount)
        client.native.deliverPending()
        idle()
        view.load()
        assertEquals("shown: no second request", 1, client.native.loadCount)
    }

    @Test
    fun `refresh keeps the shown native ad, without shimmer, until the new one replaces it`() {
        val view = nativeView()
        val first = client.native.handles.single()
        client.native.deliverImmediately = false
        view.load(refresh = true)
        view.load(refresh = true)
        assertEquals("one refresh at a time", 2, client.native.loadCount)
        assertTrue("the first ad is still on screen", !hasShimmer(view) && view.childCount == 1 && !first.isDestroyed)

        client.native.deliverPending()
        idle()
        assertEquals(2, client.native.renders.size)
        assertSame(client.native.handles.last(), client.native.renders.last().handle)
        assertEquals("replaced, then destroyed", 1, first.destroyCount)
    }

    @Test
    fun `a failed refresh keeps the shown native ad`() {
        val view = nativeView()
        val first = client.native.handles.single()
        client.native.failWith = AdError.NoFill()
        view.load(refresh = true)
        idle()
        assertEquals(View.VISIBLE, view.visibility)
        assertEquals(0, first.destroyCount)
        assertEquals(1, client.native.renders.size)
    }

    @Test
    fun `after a failed load, load() requests again`() {
        client.native.failWith = AdError.NoFill()
        val view = nativeView()
        assertEquals(View.GONE, view.visibility)
        client.native.failWith = null
        view.load()
        idle()
        assertEquals(2, client.native.loadCount)
        assertEquals(1, client.native.renders.size)
    }

    // --- Holder and attach helpers ---

    @Test
    fun `the holder never requests a second ad unless refreshed, and a refresh keeps the old ad until the new one`() {
        val holder = NativeAdHolder(FEED)
        holder.bindTo(root, controller.get())
        idle()
        holder.load()
        assertEquals(1, client.native.loadCount)
        val first = client.native.handles.single()

        client.native.deliverImmediately = false
        holder.load(refresh = true)
        assertTrue(!hasShimmer(root) && !first.isDestroyed)
        client.native.deliverPending()
        idle()
        assertEquals(2, client.native.renders.size)
        assertEquals(1, first.destroyCount)
    }

    @Test
    fun `attachNativeAd twice on one container loads once`() {
        client.native.deliverImmediately = false
        root.attachNativeAd(controller.get(), FEED)
        root.attachNativeAd(controller.get(), FEED)
        client.native.deliverPending()
        idle()
        root.attachNativeAd(controller.get(), FEED)
        assertEquals(1, client.native.loadCount)
    }

    @Test
    fun `attachBanner twice on one container returns the same banner, and a failed one is replaced`() {
        val first = root.attachBanner(controller.get(), HOME)
        assertSame(first, root.attachBanner(controller.get(), HOME))
        assertEquals(1, client.banner.created.size)

        client.banner.created.single().handle.state.value = BannerState.Failed(AdError.NoFill())
        val second = root.attachBanner(controller.get(), HOME)
        assertTrue(first !== second)
        assertEquals(1, client.banner.live.size)
    }

    // --- Banner view ---

    @Test
    fun `a banner view refresh loads unseen and swaps once loaded, keeping the old one on failure`() {
        val view = AdBannerView(controller.get()).also { it.setPlacement(HOME) }
        root.addView(view)
        idle()
        view.load()
        assertEquals("loading: no second banner", 1, client.banner.created.size)
        val first = client.banner.created.single().handle
        first.state.value = BannerState.Loaded
        idle()
        view.load()
        assertEquals("shown: no second banner", 1, client.banner.created.size)

        view.load(refresh = true)
        view.load(refresh = true)
        assertEquals("one refresh at a time", 2, client.banner.created.size)
        val second = client.banner.created.last().handle
        assertEquals(View.INVISIBLE, second.view.visibility)
        assertTrue(!first.isDestroyed)
        second.state.value = BannerState.Loaded
        idle()
        assertEquals(View.VISIBLE, second.view.visibility)
        assertTrue(first.isDestroyed)
        assertSame(second, view.bannerHandle)
        assertEquals("the replaced banner's destruction doesn't hide the view", View.VISIBLE, view.visibility)

        view.load(refresh = true)
        val third = client.banner.created.last().handle
        third.state.value = BannerState.Failed(AdError.NoFill())
        idle()
        assertTrue(third.isDestroyed)
        assertTrue(!second.isDestroyed)
        assertSame(second, view.bannerHandle)
        assertEquals(View.VISIBLE, view.visibility)

        second.state.value = BannerState.Failed(AdError.NoFill())
        idle()
        assertEquals("the shown banner still decides the view's visibility", View.GONE, view.visibility)
    }
}
