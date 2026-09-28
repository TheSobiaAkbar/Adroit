package com.company.admob.compose

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.company.admob.AdError
import com.company.admob.BannerState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** refreshKey on AdNative and AdBanner (PROMPT 29D). */
@RunWith(RobolectricTestRunner::class)
class ComposeRefreshTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val client = TestAdsClient()

    @Before
    fun setUp() {
        client.install()
    }

    @After
    fun tearDown() {
        TestAdsClient.uninstall()
    }

    @Test
    fun `changing refreshKey replaces the native ad once the new one is drawn`() {
        var refresh by mutableIntStateOf(0)
        rule.setContent { AdNative(FEED, refreshKey = refresh) }
        rule.waitForIdle()
        assertEquals(1, client.native.loadCount)
        val first = client.native.handles.single()

        client.native.deliverImmediately = false
        refresh++
        rule.waitForIdle()
        assertEquals(2, client.native.loadCount)
        assertTrue("still shown while the new one loads", !first.isDestroyed)
        rule.runOnUiThread { client.native.deliverPending() }
        rule.waitForIdle()
        assertSame(client.native.handles.last(), client.native.renders.last().handle)
        assertEquals("drawn over, then destroyed", 1, first.destroyCount)
    }

    @Test
    fun `a failed native refresh keeps the shown ad, and the same refreshKey requests nothing`() {
        var refresh by mutableIntStateOf(0)
        rule.setContent { AdNative(FEED, refreshKey = refresh) }
        rule.waitForIdle()
        val first = client.native.handles.single()
        client.native.failWith = AdError.NoFill()
        refresh++
        rule.waitForIdle()
        assertEquals(2, client.native.loadCount)
        assertEquals(0, first.destroyCount)
        assertEquals(1, client.native.renders.size)
    }

    @Test
    fun `changing refreshKey loads a banner unseen and swaps it in once loaded`() {
        var refresh by mutableIntStateOf(0)
        rule.setContent { AdBanner(HOME, refreshKey = refresh) }
        rule.waitForIdle()
        val first = client.banner.created.single().handle
        refresh++
        rule.waitForIdle()
        assertEquals("loading: nothing to refresh yet", 1, client.banner.created.size)

        first.state.value = BannerState.Loaded
        rule.waitForIdle()
        refresh++
        rule.waitForIdle()
        assertEquals(2, client.banner.created.size)
        val second = client.banner.created.last().handle
        assertTrue(!first.isDestroyed)
        second.state.value = BannerState.Loaded
        rule.waitForIdle()
        assertTrue(first.isDestroyed)
        assertTrue(!second.isDestroyed)

        refresh++
        rule.waitForIdle()
        val third = client.banner.created.last().handle
        third.state.value = BannerState.Failed(AdError.NoFill())
        rule.waitForIdle()
        assertTrue("a failed refresh is dropped", third.isDestroyed)
        assertTrue("and the shown banner stays", !second.isDestroyed)
    }
}
