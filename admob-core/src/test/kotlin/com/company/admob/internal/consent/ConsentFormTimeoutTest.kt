package com.company.admob.internal.consent

import android.app.Activity
import com.company.admob.ConsentState
import com.company.admob.ConsentStatus
import com.company.admob.config.adMobConfig
import com.company.admob.fakes.FakeConsentSdk
import com.company.admob.fakes.TestClock
import com.company.admob.internal.config.EffectiveConfig
import com.company.admob.internal.events.EventBus
import com.company.admob.internal.logging.AdLog
import com.company.admob.logging.LogLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

/** The splash's consent limit: the form gets 8 seconds to appear, and is never shown after that. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ConsentFormTimeoutTest {
    private inner class Harness(scope: TestScope, setup: FakeConsentSdk.() -> Unit) {
        val sdk = FakeConsentSdk(scope).apply {
            status = ConsentStatus.REQUIRED
            canRequestAds = false
            setup()
        }
        var initCalls = 0
        val results = mutableListOf<ConsentState>()
        val coordinator = ConsentCoordinator(
            consentSdk = sdk,
            config = EffectiveConfig.from(adMobConfig { }),
            log = AdLog({ _, _, _, _ -> }, LogLevel.NONE),
            events = EventBus(AdLog({ _, _, _, _ -> }, LogLevel.NONE)),
            clock = TestClock(scope.testScheduler),
            onCanRequestAds = { initCalls++ },
            onConsentRevoked = {},
            schedule = { delay, block ->
                val job = scope.launch {
                    delay(delay)
                    block()
                }
                ({ job.cancel() })
            },
        )
        val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        fun gather() = coordinator.gather(activity, 8.seconds) { results += it }
    }

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `a form that appears within the limit is shown, and the user can take as long as they like`() = runTest {
        val h = Harness(this) {
            formLoadDelayMs = 3_000
            formOpenMs = 60_000
            onFormShown = { canRequestAds = true }
        }
        h.gather()
        advance(3_100)
        assertEquals(1, h.sdk.formShowCount)
        advance(30_000) // past the limit, with the form open
        assertTrue(h.results.isEmpty())
        advance(30_000)
        assertEquals(1, h.results.size)
        assertTrue(h.results.single().canRequestAds)
    }

    @Test
    fun `a shown form reports itself until the user answers`() = runTest {
        val h = Harness(this) {
            formLoadDelayMs = 1_000
            formOpenMs = 20_000
        }
        h.gather()
        advance(500)
        assertFalse(h.coordinator.isFormShowing)
        advance(1_000)
        assertTrue(h.coordinator.isFormShowing)
        advance(20_000)
        assertFalse(h.coordinator.isFormShowing)
        assertEquals(1, h.results.size)
    }

    @Test
    fun `the form's screen closes before an answer - the next splash shows the form again and the gather waits`() =
        runTest {
            val controller = Robolectric.buildActivity(Activity::class.java).setup()
            val h = Harness(this) {
                formLoadDelayMs = 1_000
                formOpenMs = 600_000 // the first form never answers: its screen is gone
            }
            h.coordinator.gather(controller.get(), 8.seconds) { h.results += it }
            advance(1_100)
            assertEquals(1, h.sdk.formShowCount)
            controller.pause().stop().destroy()
            assertFalse(h.coordinator.isFormShowing)
            assertTrue(h.coordinator.isGathering)

            // A new splash: the form shows again on it, and this time the user answers.
            h.sdk.formOpenMs = 5_000
            h.sdk.onFormShown = { status = ConsentStatus.OBTAINED; canRequestAds = true }
            val next = Robolectric.buildActivity(Activity::class.java).setup().get()
            h.coordinator.gather(next, 8.seconds) { h.results += it }
            advance(1_100)
            assertEquals(2, h.sdk.formShowCount)
            assertTrue(h.coordinator.isFormShowing)
            advance(5_000)
            assertEquals(2, h.results.size)
            assertTrue(h.results.all { it.canRequestAds })
            // The first form's late answer (if any) changes nothing.
            advance(600_000)
            assertEquals(2, h.results.size)
        }

    @Test
    fun `a form that loads after the limit is never shown, and the gather finished at the limit`() = runTest {
        val h = Harness(this) { formLoadDelayMs = 12_000 }
        h.gather()
        advance(7_900)
        assertTrue(h.results.isEmpty())
        advance(200)
        assertEquals(1, h.results.size)
        advance(10_000)
        assertEquals(1, h.sdk.formLoads)
        assertEquals(0, h.sdk.formShowCount)
        assertEquals(1, h.results.size)
    }

    @Test
    fun `a consent update that takes longer than the limit ends the gather at the limit, and still counts later`() =
        runTest {
            val h = Harness(this) {
                updateDelayMs = 20_000
                status = ConsentStatus.OBTAINED
            }
            h.gather()
            advance(8_100)
            assertEquals(1, h.results.size)
            assertEquals(0, h.initCalls)
            // The update finally answers: previous consent allows ads, so ads start after all.
            h.sdk.canRequestAds = true
            advance(12_000)
            assertEquals(0, h.sdk.formShowCount)
            assertEquals(1, h.initCalls)
        }

    @Test
    fun `no form required - the gather ends as soon as the update does`() = runTest {
        val h = Harness(this) {
            status = ConsentStatus.NOT_REQUIRED
            canRequestAds = true
        }
        h.gather()
        advance(100)
        assertEquals(1, h.results.size)
        assertEquals(0, h.sdk.formShowCount)
    }
}
