package io.github.thesobiaakbar.adroit.internal.banner

import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout
import io.github.thesobiaakbar.adroit.AdroitInternal
import io.github.thesobiaakbar.adroit.BannerSize
import io.github.thesobiaakbar.adroit.CollapsibleBannerPosition
import io.github.thesobiaakbar.adroit.InternalAdroitApi
import io.github.thesobiaakbar.adroit.config.AdEnvironment
import io.github.thesobiaakbar.adroit.config.TestIds
import io.github.thesobiaakbar.adroit.config.adMobConfig
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A collapsible banner's expanded panel (a popup window) while its screen is away, and when it comes back. */
@OptIn(InternalAdroitApi::class)
@RunWith(RobolectricTestRunner::class)
class ExpandedBannerPanelsTest {
    private fun TestScope.harness(size: BannerSize) = BannerHarness(
        this,
        adMobConfig {
            environment = AdEnvironment.STAGING
            testing {
                useDemoAdUnits = false
                testDeviceIds += "TEST-DEVICE"
            }
            placements { banner(HOME, listOf(TestIds.unit(7))) { this.size = size; retry = EXACT_BANNER_RETRY } }
        },
    ).apply { ready() }

    /** A popup window over [h]'s Activity, as the SDK's expanded panel is; [web] puts the ad's web content in it. */
    private fun popup(h: BannerHarness, web: Boolean): View {
        val root = FrameLayout(h.activity).apply { if (web) addView(WebView(h.activity)) }
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.TYPE_APPLICATION_PANEL).apply {
            token = h.activity.window.decorView.windowToken
        }
        h.activity.windowManager.addView(root, params)
        h.run()
        return root
    }

    @Test
    fun `an expanded panel hides while its screen is away and shows again with the same ad`() = runTest {
        val h = harness(BannerSize.Collapsible(CollapsibleBannerPosition.BOTTOM))
        val handle = h.create()
        h.layout(400)
        h.views.single().simulateLoaded()
        val panel = popup(h, web = true)
        val appPopup = popup(h, web = false)

        AdroitInternal.parkBanner(handle)
        assertEquals(View.INVISIBLE, panel.visibility)
        assertEquals(View.VISIBLE, appPopup.visibility)

        AdroitInternal.showBannerIn(handle, h.activity)
        assertEquals(View.VISIBLE, panel.visibility)
        assertEquals(1, h.views.size) // the same ad, no new request
    }

    @Test
    fun `a banner that isn't collapsible leaves every popup alone`() = runTest {
        val h = harness(BannerSize.AnchoredAdaptive)
        val handle = h.create()
        h.layout(400)
        h.views.single().simulateLoaded()
        val panel = popup(h, web = true)

        AdroitInternal.parkBanner(handle)
        assertEquals(View.VISIBLE, panel.visibility)
    }
}
