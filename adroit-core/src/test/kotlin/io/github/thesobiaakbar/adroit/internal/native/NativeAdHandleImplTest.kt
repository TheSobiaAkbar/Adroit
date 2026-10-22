package io.github.thesobiaakbar.adroit.internal.native

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.thesobiaakbar.adroit.AdFormat
import io.github.thesobiaakbar.adroit.NativeAdHandleState
import io.github.thesobiaakbar.adroit.UnavailableReason
import io.github.thesobiaakbar.adroit.events.AdEvent
import io.github.thesobiaakbar.adroit.fakes.FakeLoadedNativeAd
import io.github.thesobiaakbar.adroit.internal.appopen.assertCollected
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.lang.ref.WeakReference

@RunWith(RobolectricTestRunner::class)
class NativeAdHandleImplTest {
    private fun kotlinx.coroutines.test.TestScope.loaded(): Pair<NativeHarness, NativeAdHandleImpl> {
        val n = NativeHarness(this)
        n.ready()
        n.load()
        return n to n.handles().single()
    }

    private fun NativeHarness.nativeEvents() =
        events.recent().filterIsInstance<AdEvent.PlacementEvent>().filter { it.format == AdFormat.NATIVE }

    @Test
    fun `destroy is idempotent - the SDK ad is destroyed once, one Destroyed event`() = runTest {
        val (n, handle) = loaded()
        val container = n.container()
        n.api.render(handle, container, FULL_LAYOUT)

        handle.destroy()
        handle.close()
        assertTrue(handle.isDestroyed)
        assertEquals(NativeAdHandleState.Destroyed, handle.state.value)
        assertEquals(1, (handle.ad as FakeLoadedNativeAd).destroyCount)
        assertEquals(1, n.nativeEvents().count { it is AdEvent.Destroyed })
        assertEquals("the rendered view is removed and released", 0, container.childCount)
        assertEquals(1, n.port.released.size)
    }

    @Test
    fun `render after destroy - IllegalStateException`() = runTest {
        val (n, handle) = loaded()
        handle.destroy()
        assertThrows(IllegalStateException::class.java) { n.api.render(handle, n.container(), FULL_LAYOUT) }
    }

    @Test
    fun `re-rendering into a new container detaches the old view first`() = runTest {
        val (n, handle) = loaded()
        val first = n.container()
        val second = n.container()
        val firstView = n.api.render(handle, first, FULL_LAYOUT)
        val secondView = n.api.render(handle, second, FULL_LAYOUT)

        assertEquals(0, first.childCount)
        assertSame(secondView, (second.getChildAt(0) as ViewGroup).getChildAt(0))
        assertEquals(listOf(firstView), n.port.released)
        assertEquals(2, n.port.registrations.size)
        assertEquals(0, (handle.ad as FakeLoadedNativeAd).destroyCount)
    }

    @Test
    fun `impression, click and paid events carry the placement, none after destroy`() = runTest {
        val (n, handle) = loaded()
        val ad = handle.ad as FakeLoadedNativeAd
        ad.simulateImpression()
        ad.simulateClicked()
        ad.simulatePaid()
        handle.destroy()
        ad.simulateImpression()

        val names = n.nativeEvents().map { it::class.simpleName }.filter { it in setOf("Impression", "Clicked", "Paid") }
        assertEquals(listOf("Impression", "Clicked", "Paid"), names)
        assertTrue(n.nativeEvents().all { it.placementKey == FEED.key })
    }

    @Test
    fun `no container or Context is kept after destroy`() = runTest {
        val (n, handle) = loaded()
        val ref = renderInActivityAndDestroy(n, handle)
        assertCollected(ref)
    }

    private fun renderInActivityAndDestroy(n: NativeHarness, handle: NativeAdHandleImpl): WeakReference<Activity> {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val container = FrameLayout(controller.get()).also { controller.get().setContentView(it) }
        n.api.render(handle, container, FULL_LAYOUT)
        handle.destroy()
        n.port.registrations.clear() // the fake port keeps the views it saw, for inspection
        n.port.released.clear()
        controller.pause().stop().destroy()
        return WeakReference(controller.get())
    }

    @Test
    fun `a blocked handle can't be rendered, and destroy doesn't destroy the ad again`() = runTest {
        val (n, handle) = loaded()
        n.engine.onAdsBlocked(UnavailableReason.AD_FREE_USER)
        assertThrows(IllegalStateException::class.java) { n.api.render(handle, n.container(), FULL_LAYOUT) }
        handle.destroy()
        assertEquals(1, (handle.ad as FakeLoadedNativeAd).destroyCount)
    }
}
