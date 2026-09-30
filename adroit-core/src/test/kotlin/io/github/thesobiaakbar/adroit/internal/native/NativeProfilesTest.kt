package io.github.thesobiaakbar.adroit.internal.native

import io.github.thesobiaakbar.adroit.NativePlacement
import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.NativeStyle
import io.github.thesobiaakbar.adroit.config.AdDefinition
import io.github.thesobiaakbar.adroit.config.AdProfile
import io.github.thesobiaakbar.adroit.config.AdSettings
import io.github.thesobiaakbar.adroit.config.PreloadStrategy
import io.github.thesobiaakbar.adroit.config.TestIds
import io.github.thesobiaakbar.adroit.config.remote.AdOverrides
import io.github.thesobiaakbar.adroit.config.remote.PlacementOverride
import io.github.thesobiaakbar.adroit.fakes.FakeAdsSdk.ScriptedLoad
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Native rotation and style (05_AD_PROFILES.md §4.1, §4.5). */
@RunWith(RobolectricTestRunner::class)
class NativeProfilesTest {
    private val ids = listOf(TestIds.unit(1), TestIds.unit(2))

    @Test
    fun `each load takes the next ad unit, and handles report the unit their ad came from`() = runTest {
        val h = NativeHarness(this, nativeConfig { placements { native(FEED, ids) } })
        h.ready()
        repeat(3) { h.load() }
        assertEquals(listOf(ids[0], ids[1], ids[0], ids[1]), h.adsSdk.loadCalls.map { it.adUnitId })
        val loaded = h.events.recent().filterIsInstance<io.github.thesobiaakbar.adroit.events.AdEvent.Loaded>().map { it.adUnitId }
        assertEquals(listOf(ids[0], ids[1], ids[0], ids[1]), loaded)
    }

    @Test
    fun `style comes from the placement, a remote override wins, and an unknown placement is MEDIUM`() = runTest {
        val h = NativeHarness(this, nativeConfig { placements { native(FEED, TestIds.unit(1)) { style = NativeStyle.SMALL_BUTTON_END } } })
        assertEquals(NativeStyle.SMALL_BUTTON_END, h.api.style(FEED))
        h.live.update(h.live.current.withOverrides(AdOverrides(placements = mapOf("feed" to PlacementOverride(nativeStyle = NativeStyle.LARGE_COMPACT)))))
        assertEquals(NativeStyle.LARGE_COMPACT, h.api.style(FEED))
        assertEquals(NativeStyle.LARGE_BUTTON_BOTTOM, h.api.style(NativePlacement("other")))
    }

    @Test
    fun `a manual native ad loads when a view asks or on preload, and a failed load isn't retried`() = runTest {
        val h = NativeHarness(this, nativeConfig { placements { native(FEED, ids) { preload = PreloadStrategy.NONE; retry = EXACT_NATIVE_RETRY } } })
        h.ready()
        h.run(60_000)
        assertEquals("nothing loads at start", 0, h.loads())

        h.adsSdk.enqueueLoad(ids[0], ScriptedLoad.Failure(AdError.NoFill(), retryable = true))
        h.engine.preload(FEED)
        h.run(600_000)
        assertEquals("no retry", 1, h.loads())

        h.load()
        h.run(1_000)
        assertEquals("a view asking loads again", 2, h.loads())
        assertEquals(1, h.handles().size)
        h.run(600_000)
        assertEquals("and nothing refills the pool", 2, h.loads())
    }

    @Test
    fun `a view asking while its ad is loading ahead gets that ad, without a second request`() = runTest {
        val h = NativeHarness(this, nativeConfig { placements { native(FEED, ids) { preload = PreloadStrategy.NONE } } })
        h.ready()
        h.adsSdk.enqueueLoad(ids[0], ScriptedLoad.Success(), delayMs = 2_000)
        h.engine.preload(FEED)
        h.run(500)
        h.load()
        h.run(500)
        assertEquals("still loading: nothing yet", 0, h.handles().size)
        h.run(2_000)
        assertEquals(1, h.handles().size)
        assertEquals("one request in all", 1, h.loads())
    }

    private enum class NativeDefs(override val defaults: AdProfile) : AdDefinition {
        feed(AdProfile.native(ids = listOf(TestIds.unit(1), TestIds.unit(2)), loadAgain = true)),
    }

    @Test
    fun `with loadAgain each ad handed to a view is replaced by the next ad unit, and nothing else loads`() = runTest {
        val h = NativeHarness(this, nativeConfig { profiles(NativeDefs.entries, AdSettings()) })
        h.ready()
        h.run(60_000)
        assertEquals("loads only when asked", 0, h.loads())

        h.load()
        h.run(1_000)
        assertEquals("the view's ad, then its replacement", listOf(ids[0], ids[1]), h.adsSdk.loadCalls.map { it.adUnitId })
        h.load()
        h.run(1_000)
        assertEquals("served from the pool, and replaced again", listOf(ids[0], ids[1], ids[0]), h.adsSdk.loadCalls.map { it.adUnitId })
        assertEquals(2, h.handles().size)
        h.run(2 * 3_600_000L) // the pooled ad expires: not replaced
        assertEquals(3, h.loads())
    }
}
