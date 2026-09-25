package com.company.admob.internal.native

import android.view.View
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import com.company.admob.NativeAdAssets
import com.company.admob.NativeAdLayout
import com.company.admob.fakes.FakeLoadedNativeAd
import com.company.admob.fakes.FakeNativeRenderPort
import com.company.admob.internal.google.NextGenNativeAdBinder
import com.company.admob.internal.logging.AdLog
import com.company.admob.logging.LogLevel
import com.company.admob.test.R
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
        assertEquals("Test headline", root.findViewById<TextView>(R.id.company_admob_test_headline).text)
        assertEquals("Test body", root.findViewById<TextView>(R.id.company_admob_test_body).text)
        assertEquals("Install", root.findViewById<TextView>(R.id.company_admob_test_cta).text)
        assertEquals("Test advertiser", root.findViewById<TextView>(R.id.company_admob_test_advertiser).text)
        assertEquals(4.5f, root.findViewById<RatingBar>(R.id.company_admob_test_rating).rating)

        val registration = n.port.registrations.single()
        assertSame(root, registration.adView)
        assertSame(handle.ad, registration.ad)
        assertSame(root.findViewById(R.id.company_admob_test_headline), registration.views.headline)
        assertSame(root.findViewById(R.id.company_admob_test_cta), registration.views.callToAction)
        val media = root.findViewById<android.view.ViewGroup>(R.id.company_admob_test_media)
        assertSame(registration.mediaView, media.getChildAt(0))
        assertTrue(registration.mediaView is FakeNativeRenderPort.FakeMediaView)
    }

    @Test
    fun `null assets are hidden, except the headline`() = runTest {
        val (n, handle) = handle(sparse)
        val root = n.api.render(handle, n.container(), FULL_LAYOUT)
        fun visibility(id: Int) = root.findViewById<View>(id).visibility
        assertEquals(View.VISIBLE, visibility(R.id.company_admob_test_headline))
        assertEquals(View.VISIBLE, visibility(R.id.company_admob_test_cta))
        for (id in listOf(
            R.id.company_admob_test_body, R.id.company_admob_test_advertiser, R.id.company_admob_test_rating,
            R.id.company_admob_test_price, R.id.company_admob_test_store, R.id.company_admob_test_icon,
        )) {
            assertEquals(root.resources.getResourceEntryName(id), View.GONE, visibility(id))
        }
        assertNull(root.findViewById<ImageView>(R.id.company_admob_test_icon).drawable)
    }

    @Test
    fun `a star rating in a TextView is shown as text`() = runTest {
        val (n, handle) = handle()
        val layout = NativeAdLayout(
            layoutRes = R.layout.company_admob_test_native_text_rating,
            headlineId = R.id.company_admob_test_headline,
            starRatingId = R.id.company_admob_test_rating,
        )
        val root = n.api.render(handle, n.container(), layout)
        assertEquals(String.format(java.util.Locale.getDefault(), "%.1f", 4.5), root.findViewById<TextView>(R.id.company_admob_test_rating).text.toString())
        assertNull("no media container, no media view", n.port.registrations.single().mediaView)
    }

    @Test
    fun `a missing headline view - IllegalArgumentException naming the id, and nothing is registered`() = runTest {
        val (n, handle) = handle()
        val layout = NativeAdLayout(R.layout.company_admob_test_native_no_headline, R.id.company_admob_test_headline)
        val error = assertThrows(IllegalArgumentException::class.java) { n.api.render(handle, n.container(), layout) }
        assertTrue(error.message!!, "company_admob_test_headline" in error.message!!)
        assertTrue(n.port.registrations.isEmpty())
    }

    @Test
    fun `missing optional views are logged once and skipped`() = runTest {
        val (n, handle) = handle()
        val layout = NativeAdLayout(
            layoutRes = R.layout.company_admob_test_native_no_headline,
            headlineId = R.id.company_admob_test_body, // any TextView works as the headline
            priceId = R.id.company_admob_test_price,
        )
        n.api.render(handle, n.container(), layout)
        n.api.render(handle, n.container(), layout)
        assertEquals(1, n.infoLogs.count { "company_admob_test_price" in it })
        assertNull(n.port.registrations.last().views.price)
    }

    @Test
    fun `renderer onBind runs after binding and registration`() = runTest {
        val (n, handle) = handle()
        var seen: String? = null
        n.api.render(handle, n.container(), FULL_LAYOUT) { root, assets ->
            val headline = root.findViewById<TextView>(R.id.company_admob_test_headline).text.toString()
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
}
