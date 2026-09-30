package io.github.thesobiaakbar.adroit.compose

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import io.github.thesobiaakbar.adroit.NativeStyle
import io.github.thesobiaakbar.adroit.views.NativeTemplates
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdNativeTest {
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
    fun `first composition loads once, and 100 recompositions neither reload nor re-render`() {
        var tick by mutableIntStateOf(0)
        rule.setContent {
            Column {
                BasicText("tick $tick")
                AdNative(FEED, layout = NativeTemplates.SmallButtonEnd)
            }
        }
        rule.waitForIdle()
        assertEquals(1, client.native.loadCount)
        assertEquals(1, client.native.renders.size)
        repeat(100) {
            tick++
            rule.waitForIdle()
        }
        rule.onNodeWithText("tick 100").assertExists()
        assertEquals(1, client.native.loadCount)
        assertEquals(1, client.native.renders.size)
        assertEquals(NativeTemplates.SmallButtonEnd, client.native.renders.single().layout)
    }

    @Test
    fun `without a layout, the template follows the placement's configured style`() {
        client.native.style = NativeStyle.SMALL_BUTTON_END
        rule.setContent { AdNative(FEED) }
        rule.waitForIdle()
        assertEquals(NativeTemplates.SmallButtonEnd, client.native.renders.single().layout)
    }

    @Test
    fun `the placeholder shows while loading, then the ad`() {
        rule.mainClock.autoAdvance = false
        rule.setContent { AdNative(FEED, placeholder = { BasicText("loading", Modifier.testTag("placeholder")) }) }
        rule.onNodeWithTag("placeholder").assertExists()
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.onNodeWithTag("placeholder").assertDoesNotExist()
        assertEquals(1, client.native.renders.size)
    }

    @Test
    fun `a failed load shows nothing`() {
        client.native.failWith = AdError.NoFill()
        rule.setContent { AdNative(FEED, Modifier.testTag("ad"), placeholder = { BasicText("loading") }) }
        rule.waitForIdle()
        rule.onNodeWithText("loading").assertDoesNotExist()
        rule.onNodeWithTag("ad").assertDoesNotExist()
        assertTrue(client.native.renders.isEmpty())
    }

    @Test
    fun `a blocked ad leaves the composition`() {
        rule.setContent { AdNative(FEED, Modifier.testTag("ad")) }
        rule.waitForIdle()
        rule.onNodeWithTag("ad").assertExists()
        client.native.handles.single().block()
        rule.waitForIdle()
        rule.onNodeWithTag("ad").assertDoesNotExist()
    }

    @Test
    fun `leaving the screen (its ViewModelStore cleared) destroys the ad once`() {
        val screen = TestScreen()
        var show by mutableStateOf(true)
        rule.setContent {
            if (show) CompositionLocalProvider(LocalViewModelStoreOwner provides screen) { AdNative(FEED) }
        }
        rule.waitForIdle()
        val handle = client.native.handles.single()
        show = false
        rule.waitForIdle()
        // Leaving the composition alone keeps the ad (the screen may come back, as a navigation back stack would).
        assertEquals(0, handle.destroyCount)
        assertEquals(listOf(handle), client.detached)
        screen.viewModelStore.clear()
        assertEquals(1, handle.destroyCount)
    }

    @Test
    fun `a LazyColumn with an ad every 5 items keeps every ad, scrolled away and back, until the screen is cleared`() {
        val screen = TestScreen()
        rule.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides screen) {
                LazyColumn(Modifier.testTag("list")) {
                    items(20, key = { it }) { index ->
                        if (index % 5 == 0) {
                            AdNative(FEED, Modifier.fillMaxWidth().height(300.dp), slotKey = index)
                        } else {
                            BasicText("item $index", Modifier.height(300.dp))
                        }
                    }
                }
            }
        }
        for (index in (0 until 20) + (19 downTo 0)) {
            rule.onNodeWithTag("list").performScrollToIndex(index)
            rule.waitForIdle()
        }
        // One load per slot, however often it scrolled away and back; every ad kept.
        assertEquals(4, client.native.loadCount)
        val (first) = client.native.handles
        assertEquals(client.native.handles, client.native.live)
        assertSame(first, client.native.renders.last().handle)
        screen.viewModelStore.clear()
        assertTrue(client.native.live.isEmpty())
        assertTrue(client.native.handles.all { it.destroyCount == 1 })
    }

    private class TestScreen : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }
}

/** Configuration changes, on a Robolectric Activity that sets its content in onCreate as an app would. */
@RunWith(RobolectricTestRunner::class)
class AdNativeRecreateTest {
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
    fun `recreation re-renders the same ad into the new view without loading again`() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller.get().setContent { AdNative(FEED) }
        idle()
        assertEquals(1, client.native.loadCount)
        val handle = client.native.handles.single()

        controller.recreate()
        controller.get().setContent { AdNative(FEED) }
        idle()

        assertEquals(1, client.native.loadCount)
        val (before, after) = client.native.renders
        assertSame(handle, before.handle)
        assertSame(handle, after.handle)
        assertNotSame(before.container, after.container)
        assertSame(controller.get(), after.container.context)
        assertEquals(0, handle.destroyCount)
        // The old screen's views were dropped from the retained ad.
        assertEquals(listOf(handle), client.detached)

        controller.pause().stop().destroy()
        idle()
        assertEquals(1, handle.destroyCount)
    }
}
