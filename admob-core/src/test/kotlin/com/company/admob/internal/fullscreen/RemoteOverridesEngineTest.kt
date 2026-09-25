package com.company.admob.internal.fullscreen

import com.company.admob.AdAvailability
import com.company.admob.AdError
import com.company.admob.FullScreenResult
import com.company.admob.UnavailableReason
import com.company.admob.config.TestIds
import com.company.admob.config.remote.AdOverrides
import com.company.admob.config.remote.PlacementOverride
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The full-screen engine reads the live effective configuration: remote overrides apply to the next decision. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RemoteOverridesEngineTest {
    private fun TestScope.loaded(): EngineHarness = EngineHarness(this).apply {
        ready()
        run(1_000)
    }

    private fun EngineHarness.override(overrides: AdOverrides) {
        live.update(effective.withOverrides(overrides))
        run()
    }

    @Test
    fun `an ad unit change releases the old slot and loads the new unit, on the same availability flow`() = runTest {
        val h = loaded()
        val availability = h.engine.availability(LEVEL_END)
        val oldAd = h.adsSdk.fullScreenAds.first()
        assertEquals(AdAvailability.Ready, availability.value)
        assertEquals(1, h.adsSdk.loadCount(TestIds.unit(1)))

        h.override(AdOverrides(placements = mapOf("level_end" to PlacementOverride(adUnitId = TestIds.unit(9)))))

        assertTrue(oldAd.released)
        assertEquals(1, h.adsSdk.loadCount(TestIds.unit(9)))
        assertSame(availability, h.engine.availability(LEVEL_END))
        assertEquals(AdAvailability.Ready, availability.value)
        h.show()
        h.run()
        // The ad shown is the one loaded for the new unit (the last load).
        assertEquals(TestIds.unit(9), h.adsSdk.loadCalls.last().adUnitId)
        assertEquals(1, h.lastAd().showCount)
        assertEquals(0, oldAd.showCount)
        // MENU kept its own unit and cached ad.
        assertEquals(1, h.adsSdk.loadCount(TestIds.unit(2)))
        assertTrue(h.engine.isReady(MENU))
    }

    @Test
    fun `a placement disabled remotely releases its ad, reports Disabled, and show returns Disabled(DISABLED_BY_REMOTE)`() =
        runTest {
            val h = loaded()
            val availability = h.engine.availability(LEVEL_END)
            val ad = h.adsSdk.fullScreenAds.first()

            h.override(AdOverrides(placements = mapOf("level_end" to PlacementOverride(enabled = false))))

            assertTrue(ad.released)
            assertEquals(AdAvailability.Disabled, availability.value)
            h.show()
            h.run()
            assertEquals(
                FullScreenResult.NotShown(AdError.Disabled(UnavailableReason.DISABLED_BY_REMOTE)),
                h.results.single(),
            )
            assertEquals(0, ad.showCount)

            // Lifted: the placement loads again and its flow follows.
            h.override(AdOverrides())
            h.run(1_000)
            assertEquals(2, h.adsSdk.loadCount(TestIds.unit(1)))
            assertEquals(AdAvailability.Ready, availability.value)
        }

    @Test
    fun `a disabled format disables its placements`() = runTest {
        val h = loaded()
        h.override(AdOverrides(disabledFormats = setOf(h.engine.format)))
        assertEquals(AdAvailability.Disabled, h.engine.availability(LEVEL_END).value)
        assertEquals(AdAvailability.Disabled, h.engine.availability(MENU).value)
        assertFalse(h.engine.isReady(MENU))
    }

    @Test
    fun `a frequency override applies to the next check`() = runTest {
        val h = loaded()
        h.show()
        h.run()
        h.adsSdk.fullScreenAds.first().simulateFullShow()
        h.run(31_000) // past the 30 s global interval
        h.override(AdOverrides(placements = mapOf("level_end" to PlacementOverride(minInterval = 2.minutes))))
        h.show()
        h.run()
        val capped = h.results.last() as FullScreenResult.NotShown
        assertTrue(capped.error is AdError.FrequencyCapped)
        assertEquals(89.seconds, (capped.error as AdError.FrequencyCapped).retryAfter.inWholeSeconds.seconds)
    }

    @Test
    fun `a global interval override applies to the next check`() = runTest {
        val h = loaded()
        h.show()
        h.run()
        h.adsSdk.fullScreenAds.first().simulateFullShow()
        h.run(1_000)
        h.override(AdOverrides(minIntervalBetweenFullScreenAds = 0.seconds))
        h.show(MENU)
        h.run()
        // Without the override, MENU would be capped for 29 s more by the 30 s global interval.
        assertFalse(h.results.last() is FullScreenResult.NotShown)
        assertEquals(2, h.adsSdk.fullScreenAds.count { it.showCount == 1 })
    }
}
