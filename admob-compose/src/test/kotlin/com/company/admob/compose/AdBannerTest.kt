package com.company.admob.compose

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.company.admob.AdError
import com.company.admob.BannerOptions
import com.company.admob.BannerSize
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

@RunWith(RobolectricTestRunner::class)
class AdBannerTest {
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
    fun `recomposing 100 times with unrelated state creates one banner`() {
        var tick by mutableIntStateOf(0)
        rule.setContent {
            Column {
                BasicText("tick $tick")
                AdBanner(HOME)
            }
        }
        repeat(100) {
            tick++
            rule.waitForIdle()
        }
        rule.onNodeWithText("tick 100").assertExists()
        assertEquals(1, client.banner.created.size)
        assertEquals(0, client.banner.created.single().handle.destroyCount)
        // The factory returned the handle's own view, now in the composition's view tree.
        assertTrue(client.banner.created.single().handle.view.isAttachedToWindow)
    }

    @Test
    fun `removing it from the composition destroys the banner once`() {
        var show by mutableStateOf(true)
        rule.setContent { if (show) AdBanner(HOME) }
        rule.waitForIdle()
        show = false
        rule.waitForIdle()
        assertEquals(1, client.banner.created.single().handle.destroyCount)
    }

    @Test
    fun `a changed placement destroys the old banner and creates a new one`() {
        var placement by mutableStateOf(HOME)
        rule.setContent { AdBanner(placement) }
        rule.waitForIdle()
        placement = OTHER_BANNER
        rule.waitForIdle()
        val (first, second) = client.banner.created
        assertEquals(HOME, first.placement)
        assertEquals(OTHER_BANNER, second.placement)
        assertEquals(1, first.handle.destroyCount)
        assertEquals(0, second.handle.destroyCount)
        assertEquals(listOf(second.handle), client.banner.live)
    }

    @Test
    fun `changed options replace the banner, equal options don't`() {
        var options by mutableStateOf(BannerOptions())
        rule.setContent { AdBanner(HOME, options = options) }
        rule.waitForIdle()
        options = BannerOptions()
        rule.waitForIdle()
        assertEquals(1, client.banner.created.size)
        options = BannerOptions(BannerSize.MediumRectangle)
        rule.waitForIdle()
        assertEquals(2, client.banner.created.size)
        assertEquals(BannerSize.MediumRectangle, client.banner.live.single().let { live ->
            client.banner.created.single { it.handle === live }.options.size
        })
    }

    @Test
    fun `the banner is created for the composition's Activity`() {
        rule.setContent { AdBanner(HOME) }
        rule.waitForIdle()
        val created = client.banner.created.single()
        assertSame(rule.activity, created.activity)
    }

    @Test
    fun `the placeholder shows while loading only, and onStateChange reports each state`() {
        val states = mutableListOf<BannerState>()
        rule.setContent {
            AdBanner(HOME, placeholder = { BasicText("placeholder", Modifier.testTag("placeholder")) }) { states += it }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("placeholder").assertExists()
        val handle = client.banner.created.single().handle
        handle.state.value = BannerState.Loaded
        rule.waitForIdle()
        rule.onNodeWithTag("placeholder").assertDoesNotExist()
        handle.state.value = BannerState.Failed(AdError.NoFill())
        rule.waitForIdle()
        assertEquals(listOf(BannerState.Loading, BannerState.Loaded, BannerState.Failed(AdError.NoFill())), states)
    }

    @Test
    fun `without reserveSpace the slot collapses on failure`() {
        rule.setContent { AdBanner(HOME, Modifier.testTag("banner"), BannerOptions(reserveSpace = false)) }
        rule.waitForIdle()
        val handle = client.banner.created.single().handle
        handle.state.value = BannerState.Failed(AdError.NoFill())
        rule.waitForIdle()
        val height = rule.onNodeWithTag("banner").fetchSemanticsNode().size.height
        assertEquals(0, height)
    }
}
