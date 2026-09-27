package com.company.admob.views

import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.company.admob.AdError
import com.company.admob.NativeAdLayout
import com.company.admob.NativeAdResult
import com.company.admob.NativeStyle
import com.company.admob.views.test.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
class AttachNativeAdTest {
    private lateinit var client: TestAdsClient
    private lateinit var controller: ActivityController<FragmentActivity>
    private lateinit var container: FrameLayout
    private val results = mutableListOf<NativeAdResult>()

    @Before
    fun setUp() {
        client = TestAdsClient().install()
        controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        container = FrameLayout(controller.get()).also { controller.get().setContentView(it) }
    }

    @After
    fun tearDown() = TestAdsClient.uninstall()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `loaded - rendered with the template, and destroyed with its owner`() {
        container.attachNativeAd(controller.get(), FEED) { results += it }
        idle()

        assertTrue(results.single() is NativeAdResult.Loaded)
        assertEquals(NativeTemplates.Medium, client.native.renders.single().layout)
        val headline = container.findViewById<TextView>(com.company.admob.views.R.id.company_admob_native_headline)
        assertEquals("Fake headline", headline.text.toString())
        assertEquals(View.VISIBLE, container.visibility)

        controller.pause().stop().destroy()
        assertEquals(1, client.native.handles.single().destroyCount)
    }

    @Test
    fun `without a layout, the template follows the placement's configured style`() {
        client.native.style = NativeStyle.LARGE
        container.attachNativeAd(controller.get(), FEED) { results += it }
        idle()
        assertEquals(NativeTemplates.Large, client.native.renders.single().layout)
    }

    @Test
    fun `failed - the container is GONE`() {
        client.native.failWith = AdError.NoFill()
        container.attachNativeAd(controller.get(), FEED) { results += it }
        idle()
        assertEquals(listOf<NativeAdResult>(NativeAdResult.Failed(AdError.NoFill())), results)
        assertEquals(View.GONE, container.visibility)
    }

    @Test
    fun `owner destroyed before the ad arrives - the ad is destroyed, never rendered`() {
        client.native.deliverImmediately = false
        container.attachNativeAd(controller.get(), FEED) { results += it }
        controller.pause().stop().destroy()
        client.native.deliverPending()
        idle()
        assertTrue(results.isEmpty())
        assertTrue(client.native.renders.isEmpty())
        assertEquals(1, client.native.handles.single().destroyCount)
    }

    @Test
    fun `ads blocked later - the container is GONE`() {
        container.attachNativeAd(controller.get(), FEED)
        idle()
        client.native.handles.single().block()
        idle()
        assertEquals(View.GONE, container.visibility)
    }

    @Test
    fun `a layout without the headline view - Failed(InvalidConfiguration), no crash, ad destroyed`() {
        val broken = NativeAdLayout(R.layout.company_admob_test_banners, headlineId = View.generateViewId())
        container.attachNativeAd(controller.get(), FEED, broken) { results += it }
        idle()
        assertTrue((results.single() as NativeAdResult.Failed).error is AdError.InvalidConfiguration)
        assertEquals(View.GONE, container.visibility)
        assertEquals(1, client.native.handles.single().destroyCount)
    }

    @Test
    fun `AdNativeView from XML - template and custom layout, loaded on attach`() {
        controller.get().setContentView(R.layout.company_admob_test_natives)
        idle()
        assertEquals(
            listOf(NativeTemplates.Small, NativeTemplates.forLayout(R.layout.company_admob_test_custom_native)),
            client.native.renders.map { it.layout },
        )
        controller.pause().stop().destroy()
        assertTrue(client.native.handles.all { it.destroyCount == 1 })
    }
}
