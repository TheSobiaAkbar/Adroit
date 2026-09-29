package com.company.admob.views

import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import com.company.admob.AdError
import com.company.admob.BannerState
import com.company.admob.InternalCompanyAdMobApi
import com.company.admob.NativeAdLayout
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

    private fun laidOut(shimmer: AdShimmerView): AdShimmerView {
        shimmer.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
        shimmer.layout(0, 0, shimmer.measuredWidth, shimmer.measuredHeight)
        return shimmer
    }

    /** The shimmer's drawn shapes: its card and blocks, recorded from a draw. */
    private fun drawn(shimmer: AdShimmerView): List<android.graphics.RectF> {
        val rects = mutableListOf<android.graphics.RectF>()
        val canvas = object : android.graphics.Canvas() {
            override fun drawRoundRect(rect: android.graphics.RectF, rx: Float, ry: Float, paint: android.graphics.Paint) {
                rects += android.graphics.RectF(rect)
            }
        }
        shimmer.draw(canvas)
        return rects
    }

    @Test
    fun `a native shimmer is the layout's own size, whatever the template`() {
        for (style in NativeStyle.entries) {
            val layout = NativeTemplates.forStyle(style)
            val shimmer = laidOut(AdShimmerView(controller.get()).apply { showLayout(layout) })
            val template = android.view.LayoutInflater.from(controller.get()).inflate(layout.layoutRes, FrameLayout(controller.get()), false)
            template.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
            // Close to the template's own height: less the rows most ads lack (rating, store, price), which the
            // shimmer leaves out as the rendered ad does, plus a second body line.
            assertTrue(
                "${style.name}: the shimmer (${shimmer.measuredHeight}) vs the empty template (${template.measuredHeight})",
                shimmer.measuredHeight in (template.measuredHeight * 0.8).toInt()..(template.measuredHeight * 1.3).toInt(),
            )
        }
    }

    @Test
    fun `the whole card shimmers, with a block over each view inside it`() {
        val shimmer = laidOut(AdShimmerView(controller.get()).apply { showLayout(NativeTemplates.LargeButtonBottom) })
        val rects = drawn(shimmer)
        val card = rects.first()
        assertEquals("the card covers the whole ad", shimmer.measuredWidth.toFloat(), card.width(), 1f)
        assertEquals(shimmer.measuredHeight.toFloat(), card.height(), 1f)
        // Card, then the blocks (icon, badge, headline line, body lines, media, button), then the sweep over the card.
        val blocks = rects.drop(1).dropLast(1)
        assertTrue("blocks: ${blocks.size}", blocks.size >= 6)
        assertTrue("every block is inside the card", blocks.all { card.contains(it) })
        assertEquals("the sweep covers the card too", card, rects.last())
    }

    @Test
    fun `an app's own layout gets its shimmer at runtime, from its views`() {
        val layout = NativeAdLayout(
            layoutRes = com.company.admob.views.test.R.layout.company_admob_test_shimmer_native,
            headlineId = com.company.admob.views.test.R.id.company_admob_test_title,
            bodyId = com.company.admob.views.test.R.id.company_admob_test_text,
            callToActionId = com.company.admob.views.test.R.id.company_admob_test_button,
            mediaContainerId = com.company.admob.views.test.R.id.company_admob_test_media,
        )
        val shimmer = laidOut(AdShimmerView(controller.get()).apply { showLayout(layout) })
        val density = controller.get().resources.displayMetrics.density
        assertTrue("its own size: the 200dp media and more", shimmer.measuredHeight > 200 * density)
        val blocks = drawn(shimmer).drop(1).dropLast(1)
        assertTrue("media, title, text lines and button: ${blocks.size}", blocks.size >= 4)
        assertTrue("the media block is 200dp tall", blocks.any { kotlin.math.abs(it.height() - 200 * density) < 2 })
    }

    @Test
    fun `a banner shimmer is its height, and its whole area shimmers`() {
        val shimmer = laidOut(AdShimmerView(controller.get()).apply { showBanner(150) })
        assertEquals(150, shimmer.measuredHeight)
        val card = drawn(shimmer).first()
        assertEquals(1080f, card.width(), 1f)
        assertEquals(150f, card.height(), 1f)
    }

    @Test
    fun `a native ad's shimmer follows its registered layout`() {
        NativeTemplates.register("promo", NativeTemplates.SideMediaButtonTop)
        ViewsRuntime.nativeLayoutNameOverride = { "promo" }
        try {
            client.native.deliverImmediately = false
            container.attachNativeAd(controller.get(), FEED)
            assertEquals(NativeTemplates.SideMediaButtonTop, NativeTemplates.forPlacement(FEED))
            client.native.deliverPending()
            idle()
            assertEquals(NativeTemplates.SideMediaButtonTop, client.native.renders.single().layout)
        } finally {
            ViewsRuntime.nativeLayoutNameOverride = null
        }
    }

    @Test
    fun `an unregistered name shows the style's template`() {
        ViewsRuntime.nativeLayoutNameOverride = { "never_registered" }
        try {
            client.native.style = NativeStyle.SMALL_BUTTON_END
            assertEquals(NativeTemplates.SmallButtonEnd, NativeTemplates.forPlacement(FEED))
        } finally {
            ViewsRuntime.nativeLayoutNameOverride = null
        }
    }

    @Test
    fun `a layout name must be valid and can't be a library style's`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { NativeTemplates.register("my promo", NativeTemplates.Small) }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { NativeTemplates.register("Large", NativeTemplates.Small) }
        NativeTemplates.register(" My_Promo ", NativeTemplates.Small)
        assertEquals(NativeTemplates.Small, NativeTemplates.registered("my_promo"))
    }
}
