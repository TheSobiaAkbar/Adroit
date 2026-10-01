package io.github.thesobiaakbar.adroit.views

import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

private val BANNER_ID = View.generateViewId()
private val NATIVE_ID = View.generateViewId()

/** A screen with an XML-style banner and native ad, as a layout would declare them. */
class AdsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FrameLayout(inflater.context).apply {
            addView(AdBannerView(context).apply { id = BANNER_ID; setPlacement(HOME) })
            addView(AdNativeView(context).apply { id = NATIVE_ID; setPlacement(FEED) })
        }
}

/** AdBannerView and AdNativeView keep their ads for their screen (its ViewModel), not for their views. */
@RunWith(RobolectricTestRunner::class)
class RetainedViewsTest {
    private lateinit var client: TestAdsClient

    @Before
    fun setUp() {
        client = TestAdsClient().install()
    }

    @After
    fun tearDown() = TestAdsClient.uninstall()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun start(): Pair<ActivityController<FragmentActivity>, AdsFragment> {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        val fragment = AdsFragment()
        controller.get().supportFragmentManager.beginTransaction().add(android.R.id.content, fragment, TAG).commitNow()
        idle()
        return controller to fragment
    }

    private fun FragmentActivity.adsFragment() = supportFragmentManager.findFragmentByTag(TAG) as AdsFragment

    @Test
    fun `views destroyed and created again (detach, attach) - the same banner and native ad, no new request`() {
        val (controller, fragment) = start()
        val banner = client.banner.created.single().handle
        val native = client.native.handles.single()

        val fm = controller.get().supportFragmentManager
        fm.beginTransaction().detach(fragment).commitNow()
        idle()
        assertFalse("kept while the screen lives", banner.isDestroyed)
        assertFalse(native.isDestroyed)
        fm.beginTransaction().attach(fragment).commitNow()
        idle()

        assertEquals(1, client.banner.created.size)
        assertEquals(1, client.native.loadCount)
        assertSame(banner, fragment.requireView().findViewById<AdBannerView>(BANNER_ID).bannerHandle)
        assertSame(native, client.native.renders.last().handle)
    }

    @Test
    fun `rotation - the same ads in the new Activity`() {
        val (controller, _) = start()
        val banner = client.banner.created.single().handle
        val native = client.native.handles.single()

        controller.recreate()
        idle()

        assertEquals(1, client.banner.created.size)
        assertEquals(1, client.native.loadCount)
        val view = controller.get().adsFragment().requireView().findViewById<AdBannerView>(BANNER_ID)
        assertSame(banner, view.bannerHandle)
        assertSame(native, client.native.renders.last().handle)
    }

    @Test
    fun `refresh asks for another ad, and removing the screen destroys its ads`() {
        val (controller, fragment) = start()
        fragment.requireView().findViewById<AdNativeView>(NATIVE_ID).load(refresh = true)
        idle()
        assertEquals(2, client.native.loadCount)

        controller.get().supportFragmentManager.beginTransaction().remove(fragment).commitNow()
        idle()

        assertTrue(client.banner.created.all { it.handle.isDestroyed })
        assertTrue(client.native.handles.all { it.isDestroyed })
    }

    private companion object {
        const val TAG = "ads"
    }
}
