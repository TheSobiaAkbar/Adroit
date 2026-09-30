package io.github.thesobiaakbar.adroit.testing

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.github.thesobiaakbar.adroit.BannerPlacement
import io.github.thesobiaakbar.adroit.BannerState
import io.github.thesobiaakbar.adroit.NativeAdLayout
import io.github.thesobiaakbar.adroit.NativeAdResult
import io.github.thesobiaakbar.adroit.NativePlacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import io.github.thesobiaakbar.adroit.testing.test.R

/** The View parts of the fake, which need an Android runtime. */
@RunWith(RobolectricTestRunner::class)
class FakeViewsTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val ads = FakeAdsClient()
    private val feed = NativePlacement("feed")
    private val layout = NativeAdLayout(
        layoutRes = R.layout.adroit_test_fake_native,
        headlineId = R.id.adroit_test_headline,
        bodyId = R.id.adroit_test_body,
        callToActionId = R.id.adroit_test_cta,
        priceId = R.id.adroit_test_price,
    )

    @Test
    fun `render inflates the layout, fills the assets and hides the missing ones`() {
        var result: NativeAdResult? = null
        ads.native.load(feed) { result = it }
        val handle = (result as NativeAdResult.Loaded).handle
        val container = FrameLayout(activity)

        val root = ads.native.render(handle, container, layout)

        assertSame(root, container.getChildAt(0))
        assertEquals("Test ad headline", root.findViewById<TextView>(R.id.adroit_test_headline).text.toString())
        assertEquals("Install", root.findViewById<TextView>(R.id.adroit_test_cta).text.toString())
        assertEquals(View.GONE, root.findViewById<TextView>(R.id.adroit_test_price).visibility)
        assertEquals(listOf(FakeCall.LoadNative("feed"), FakeCall.RenderNative("feed")), ads.recordedCalls)
    }

    @Test(expected = IllegalStateException::class)
    fun `rendering a destroyed ad throws, like the library`() {
        val handle = FakeNativeAdHandle(feed)
        handle.destroy()
        ads.native.render(handle, FrameLayout(activity), layout)
    }

    @Test
    fun `banners are plain views in the given state, destroyed with their owner`() {
        ads.initialBannerState = BannerState.Loading
        val handle = ads.banner.create(activity, BannerPlacement("home")) as FakeBannerAdHandle
        val container = FrameLayout(activity).apply { addView(handle.view) }
        assertEquals(BannerState.Loading, handle.state.value)
        handle.setState(BannerState.Loaded)
        assertEquals(BannerState.Loaded, handle.state.value)

        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }
        owner.registry.currentState = Lifecycle.State.RESUMED
        handle.bindTo(owner)
        owner.registry.currentState = Lifecycle.State.DESTROYED

        assertTrue(handle.isDestroyed)
        assertNull(handle.view.parent)
        assertEquals(0, container.childCount)
        assertEquals(BannerState.Hidden(null), handle.state.value)
        assertEquals(listOf(handle), ads.createdBanners)
    }
}
