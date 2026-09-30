package io.github.thesobiaakbar.adroit.internal.fullscreen

import app.cash.turbine.test
import io.github.thesobiaakbar.adroit.FullScreenResult
import io.github.thesobiaakbar.adroit.events.AdEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InterstitialApiTest {
    @Test
    fun `showAndAwait returns the result after dismiss`() = runTest {
        val h = EngineHarness(this).apply { ready(); run(1_000) }
        val api = InterstitialApiImpl(h.engine, h.log)

        val result = async { api.showAndAwait(h.activity(), LEVEL_END) }
        h.run()
        h.adsSdk.fullScreenAds.first().simulateFullShow()
        h.run()

        assertEquals(FullScreenResult.Dismissed(clicked = false), result.await())
    }

    @Test
    fun `cancelling the caller doesn't stop the ad, and dismiss still releases the coordinator`() = runTest {
        val h = EngineHarness(this).apply { ready(); run(1_000) }
        val api = InterstitialApiImpl(h.engine, h.log)

        val caller = async { api.showAndAwait(h.activity(), LEVEL_END) }
        h.run()
        val ad = h.adsSdk.fullScreenAds.first()
        ad.simulateShown()
        caller.cancel()
        h.run()
        assertTrue("the ad is still showing", h.coordinator.isShowing)

        ad.simulateDismissed()
        h.run()
        assertFalse(h.coordinator.isShowing)
        assertTrue(caller.isCancelled)
    }

    @Test
    fun `the API delegates preload, availability, isReady and clear`() = runTest {
        val h = EngineHarness(this)
        val api = InterstitialApiImpl(h.engine, h.log)
        h.ready()
        api.preload(LEVEL_END)
        h.run(1_000)
        assertTrue(api.isReady(LEVEL_END))
        assertEquals(h.engine.availability(LEVEL_END), api.availability(LEVEL_END))
        api.clear(LEVEL_END)
        assertFalse(api.isReady(LEVEL_END))

        val results = mutableListOf<FullScreenResult>()
        api.show(h.activity(), LEVEL_END) { results += it }
        h.run()
        assertEquals(1, results.size)
    }

    @Test
    fun `the exact event sequence of a successful show`() = runTest {
        val h = EngineHarness(this)
        h.events.events.test {
            h.ready()
            h.run(250)
            h.show()
            h.run()
            val ad = h.adsSdk.fullScreenAds.first()
            ad.simulateShown()
            ad.simulateImpression()
            ad.simulateClicked()
            ad.simulatePaid()
            ad.simulateDismissed()
            h.run()

            val sequence = List(12) { awaitItem() }.map { "${it::class.simpleName}(${it.placementKey})" }
            assertEquals(
                listOf(
                    "LoadRequested(level_end)", "Loaded(level_end)",
                    "LoadRequested(menu)", "Loaded(menu)",
                    "ShowRequested(level_end)", "Shown(level_end)", "Impression(level_end)",
                    "Clicked(level_end)", "Paid(level_end)", "Dismissed(level_end)",
                    "LoadRequested(level_end)", "Loaded(level_end)",
                ),
                sequence,
            )
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(h.events.recent().none { it is AdEvent.NotShown })
    }
}
