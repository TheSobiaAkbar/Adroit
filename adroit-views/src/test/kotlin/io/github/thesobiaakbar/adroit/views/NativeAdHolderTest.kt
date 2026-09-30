package io.github.thesobiaakbar.adroit.views

import android.app.Activity
import android.os.Looper
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import java.lang.ref.WeakReference

@RunWith(RobolectricTestRunner::class)
class NativeAdHolderTest {
    private lateinit var client: TestAdsClient

    @Before
    fun setUp() {
        client = TestAdsClient().install()
    }

    @After
    fun tearDown() = TestAdsClient.uninstall()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** A screen: the holder is bound in "onCreate", as a Fragment or Activity would. */
    private fun ActivityController<FragmentActivity>.screen(holder: NativeAdHolder): FrameLayout {
        val container = FrameLayout(get()).also { get().setContentView(it) }
        holder.bindTo(container, get())
        idle()
        return container
    }

    @Test
    fun `rotation - the same ad is rendered again without a new load, and clear destroys it`() {
        val holder = NativeAdHolder(FEED) // lives in the ViewModel
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        controller.screen(holder)
        assertEquals(1, client.native.loadCount)

        controller.recreate()
        val container = controller.screen(holder)

        assertEquals("no new load", 1, client.native.loadCount)
        assertEquals(2, client.native.renders.size)
        assertSame(client.native.handles.single(), client.native.renders.last().handle)
        val headline = container.findViewById<TextView>(R.id.adroit_native_headline)
        assertEquals("Fake headline", headline.text.toString())
        assertEquals("rotation doesn't destroy the ad", 0, client.native.handles.single().destroyCount)

        holder.clear()
        assertEquals(1, client.native.handles.single().destroyCount)
    }

    @Test
    fun `no View or Context kept once the bound owner is destroyed`() {
        val holder = NativeAdHolder(FEED)
        val ref = bindAndDestroy(holder)
        client.native.renders.clear()
        assertCollected(ref)
        assertEquals(0, client.native.handles.single().destroyCount)
    }

    private fun bindAndDestroy(holder: NativeAdHolder): WeakReference<Activity> {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        controller.screen(holder)
        controller.pause().stop().destroy()
        idle() // lets the cancelled collectors finish, as the main loop would
        return WeakReference(controller.get())
    }

    @Test
    fun `clear while a load is pending - the late ad is destroyed, not kept`() {
        val holder = NativeAdHolder(FEED)
        client.native.deliverImmediately = false
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        controller.screen(holder)
        holder.clear()
        client.native.deliverPending()
        idle()
        assertEquals(1, client.native.handles.single().destroyCount)
        assertEquals(0, client.native.renders.size)
    }

    @Test
    fun `a blocked or destroyed ad is replaced on the next bind`() {
        val holder = NativeAdHolder(FEED)
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        controller.screen(holder)
        client.native.handles.single().block()
        controller.recreate()
        controller.screen(holder)
        assertEquals(2, client.native.loadCount)
        assertSame(client.native.handles.last(), client.native.renders.last().handle)
    }

    @Test
    fun `binds made while loading render once the ad arrives, into the latest container`() {
        val holder = NativeAdHolder(FEED)
        client.native.deliverImmediately = false
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        controller.screen(holder)
        controller.recreate()
        val latest = controller.screen(holder)
        assertEquals("one load in flight", 1, client.native.loadCount)
        client.native.deliverPending()
        idle()
        assertEquals(1, latest.childCount)
    }
}

/** Asserts [ref] can be garbage collected. */
internal fun assertCollected(ref: WeakReference<*>) {
    repeat(20) {
        if (ref.get() == null) return
        System.gc()
        Thread.sleep(10)
    }
    assertNull("still strongly reachable", ref.get())
}
