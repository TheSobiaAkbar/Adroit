package com.company.admob.internal.banner

import com.company.admob.AdError
import com.company.admob.AdFormat
import com.company.admob.config.TestIds
import com.company.admob.events.AdEvent
import com.company.admob.events.AdSourceInfo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BannerEventsTest {
    private fun BannerHarness.bannerEvents(): List<AdEvent.PlacementEvent> =
        events.recent().filterIsInstance<AdEvent.PlacementEvent>().filter { it.format == AdFormat.BANNER }

    @Test
    fun `load, refresh, impression, click and paid - in order, with the banner placement`() = runTest {
        val b = BannerHarness(this)
        b.ready()
        val handle = b.create()
        b.layout(400)
        val view = b.views.single()
        val source = AdSourceInfo("AdMob Network", "5450213213286189855", null, null, null, null, null, null, null)
        b.run(250)
        view.simulateLoaded(source)
        view.simulateImpression()
        view.simulateClicked()
        view.simulatePaid()
        view.simulateLoaded(source, isRefresh = true)

        val events = b.bannerEvents()
        assertEquals(
            listOf("LoadRequested", "Loaded", "Impression", "Clicked", "Paid", "Loaded"),
            events.map { it::class.simpleName },
        )
        assertTrue(events.all { it.placementKey == HOME.key && it.adUnitId == TestIds.unit(7) })
        assertEquals(250L, (events[1] as AdEvent.Loaded).latencyMillis)
        assertEquals(source, (events[1] as AdEvent.Loaded).source)
        assertEquals(source, (events[4] as AdEvent.Paid).source)
        assertEquals("a refresh has no load latency", 0L, (events[5] as AdEvent.Loaded).latencyMillis)
    }

    @Test
    fun `failures - LoadFailed with the attempt and whether a retry follows`() = runTest {
        val b = BannerHarness(this)
        b.ready()
        val handle = b.create()
        b.layout(400)
        val view = b.views.single()
        view.simulateFailedToLoad(AdError.NetworkError())
        b.run(15_000)
        view.simulateFailedToLoad(AdError.NoFill(), isRefresh = true)
        view.simulateFailedToLoad(AdError.InvalidConfiguration("x"), retryable = false)

        val failures = b.bannerEvents().filterIsInstance<AdEvent.LoadFailed>()
        assertEquals(listOf(1, 0, 2), failures.map { it.attempt })
        assertEquals(listOf(true, false, false), failures.map { it.willRetry })
        assertEquals(2, b.bannerEvents().count { it is AdEvent.LoadRequested })
    }

    @Test
    fun `events after destroy are dropped`() = runTest {
        val b = BannerHarness(this)
        b.ready()
        val handle = b.create()
        b.layout(400)
        val view = b.views.single()
        val listener = checkNotNull(view.listener)
        handle.destroy()
        listener.onImpression()
        listener.onLoaded(null, isRefresh = true)
        assertEquals(listOf("LoadRequested", "Destroyed"), b.bannerEvents().map { it::class.simpleName })
    }

    @Test
    fun `destroy - one Destroyed event for the SDK view, none without one`() = runTest {
        val b = BannerHarness(this)
        b.ready()
        val handle = b.create()
        b.layout(400)
        b.views.single().simulateLoaded(null)
        handle.destroy()
        handle.destroy()
        val destroyed = b.bannerEvents().filterIsInstance<AdEvent.Destroyed>()
        assertEquals(1, destroyed.size)
        assertEquals(HOME.key, destroyed.single().placementKey)
        assertEquals(TestIds.unit(7), destroyed.single().adUnitId)

        val neverLaidOut = BannerHarness(this)
        neverLaidOut.ready()
        neverLaidOut.create().destroy()
        assertTrue(neverLaidOut.bannerEvents().none { it is AdEvent.Destroyed })
    }
}
