package com.company.admob.internal.banner

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import com.company.admob.AdError
import com.company.admob.AdsState
import com.company.admob.BannerAdHandle
import com.company.admob.BannerOptions
import com.company.admob.BannerPlacement
import com.company.admob.BannerSize
import com.company.admob.BannerState
import com.company.admob.UnavailableReason
import com.company.admob.config.AdEnvironment
import com.company.admob.config.TestIds
import com.company.admob.config.adMobConfig
import com.company.admob.internal.appopen.TestLifecycleOwner
import com.company.admob.internal.appopen.assertCollected
import com.company.admob.internal.config.GoogleDemoAdUnits
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.lang.ref.WeakReference

@RunWith(RobolectricTestRunner::class)
class BannerAdHandleTest {
    @Test
    fun `created before Ready - no load, then exactly one load when Ready`() = runTest {
        val b = BannerHarness(this)
        val handle = b.create()
        b.layout(400)
        assertEquals(0, b.adsSdk.bannerViews.size)
        assertEquals(BannerState.Loading, handle.state.value)

        b.ready()
        assertEquals(1, b.loads())
        val load = b.views.single().loadCalls.single()
        assertEquals(TestIds.unit(7), load.adUnitId)
        assertEquals(BannerSize.AnchoredAdaptive, load.size)
        assertEquals(400, load.widthDp)
        assertSame("the SDK view is added at the first load", handle.view, b.views.single().view.parent)

        b.readiness.state.value = AdsState.Ready
        b.run()
        assertEquals(1, b.loads())
    }

    @Test
    fun `waits for its first layout, then falls back to the window width when it has none`() = runTest {
        val b = BannerHarness(this)
        b.ready()
        val handle = b.create()
        assertEquals("not laid out yet", 0, b.loads())

        b.layout(0)
        val windowDp = BannerSizeResolver.windowWidthPx(b.activity) / b.activity.resources.displayMetrics.density
        assertEquals(windowDp.toInt(), b.views.single().loadCalls.single().widthDp)
    }

    @Test
    fun `the expected height is reserved before the load`() = runTest {
        val b = BannerHarness(this)
        b.ready()
        val handle = b.create()
        assertEquals(0, handle.expectedHeightPx)
        b.layout(400)

        val view = b.views.single()
        assertEquals(150, handle.expectedHeightPx)
        assertEquals(listOf(150), view.parentMinHeightAtLoad)
    }

    @Test
    fun `reserveSpace = false - no reserved height`() = runTest {
        val b = BannerHarness(this)
        b.ready()
        val handle = b.create(BannerOptions(reserveSpace = false))
        b.layout(400)
        assertEquals(listOf(0), b.views.single().parentMinHeightAtLoad)
        assertEquals(150, handle.expectedHeightPx)
    }

    @Test
    fun `destroy is idempotent - SDK view destroyed once, removed from its parent, Hidden(null)`() = runTest {
        val b = BannerHarness(this)
        b.ready()
        val handle = b.create()
        b.layout(400)
        val sdkView = b.views.single()

        handle.destroy()
        handle.destroy()
        assertEquals(1, sdkView.destroyCount)
        assertNull(handle.view.parent)
        assertNull(sdkView.view.parent)
        assertEquals(BannerState.Hidden(null), handle.state.value)

        // Every method is a no-op afterwards.
        handle.bindTo(TestLifecycleOwner().apply { moveTo(Lifecycle.State.RESUMED) })
        b.readiness.state.value = AdsState.Unavailable(UnavailableReason.AD_FREE_USER)
        b.run()
        b.ready()
        b.layout(500)
        assertEquals(1, b.loads())
        assertEquals(BannerState.Hidden(null), handle.state.value)
    }

    @Test
    fun `bindTo - ON_DESTROY destroys, and the last owner wins`() = runTest {
        val b = BannerHarness(this)
        b.ready()
        val handle = b.create()
        b.layout(400)
        val first = TestLifecycleOwner().apply { moveTo(Lifecycle.State.RESUMED) }
        val second = TestLifecycleOwner().apply { moveTo(Lifecycle.State.RESUMED) }
        handle.bindTo(first)
        handle.bindTo(second)

        first.moveTo(Lifecycle.State.DESTROYED)
        assertEquals(BannerState.Loading, handle.state.value)

        second.moveTo(Lifecycle.State.DESTROYED)
        assertEquals(BannerState.Hidden(null), handle.state.value)
        assertTrue(b.views.single().destroyed)
    }

    @Test
    fun `bindTo an owner that is already destroyed - destroyed at once`() = runTest {
        val b = BannerHarness(this)
        val handle = b.create()
        handle.bindTo(TestLifecycleOwner().apply { moveTo(Lifecycle.State.CREATED); moveTo(Lifecycle.State.DESTROYED) })
        assertEquals(BannerState.Hidden(null), handle.state.value)
    }

    @Test
    fun `a failed load retries only while attached and shown`() = runTest {
        val b = BannerHarness(this)
        b.ready()
        val handle = b.create()
        b.layout(400)
        val sdkView = b.views.single()

        sdkView.simulateFailedToLoad(AdError.NetworkError())
        b.run()
        assertTrue(handle.state.value is BannerState.Failed)
        b.run(15_000)
        assertEquals("retry 1 after 15 s", 2, sdkView.loadCalls.size)

        // Detached: the next failure's retry waits.
        b.host.removeView(handle.view)
        sdkView.simulateFailedToLoad(AdError.NetworkError())
        b.run(120_000)
        assertEquals(2, sdkView.loadCalls.size)

        b.host.addView(handle.view)
        b.run(29_999)
        assertEquals(2, sdkView.loadCalls.size)
        b.run(1)
        assertEquals("retry 2 after 30 s, once attached", 3, sdkView.loadCalls.size)
    }

    @Test
    fun `detached while a retry is pending - paused, resumed on attach`() = runTest {
        val b = BannerHarness(this)
        b.ready()
        val handle = b.create()
        b.layout(400)
        val sdkView = b.views.single()
        sdkView.simulateFailedToLoad(AdError.NetworkError())
        b.run(5_000)
        b.host.removeView(handle.view)
        b.run(60_000)
        assertEquals(1, sdkView.loadCalls.size)

        b.host.addView(handle.view)
        b.run(15_000)
        assertEquals(2, sdkView.loadCalls.size)
    }

    @Test
    fun `hidden container - no retry until shown`() = runTest {
        val b = BannerHarness(this)
        b.ready()
        val handle = b.create()
        b.layout(400)
        val sdkView = b.views.single()
        b.host.visibility = View.GONE
        sdkView.simulateFailedToLoad(AdError.NetworkError())
        b.run(60_000)
        assertEquals(1, sdkView.loadCalls.size)

        b.host.visibility = View.VISIBLE
        b.run(15_000)
        assertEquals(2, sdkView.loadCalls.size)
    }

    @Test
    fun `refresh failures and non-retryable errors are not retried by the library`() = runTest {
        val b = BannerHarness(this)
        b.ready()
        val handle = b.create()
        b.layout(400)
        val sdkView = b.views.single()
        sdkView.simulateLoaded()
        sdkView.simulateFailedToLoad(AdError.NoFill(), isRefresh = true)
        b.run(300_000)
        assertEquals(1, sdkView.loadCalls.size)
        assertEquals("the previous ad stays", BannerState.Loaded, handle.state.value)

        val other = b.create()
        b.layout(400)
        b.views.last().simulateFailedToLoad(AdError.InvalidConfiguration("bad unit"), retryable = false)
        b.run(300_000)
        assertEquals(1, b.views.last().loadCalls.size)
    }

    @Test
    fun `ad-free - hidden and destroyed, then back - reloads`() = runTest {
        val b = BannerHarness(this)
        b.ready()
        val handle = b.create()
        b.layout(400)
        val first = b.views.single()
        first.simulateLoaded()

        b.readiness.state.value = AdsState.Unavailable(UnavailableReason.AD_FREE_USER)
        b.run()
        assertEquals(BannerState.Hidden(UnavailableReason.AD_FREE_USER), handle.state.value)
        assertTrue(first.destroyed)
        assertEquals(0, (handle.view as FrameLayout).childCount)
        assertEquals(0, handle.view.minimumHeight)

        b.ready()
        assertEquals(2, b.views.size)
        assertEquals(1, b.views.last().loadCalls.size)
        assertEquals(BannerState.Loading, handle.state.value)
    }

    @Test
    fun `width change - 8 dp or more reloads, less doesn't`() = runTest {
        val b = BannerHarness(this)
        b.ready()
        val handle = b.create()
        b.layout(400)
        b.layout(405)
        assertEquals(1, b.loads())

        b.layout(700) // for example, rotation with the host handling configuration changes
        assertEquals(2, b.views.size)
        assertTrue(b.views.first().destroyed)
        assertEquals(700, b.views.last().loadCalls.single().widthDp)
    }

    @Test
    fun `fixed sizes don't reload on width changes`() = runTest {
        val b = BannerHarness(this)
        b.ready()
        val handle = b.create(BannerOptions(BannerSize.MediumRectangle))
        b.layout(400)
        b.layout(800)
        assertEquals(1, b.loads())
        assertEquals(300, b.views.single().loadCalls.single().widthDp)
    }

    @Test
    fun `no Activity is retained after destroy`() = runTest {
        val b = BannerHarness(this)
        b.ready()
        val ref = createAndDestroy(b)
        b.adsSdk.bannerViews.clear() // the fake SDK keeps its views for inspection
        b.run(300_000)
        assertCollected(ref)
    }

    private fun createAndDestroy(b: BannerHarness): WeakReference<Activity> {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val host = FrameLayout(activity).also { activity.setContentView(it) }
        val handle = b.api.create(activity, HOME, BannerOptions())
        host.addView(handle.view)
        b.run()
        b.views.single().simulateFailedToLoad(AdError.NetworkError()) // a retry is pending at destroy
        handle.bindTo(TestLifecycleOwner().apply { moveTo(Lifecycle.State.RESUMED) })
        handle.destroy()
        controller.pause().stop().destroy()
        return WeakReference(activity)
    }

    @Test
    fun `park and showIn - a loaded ad moves to a new Activity with no new request, and the old Activity is released`() = runTest {
        val b = BannerHarness(this)
        b.ready()
        val (handle, ref) = showParkAndMove(b)
        // No local holds the first view: it's the old Activity's.
        val moved = checkNotNull(b.views.single().detachedAs) { "the loaded ad was taken out of its view" }
        assertTrue(b.views.single().destroyed)
        assertEquals(1, b.loads())
        assertSame(b.activity, handle.view.context)
        assertEquals(BannerState.Loaded, handle.state.value)
        assertTrue(moved.attachedView != null)
        b.adsSdk.bannerViews.clear() // the fake SDK keeps its views for inspection
        b.run(300_000)
        assertCollected(ref)
        // Destroyed from its new place, the moved ad goes too.
        handle.destroy()
        assertEquals(1, moved.attachedView!!.destroyCount)
    }

    @Test
    fun `park before the ad loaded - the request is dropped, and showIn loads again`() = runTest {
        val b = BannerHarness(this)
        b.ready()
        val handle = b.create()
        b.layout(400)
        CompanyAdMobPark.park(handle)
        assertNull(b.views.single().detachedAs)
        assertTrue(b.views.single().destroyed)
        CompanyAdMobPark.showIn(handle, b.activity)
        b.host.addView(handle.view)
        b.run()
        assertEquals(2, b.loads())
    }

    private fun showParkAndMove(b: BannerHarness): Pair<BannerAdHandle, WeakReference<Activity>> {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val first = controller.get()
        val host = FrameLayout(first).also { first.setContentView(it) }
        val handle = b.api.create(first, HOME, BannerOptions())
        host.addView(handle.view)
        b.run()
        b.views.single().simulateLoaded()
        // The Activity goes (a rotation): the banner leaves the screen, then shows in the new Activity.
        CompanyAdMobPark.park(handle)
        controller.pause().stop().destroy()
        CompanyAdMobPark.showIn(handle, b.activity)
        b.host.addView(handle.view)
        b.run()
        return handle to WeakReference(first)
    }

    @Test
    fun `create off the main thread - logged, and it still works`() = runTest {
        val b = BannerHarness(this)
        b.ready()
        var handle: BannerAdHandle? = null
        val thread = Thread { handle = b.api.create(b.activity, HOME, BannerOptions()) }
        thread.start()
        thread.join()
        b.host.addView(handle!!.view)
        b.layout(400)
        assertEquals(1, b.loads())
    }

    @Test
    fun `unknown and disabled placements never load`() = runTest {
        val b = BannerHarness(
            this,
            bannerConfig { placements { banner(BannerPlacement("off"), TestIds.unit(8)) { enabled = false } } },
        )
        b.ready()
        val unknown = b.create(placement = BannerPlacement("nope"))
        val disabled = b.create(placement = BannerPlacement("off"))
        assertTrue((unknown.state.value as BannerState.Failed).error is AdError.InvalidConfiguration)
        assertEquals(BannerState.Hidden(UnavailableReason.DISABLED_BY_CONFIG), disabled.state.value)
        assertEquals(View.GONE, disabled.view.visibility)
        assertEquals(0, b.loads())
        unknown.destroy()
        assertNull(unknown.view.parent)
    }

    @Test
    fun `demo ad units follow the requested size`() = runTest {
        val b = BannerHarness(
            this,
            adMobConfig {
                environment = AdEnvironment.DEVELOPMENT
                placements { banner(HOME, TestIds.unit(7)) }
            },
        )
        b.ready()
        b.create(BannerOptions(BannerSize.MediumRectangle))
        b.create(BannerOptions(BannerSize.Standard))
        b.create()
        b.layout(400)
        assertEquals(
            listOf(GoogleDemoAdUnits.FIXED_SIZE_BANNER, GoogleDemoAdUnits.FIXED_SIZE_BANNER, GoogleDemoAdUnits.ADAPTIVE_BANNER),
            b.views.map { it.loadCalls.single().adUnitId },
        )
    }
}

/** The public hooks Compose uses, as it calls them. */
@OptIn(com.company.admob.InternalCompanyAdMobApi::class)
private object CompanyAdMobPark {
    fun park(handle: BannerAdHandle) = com.company.admob.CompanyAdMobInternal.parkBanner(handle)

    fun showIn(handle: BannerAdHandle, activity: Activity) = com.company.admob.CompanyAdMobInternal.showBannerIn(handle, activity)
}
