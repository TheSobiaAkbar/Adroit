package io.github.thesobiaakbar.adroit.views

import android.app.Activity
import android.os.Looper
import android.view.View
import androidx.fragment.app.FragmentActivity
import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.BannerOptions
import io.github.thesobiaakbar.adroit.BannerSize
import io.github.thesobiaakbar.adroit.BannerState
import io.github.thesobiaakbar.adroit.views.test.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AdBannerViewInflationTest {
    private lateinit var client: TestAdsClient

    @Before
    fun setUp() {
        client = TestAdsClient().install()
    }

    @After
    fun tearDown() = TestAdsClient.uninstall()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `inflated with attrs and attached - banner created with those options and bound to the Activity`() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        val activity = controller.get()
        activity.setContentView(R.layout.adroit_test_banners)
        idle()

        val byPlacement = client.banner.created.filter { it.placement == HOME }
        assertEquals(listOf(BannerOptions(BannerSize.MediumRectangle), BannerOptions(BannerSize.InlineAdaptive(90))), byPlacement.map { it.options })
        val mrec = activity.findViewById<AdBannerView>(R.id.adroit_test_mrec)
        val handle = byPlacement.first().handle
        assertSame(handle, mrec.bannerHandle)
        assertSame(mrec, handle.view.parent)
        assertSame(activity, handle.boundOwner)

        controller.pause().stop().destroy()
        assertTrue("destroyed with its owner", client.banner.created.all { it.handle.isDestroyed })
    }

    @Test
    fun `unknown or invalid key - GONE, no crash`() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        activity.setContentView(R.layout.adroit_test_banners)
        idle()

        val unknown = activity.findViewById<AdBannerView>(R.id.adroit_test_unknown)
        assertEquals(View.GONE, unknown.visibility)
        assertTrue((unknown.bannerHandle!!.state.value as BannerState.Failed).error is AdError.InvalidConfiguration)

        val invalid = activity.findViewById<AdBannerView>(R.id.adroit_test_invalid)
        assertEquals(View.GONE, invalid.visibility)
        assertNull("an invalid key never reaches core", invalid.bannerHandle)
        assertEquals(3, client.banner.created.size)
    }

    @Test
    fun `collapseWhenEmpty - GONE while there's no ad, visible with one, or always visible when off`() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        activity.setContentView(R.layout.adroit_test_banners)
        idle()
        val mrec = activity.findViewById<AdBannerView>(R.id.adroit_test_mrec)
        val inline = activity.findViewById<AdBannerView>(R.id.adroit_test_inline)
        val (mrecHandle, inlineHandle) = client.banner.created.take(2).map { it.handle }

        mrecHandle.state.value = BannerState.Failed(AdError.NoFill())
        inlineHandle.state.value = BannerState.Failed(AdError.NoFill())
        idle()
        assertEquals(View.GONE, mrec.visibility)
        assertEquals(View.VISIBLE, inline.visibility)

        mrecHandle.state.value = BannerState.Loaded
        idle()
        assertEquals(View.VISIBLE, mrec.visibility)
    }

    @Test
    fun `no LifecycleOwner in the view tree - GONE, nothing created`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(R.layout.adroit_test_banners)
        idle()
        assertEquals(0, client.banner.created.size)
        assertEquals(View.GONE, activity.findViewById<AdBannerView>(R.id.adroit_test_mrec).visibility)
    }

    @Test
    fun `setPlacement replaces the current banner`() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val view = AdBannerView(activity)
        activity.setContentView(view)
        idle()
        assertEquals("no placement yet", 0, client.banner.created.size)

        view.setPlacement(HOME)
        view.setPlacement(HOME, BannerOptions(BannerSize.Standard))
        assertEquals(2, client.banner.created.size)
        assertTrue(client.banner.created[0].handle.isDestroyed)
        assertEquals(1, client.banner.live.size)
    }
}
