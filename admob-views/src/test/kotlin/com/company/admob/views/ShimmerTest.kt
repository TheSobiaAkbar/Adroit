package com.company.admob.views

import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import com.company.admob.AdError
import com.company.admob.BannerState
import com.company.admob.InternalCompanyAdMobApi
import com.company.admob.NativeStyle
import com.company.admob.views.internal.ViewsRuntime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

/** The loading shimmer of banners and native ads (PROMPT 29C), driven by each ad's `showLoading`. */
@OptIn(InternalCompanyAdMobApi::class)
@RunWith(RobolectricTestRunner::class)
class ShimmerTest {
    private lateinit var client: TestAdsClient
    private lateinit var controller: ActivityController<FragmentActivity>
    private lateinit var container: FrameLayout
    private var showsLoading = true

    @Before
    fun setUp() {
        client = TestAdsClient().install()
        ViewsRuntime.showsLoadingOverride = { showsLoading }
        controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        container = FrameLayout(controller.get()).also { controller.get().setContentView(it) }
    }

    @After
    fun tearDown() {
        ViewsRuntime.showsLoadingOverride = null
        TestAdsClient.uninstall()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun shimmerIn(view: View): AdShimmerView? = when (view) {
        is AdShimmerView -> view
        is android.view.ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { shimmerIn(view.getChildAt(it)) }
        else -> null
    }

    @Test
    fun `a native ad shows a shimmer until it's rendered, which replaces it`() {
        client.native.deliverImmediately = false
        container.attachNativeAd(controller.get(), FEED)
        assertTrue(shimmerIn(container) != null)
        assertEquals(View.VISIBLE, container.visibility)

        client.native.deliverPending()
        idle()
        assertNull(shimmerIn(container))
        assertEquals(1, client.native.renders.size)
    }

    @Test
    fun `a failed native load removes the shimmer and hides the container`() {
        client.native.deliverImmediately = false
        client.native.failWith = AdError.NoFill()
        container.attachNativeAd(controller.get(), FEED)
        client.native.deliverPending()
        idle()
        assertNull(shimmerIn(container))
        assertEquals(View.GONE, container.visibility)
    }

    @Test
    fun `without showLoading there's no shimmer`() {
        showsLoading = false
        client.native.deliverImmediately = false
        container.attachNativeAd(controller.get(), FEED)
        assertNull(shimmerIn(container))
    }

    @Test
    fun `the holder shows a shimmer while it loads, also in a new container after a rotation`() {
        client.native.deliverImmediately = false
        val holder = NativeAdHolder(FEED)
        holder.bindTo(container, controller.get())
        assertTrue(shimmerIn(container) != null)

        val second = FrameLayout(controller.get())
        holder.bindTo(second, controller.get())
        assertTrue("the load goes on; the new container shows the shimmer", shimmerIn(second) != null)
        client.native.deliverPending()
        idle()
        assertNull(shimmerIn(second))
        assertEquals(1, client.native.renders.size)
    }

    @Test
    fun `a banner shows a shimmer over it while Loading, gone once Loaded or Failed, back when Loading again`() {
        val handle = container.attachBanner(controller.get(), HOME)
        idle()
        val shimmer = checkNotNull(shimmerIn(handle.view))
        assertEquals(View.VISIBLE, shimmer.visibility)

        client.banner.live.single().state.value = BannerState.Loaded
        idle()
        assertEquals(View.GONE, shimmer.visibility)

        client.banner.live.single().state.value = BannerState.Loading
        idle()
        assertEquals(View.VISIBLE, shimmer.visibility)

        client.banner.live.single().state.value = BannerState.Failed(AdError.NoFill())
        idle()
        assertEquals(View.GONE, shimmer.visibility)
    }

    @Test
    fun `the shimmer takes the native template's height, and a banner's height`() {
        val shimmer = AdShimmerView(controller.get())
        val density = controller.get().resources.displayMetrics.density
        fun measuredHeight(): Int {
            shimmer.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
            return shimmer.measuredHeight
        }
        shimmer.showNative(NativeStyle.SMALL)
        assertEquals((72 * density).toInt(), measuredHeight())
        shimmer.showNative(NativeStyle.LARGE)
        assertEquals((378 * density).toInt(), measuredHeight())
        shimmer.showBanner(150)
        assertEquals(150, measuredHeight())
    }
}
