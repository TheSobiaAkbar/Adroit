package io.github.thesobiaakbar.adroit.compose

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.BannerOptions
import io.github.thesobiaakbar.adroit.BannerSize
import io.github.thesobiaakbar.adroit.BannerState
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
    fun `leaving the composition keeps the banner, coming back shows the same one, clearing the screen destroys it`() {
        val screen = TestScreen()
        var show by mutableStateOf(true)
        rule.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides screen) { if (show) AdBanner(HOME) }
        }
        rule.waitForIdle()
        val handle = client.banner.created.single().handle
        repeat(3) {
            show = false
            rule.waitForIdle()
            assertEquals(0, handle.destroyCount)
            show = true
            rule.waitForIdle()
        }
        // One banner, back on screen each time: no new request.
        assertEquals(1, client.banner.created.size)
        assertTrue(handle.view.isAttachedToWindow)
        show = false
        rule.waitForIdle()
        screen.viewModelStore.clear()
        assertEquals(1, handle.destroyCount)
    }

    @Test
    fun `banners in a LazyColumn are created once each, scrolled away and back`() {
        val screen = TestScreen()
        rule.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides screen) {
                LazyColumn(Modifier.testTag("list")) {
                    items(20, key = { it }) { index ->
                        if (index % 5 == 0) AdBanner(HOME, slotKey = index) else BasicText("item $index", Modifier.height(300.dp))
                    }
                }
            }
        }
        for (index in (0 until 20) + (19 downTo 0)) {
            rule.onNodeWithTag("list").performScrollToIndex(index)
            rule.waitForIdle()
        }
        assertEquals(4, client.banner.created.size)
        assertEquals(client.banner.created.map { it.handle }, client.banner.live)
        screen.viewModelStore.clear()
        assertTrue(client.banner.live.isEmpty())
    }

    @Test
    fun `a changed placement shows a new banner, the old one kept until the screen is cleared`() {
        val screen = TestScreen()
        var placement by mutableStateOf(HOME)
        rule.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides screen) { AdBanner(placement) }
        }
        rule.waitForIdle()
        placement = OTHER_BANNER
        rule.waitForIdle()
        val (first, second) = client.banner.created
        assertEquals(HOME, first.placement)
        assertEquals(OTHER_BANNER, second.placement)
        assertTrue(second.handle.view.isAttachedToWindow)
        assertEquals(0, first.handle.destroyCount)
        screen.viewModelStore.clear()
        assertTrue(client.banner.live.isEmpty())
    }

    @Test
    fun `changed options show another banner, equal options don't`() {
        var options by mutableStateOf(BannerOptions())
        rule.setContent { AdBanner(HOME, options = options) }
        rule.waitForIdle()
        options = BannerOptions()
        rule.waitForIdle()
        assertEquals(1, client.banner.created.size)
        options = BannerOptions(BannerSize.MediumRectangle)
        rule.waitForIdle()
        assertEquals(2, client.banner.created.size)
        assertEquals(BannerSize.MediumRectangle, client.banner.created.last().options.size)
        assertTrue(client.banner.created.last().handle.view.isAttachedToWindow)
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

    private class TestScreen : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }
}
