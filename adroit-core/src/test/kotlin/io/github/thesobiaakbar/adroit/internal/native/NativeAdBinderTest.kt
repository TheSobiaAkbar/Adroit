package io.github.thesobiaakbar.adroit.internal.native

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import io.github.thesobiaakbar.adroit.NativeAdAssets
import io.github.thesobiaakbar.adroit.NativeAdLayout
import io.github.thesobiaakbar.adroit.fakes.FakeLoadedNativeAd
import io.github.thesobiaakbar.adroit.fakes.FakeNativeRenderPort
import io.github.thesobiaakbar.adroit.internal.config.AdBehavior
import io.github.thesobiaakbar.adroit.internal.google.NextGenNativeAdBinder
import io.github.thesobiaakbar.adroit.internal.logging.AdLog
import io.github.thesobiaakbar.adroit.logging.LogLevel
import io.github.thesobiaakbar.adroit.test.R
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NativeAdBinderTest {
    private val sparse = NativeAdAssets(
        headline = "Headline",
        body = null,
        callToAction = "Open",
        advertiser = null,
        starRating = null,
        price = null,
        store = null,
        icon = null,
        hasVideo = false,
        mediaAspectRatio = null,
    )

    private fun kotlinx.coroutines.test.TestScope.handle(assets: NativeAdAssets = FakeLoadedNativeAd.defaultAssets()) =
        NativeHarness(this).let { n ->
            n to NativeAdHandleImpl(FEED, FakeLoadedNativeAd(assets = assets), "unit", n.events, n.clock, n.log) {}
        }

    @Test
    fun `binds every asset view, adds the media view, registers, then replaces the container's children`() = runTest {
        val (n, handle) = handle()
        val container = n.container().apply { addView(View(context)) }
        val root = n.api.render(handle, container, FULL_LAYOUT)

        assertSame(root, container.getChildAt(0))
        assertEquals(1, container.childCount)
        assertTrue(root is FakeNativeRenderPort.FakeAdView)
        assertEquals("Test headline", root.findViewById<TextView>(R.id.adroit_test_headline).text)
        assertEquals("Test body", root.findViewById<TextView>(R.id.adroit_test_body).text)
        assertEquals("Install", root.findViewById<TextView>(R.id.adroit_test_cta).text)
        assertEquals("Test advertiser", root.findViewById<TextView>(R.id.adroit_test_advertiser).text)
        assertEquals(4.5f, root.findViewById<RatingBar>(R.id.adroit_test_rating).rating)

        val registration = n.port.registrations.single()
        assertSame(root, registration.adView)
        assertSame(handle.ad, registration.ad)
        assertSame(root.findViewById(R.id.adroit_test_headline), registration.views.headline)
        assertSame(root.findViewById(R.id.adroit_test_cta), registration.views.callToAction)
        val media = root.findViewById<android.view.ViewGroup>(R.id.adroit_test_media)
        assertSame(registration.mediaView, media.getChildAt(0))
        assertTrue(registration.mediaView is FakeNativeRenderPort.FakeMediaView)
    }

    @Test
    fun `null assets are hidden, except the headline`() = runTest {
        val (n, handle) = handle(sparse)
        val root = n.api.render(handle, n.container(), FULL_LAYOUT)
        fun visibility(id: Int) = root.findViewById<View>(id).visibility
        assertEquals(View.VISIBLE, visibility(R.id.adroit_test_headline))
        assertEquals(View.VISIBLE, visibility(R.id.adroit_test_cta))
        for (id in listOf(
            R.id.adroit_test_body, R.id.adroit_test_advertiser, R.id.adroit_test_rating,
            R.id.adroit_test_price, R.id.adroit_test_store, R.id.adroit_test_icon,
        )) {
            assertEquals(root.resources.getResourceEntryName(id), View.GONE, visibility(id))
        }
        assertNull(root.findViewById<ImageView>(R.id.adroit_test_icon).drawable)
    }

    @Test
    fun `a star rating in a TextView is shown as text`() = runTest {
        val (n, handle) = handle()
        val layout = NativeAdLayout(
            layoutRes = R.layout.adroit_test_native_text_rating,
            headlineId = R.id.adroit_test_headline,
            starRatingId = R.id.adroit_test_rating,
        )
        val root = n.api.render(handle, n.container(), layout)
        assertEquals(String.format(java.util.Locale.getDefault(), "%.1f", 4.5), root.findViewById<TextView>(R.id.adroit_test_rating).text.toString())
        assertNull("no media container, no media view", n.port.registrations.single().mediaView)
    }

    @Test
    fun `a missing headline view - IllegalArgumentException naming the id, and nothing is registered`() = runTest {
        val (n, handle) = handle()
        val layout = NativeAdLayout(R.layout.adroit_test_native_no_headline, R.id.adroit_test_headline)
        val error = assertThrows(IllegalArgumentException::class.java) { n.api.render(handle, n.container(), layout) }
        assertTrue(error.message!!, "adroit_test_headline" in error.message!!)
        assertTrue(n.port.registrations.isEmpty())
    }

    @Test
    fun `missing optional views are logged once, at debug level, and skipped`() = runTest {
        val (n, handle) = handle()
        val layout = NativeAdLayout(
            layoutRes = R.layout.adroit_test_native_no_headline,
            headlineId = R.id.adroit_test_body, // any TextView works as the headline
            priceId = R.id.adroit_test_price,
        )
        n.api.render(handle, n.container(), layout)
        n.api.render(handle, n.container(), layout)
        assertEquals(1, n.allLogs.count { "adroit_test_price" in it })
        assertEquals("not a warning: optional views are optional", 0, n.infoLogs.count { "adroit_test_price" in it })
        assertNull(n.port.registrations.last().views.price)
    }

    @Test
    fun `renderer onBind runs after binding and registration`() = runTest {
        val (n, handle) = handle()
        var seen: String? = null
        n.api.render(handle, n.container(), FULL_LAYOUT) { root, assets ->
            val headline = root.findViewById<TextView>(R.id.adroit_test_headline).text.toString()
            seen = "$headline|${assets.headline}|${n.port.registrations.size}"
        }
        assertEquals("Test headline|Test headline|1", seen)
    }

    @Test
    fun `Google's NativeAdView and MediaView can be created under Robolectric`() = runTest {
        val (n, _) = handle()
        val port = NextGenNativeAdBinder(AdLog({ _, _, _, _ -> }, LogLevel.NONE))
        val adView = port.createAdView(n.activity)
        val mediaView = port.createMediaView(n.activity)
        assertEquals("NativeAdView", adView.javaClass.simpleName)
        assertEquals("MediaView", mediaView.javaClass.simpleName)
        port.release(adView)
    }

    @Test
    fun `Firebase colors paint the call to action as a gradient, and the badge, keeping a shape's corners`() = runTest {
        val (n, handle) = handle()
        val orange = Color.parseColor("#FF6D00")
        val amber = Color.parseColor("#FFC107")
        n.look = AdBehavior(ctaColors = listOf(orange, amber), ctaTextColor = Color.WHITE, attributionColor = amber)
        val root = n.api.render(handle, n.container(), FULL_LAYOUT)

        val cta = root.findViewById<TextView>(R.id.adroit_test_cta)
        val gradient = cta.background as GradientDrawable
        assertEquals(listOf(orange, amber), gradient.colors!!.toList())
        assertEquals(Color.WHITE, cta.currentTextColor)
        val badge = root.findViewById<TextView>(R.id.adroit_test_attribution).background as GradientDrawable
        assertEquals(listOf(amber, amber), badge.colors!!.toList())

        // A background holding a shape (here a ripple) keeps it, corners and all, with the new colors.
        val shaped = NativeAdLayout(
            layoutRes = R.layout.adroit_test_native_shaped,
            headlineId = R.id.adroit_test_headline,
            callToActionId = R.id.adroit_test_cta,
        )
        n.look = AdBehavior(ctaColors = listOf(orange))
        val ripple = n.api.render(handle, n.container(), shaped).findViewById<View>(R.id.adroit_test_cta).background
        val shape = (ripple as RippleDrawable).getDrawable(0) as GradientDrawable
        assertEquals(listOf(orange, orange), shape.colors!!.toList())
        assertEquals(12 * root.resources.displayMetrics.density, shape.cornerRadius, 0.5f)
    }

    @Test
    fun `without colors the layout keeps its own look`() = runTest {
        val (n, handle) = handle()
        n.look = AdBehavior(showLoading = true)
        val root = n.api.render(handle, n.container(), FULL_LAYOUT)
        // The theme's own button background, not a painted one.
        assertTrue(root.findViewById<View>(R.id.adroit_test_cta).background !is GradientDrawable)
    }
}
