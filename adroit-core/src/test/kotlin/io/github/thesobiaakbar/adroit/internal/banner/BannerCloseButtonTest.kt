package io.github.thesobiaakbar.adroit.internal.banner

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import io.github.thesobiaakbar.adroit.AdCloseButton
import io.github.thesobiaakbar.adroit.BannerPlacement
import io.github.thesobiaakbar.adroit.BannerState
import io.github.thesobiaakbar.adroit.config.AdDefinition
import io.github.thesobiaakbar.adroit.config.AdEnvironment
import io.github.thesobiaakbar.adroit.config.AdProfile
import io.github.thesobiaakbar.adroit.config.AdSettings
import io.github.thesobiaakbar.adroit.config.TestIds
import io.github.thesobiaakbar.adroit.config.adMobConfig
import io.github.thesobiaakbar.adroit.events.AdEvent
import io.github.thesobiaakbar.adroit.internal.AdFrame
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BannerCloseButtonTest {
    private enum class CloseAds(override val defaults: AdProfile) : AdDefinition {
        Closable(AdProfile.banner(listOf(TestIds.unit(7)), closeButton = AdCloseButton.TOP_END)),
        Plain(AdProfile.banner(listOf(TestIds.unit(8)))),
    }

    private fun TestScope.harness() = BannerHarness(
        this,
        adMobConfig {
            environment = AdEnvironment.STAGING
            testing {
                useDemoAdUnits = false
                testDeviceIds += "TEST-DEVICE"
            }
            profiles(CloseAds.entries, AdSettings())
        },
    ).apply { ready() }

    private fun ViewGroup.closeButton(): ImageButton? =
        (0 until childCount).map(::getChildAt).filterIsInstance<ImageButton>().singleOrNull()

    @Test
    fun `the close row is reserved with the ad, the button shows once it loads, and a tap is only reported`() = runTest {
        val b = harness()
        val handle = b.create(placement = BannerPlacement(CloseAds.Closable.name))
        b.layout(400)
        val container = handle.view as ViewGroup
        val row = AdFrame.closeRow(container.context)
        assertEquals("the ad, its margins and the close row", 150 + 2 * b.margin + row, container.minimumHeight)
        val sdkView = b.views.single()
        assertEquals("the ad is below the row", b.margin + row, (sdkView.view.layoutParams as ViewGroup.MarginLayoutParams).topMargin)
        assertNull("no button before the ad", container.closeButton())

        sdkView.simulateLoaded()
        b.run()
        val close = checkNotNull(container.closeButton())
        assertEquals(Gravity.TOP or Gravity.END, (close.layoutParams as FrameLayout.LayoutParams).gravity)

        close.performClick()
        b.run()
        assertEquals(1, b.events.recent().filterIsInstance<AdEvent.CloseClicked>().size)
        assertEquals("the banner stays", BannerState.Loaded, handle.state.value)
        assertEquals(View.VISIBLE, sdkView.view.visibility)
    }

    @Test
    fun `without a close button there is no row, and a failed load drops the button`() = runTest {
        val b = harness()
        val plain = b.create(placement = BannerPlacement(CloseAds.Plain.name))
        b.layout(400)
        assertEquals(150 + 2 * b.margin, plain.view.minimumHeight)

        val closable = b.create(placement = BannerPlacement(CloseAds.Closable.name))
        b.layout(400)
        val view = b.views.last()
        view.simulateLoaded()
        b.run()
        view.simulateFailedToLoad()
        b.run()
        assertNull((closable.view as ViewGroup).closeButton())
    }
}
