package io.github.thesobiaakbar.adroit.compose

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import io.github.thesobiaakbar.adroit.BannerState
import io.github.thesobiaakbar.adroit.InternalAdroitApi
import io.github.thesobiaakbar.adroit.compose.internal.ComposeRuntime
import io.github.thesobiaakbar.adroit.views.AdShimmerView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The default loading shimmer of AdBanner and AdNative (PROMPT 29C). */
@OptIn(InternalAdroitApi::class)
@RunWith(RobolectricTestRunner::class)
class ComposeShimmerTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val client = TestAdsClient()
    private var showsLoading = true

    @Before
    fun setUp() {
        client.install()
        ComposeRuntime.showsLoadingOverride = { showsLoading }
    }

    @After
    fun tearDown() {
        ComposeRuntime.showsLoadingOverride = null
        TestAdsClient.uninstall()
    }

    private fun shimmers(): List<AdShimmerView> {
        fun collect(view: View): List<AdShimmerView> = when (view) {
            is AdShimmerView -> listOf(view)
            is ViewGroup -> (0 until view.childCount).flatMap { collect(view.getChildAt(it)) }
            else -> emptyList()
        }
        return collect(rule.activity.window.decorView)
    }

    @Test
    fun `AdNative shows a shimmer while loading, then the ad`() {
        client.native.deliverImmediately = false
        rule.setContent { AdNative(FEED) }
        rule.waitForIdle()
        assertEquals(1, shimmers().size)
        rule.runOnUiThread { client.native.deliverPending() }
        rule.waitForIdle()
        assertTrue(shimmers().isEmpty())
        assertEquals(1, client.native.renders.size)
    }

    @Test
    fun `an app placeholder replaces the shimmer`() {
        client.native.deliverImmediately = false
        rule.setContent { AdNative(FEED, placeholder = { BasicText("loading") }) }
        rule.waitForIdle()
        assertTrue(shimmers().isEmpty())
    }

    @Test
    fun `AdNative without showLoading shows no shimmer`() {
        showsLoading = false
        client.native.deliverImmediately = false
        rule.setContent { AdNative(FEED) }
        rule.waitForIdle()
        assertTrue(shimmers().isEmpty())
    }

    @Test
    fun `AdBanner shows a shimmer while Loading only`() {
        rule.setContent { AdBanner(HOME) }
        rule.waitForIdle()
        assertEquals(1, shimmers().size)
        client.banner.created.single().handle.state.value = BannerState.Loaded
        rule.waitForIdle()
        assertTrue(shimmers().isEmpty())
    }

    @Test
    fun `AdBanner without showLoading shows no shimmer`() {
        showsLoading = false
        rule.setContent { AdBanner(HOME) }
        rule.waitForIdle()
        assertNull(shimmers().firstOrNull())
    }
}
