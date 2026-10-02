package io.github.thesobiaakbar.adroit.views

import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import io.github.thesobiaakbar.adroit.config.AdDefinition
import io.github.thesobiaakbar.adroit.config.AdProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

private enum class TestAds(override val defaults: AdProfile) : AdDefinition {
    Top(AdProfile.banner()),
    Bottom(AdProfile.native()),
}

/** attachAd puts one AdSlotView in its container, named after the container's id, whatever the ad's format. */
@RunWith(RobolectricTestRunner::class)
class AttachAdTest {
    @Test
    fun `one slot per container - another call reuses it, another ad replaces it`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val container = FrameLayout(activity).apply { id = View.generateViewId(); addView(View(activity)) }
        activity.setContentView(container)

        val slot = container.attachAd(TestAds.Top)
        assertEquals(1, container.childCount)
        assertSame(slot, container.getChildAt(0))
        assertEquals("attach:${container.id}", slot.slotKey)
        assertEquals("Top", slot.key)

        assertSame(slot, container.attachAd(TestAds.Top, refresh = true))

        val other = container.attachAd(TestAds.Bottom)
        assertNotSame(slot, other)
        assertEquals(1, container.childCount)
        assertEquals("Bottom", other.key)
    }
}
