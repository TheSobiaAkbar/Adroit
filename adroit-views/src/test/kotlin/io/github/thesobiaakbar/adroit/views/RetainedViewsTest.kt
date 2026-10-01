package io.github.thesobiaakbar.adroit.views

import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import io.github.thesobiaakbar.adroit.BannerState
import io.github.thesobiaakbar.adroit.NativeAdResult
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

private val CONTAINER_ID = View.generateViewId()

/** A banner attached from code into a container with an id, as DetailsActivity does. */
class AttachBannerActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = FrameLayout(this).apply { id = CONTAINER_ID }
        setContentView(container)
        container.attachBanner(this, HOME)
    }
}

/** A native ad attached from code into a container with an id; records each onResult. */
class AttachNativeActivity : FragmentActivity() {
    val results = mutableListOf<NativeAdResult>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = FrameLayout(this).apply { id = CONTAINER_ID }
        setContentView(container)
        container.attachNativeAd(this, FEED) { results += it }
    }
}

/** Two native views of one placement made in code, without ids, told apart by their slot keys; one refreshes on show. */
class CodeAdsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FrameLayout(inflater.context).apply {
            addView(AdNativeView(context).apply { slotKey = "first"; setPlacement(FEED) })
            addView(AdNativeView(context).apply { slotKey = "second"; refreshOnShow = true; setPlacement(FEED) })
        }
}

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

    @Test
    fun `attachBanner into a container with an id - rotation shows the same banner, finishing destroys it`() {
        val controller = Robolectric.buildActivity(AttachBannerActivity::class.java).setup()
        idle()
        val banner = client.banner.created.single().handle

        controller.recreate()
        idle()
        assertEquals(1, client.banner.created.size)
        assertSame(banner.view, controller.get().findViewById<FrameLayout>(CONTAINER_ID).getChildAt(0))

        controller.get().finish()
        controller.pause().stop().destroy()
        assertTrue(banner.isDestroyed)
    }

    @Test
    fun `views made in code keep one ad per slot key, and refreshOnShow asks for a new one when shown again`() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        val fragment = CodeAdsFragment()
        val fm = controller.get().supportFragmentManager
        fm.beginTransaction().add(android.R.id.content, fragment).commitNow()
        idle()
        assertEquals(2, client.native.loadCount)
        val (first, second) = client.native.handles

        fm.beginTransaction().detach(fragment).commitNow()
        fm.beginTransaction().attach(fragment).commitNow()
        idle()

        assertEquals("only the refreshOnShow view asked again", 3, client.native.loadCount)
        assertFalse(first.isDestroyed)
        assertTrue("replaced once its successor arrived", second.isDestroyed)
        assertSame(first, client.native.renders.first { it.handle === first }.handle)
    }

    @Test
    fun `attachNativeAd into a container with an id - rotation shows the same ad, refresh replaces it, finishing destroys it`() {
        val controller = Robolectric.buildActivity(AttachNativeActivity::class.java).setup()
        idle()
        val first = client.native.handles.single()
        assertEquals(listOf(first), controller.get().results.map { (it as NativeAdResult.Loaded).handle })

        controller.recreate()
        idle()
        assertEquals(1, client.native.loadCount)
        assertEquals("onResult called again, with the same ad", listOf(first), controller.get().results.map { (it as NativeAdResult.Loaded).handle })

        val container = controller.get().findViewById<FrameLayout>(CONTAINER_ID)
        client.native.deliverImmediately = false
        container.attachNativeAd(controller.get(), FEED, refresh = true)
        assertEquals(2, client.native.loadCount)
        assertFalse("shown until its successor arrives", first.isDestroyed)
        client.native.deliverPending()
        idle()
        val second = client.native.handles.last()
        assertTrue(first.isDestroyed)
        assertSame(second, client.native.renders.last().handle)

        controller.get().finish()
        controller.pause().stop().destroy()
        assertTrue(second.isDestroyed)
    }

    @Test
    fun `attachBanner with refresh - the shown banner stays until the new one is loaded, then it's destroyed`() {
        val controller = Robolectric.buildActivity(AttachBannerActivity::class.java).setup()
        idle()
        val activity = controller.get()
        val container = activity.findViewById<FrameLayout>(CONTAINER_ID)
        val first = client.banner.created.single().handle
        first.state.value = BannerState.Loaded
        idle()

        assertSame(first, container.attachBanner(activity, HOME))
        assertEquals("no refresh, no request", 1, client.banner.created.size)

        assertSame("returns the banner shown now", first, container.attachBanner(activity, HOME, refresh = true))
        idle()
        val second = client.banner.created.last().handle
        assertEquals(2, client.banner.created.size)
        assertFalse(first.isDestroyed)

        second.state.value = BannerState.Loaded
        idle()
        assertTrue("replaced once the new one loaded", first.isDestroyed)
        assertSame(second, container.attachBanner(activity, HOME))

        controller.recreate()
        idle()
        assertEquals("the refreshed banner is the one kept", 2, client.banner.created.size)
        assertSame(second.view, controller.get().findViewById<FrameLayout>(CONTAINER_ID).getChildAt(0))
    }

    private companion object {
        const val TAG = "ads"
    }
}
