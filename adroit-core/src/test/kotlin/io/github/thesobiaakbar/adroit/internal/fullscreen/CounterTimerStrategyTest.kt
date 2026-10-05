package io.github.thesobiaakbar.adroit.internal.fullscreen

import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.AdFormat
import io.github.thesobiaakbar.adroit.FullScreenResult
import io.github.thesobiaakbar.adroit.InterstitialPlacement
import io.github.thesobiaakbar.adroit.config.AdDefinition
import io.github.thesobiaakbar.adroit.config.AdProfile
import io.github.thesobiaakbar.adroit.config.AdSettings
import io.github.thesobiaakbar.adroit.config.TestIds
import io.github.thesobiaakbar.adroit.fakes.FakeAdsSdk.ScriptedLoad
import io.github.thesobiaakbar.adroit.internal.config.AdProfiles
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration

/** The counter (start, step, several max counters, reset on fail, loadAhead) and the timer (docs/AD_PROFILES.md). */
@RunWith(RobolectricTestRunner::class)
class CounterTimerStrategyTest {
    // loadTimeoutSeconds = 0: a show never waits for a load, so each call's result is immediate.
    private enum class Defs(override val defaults: AdProfile) : AdDefinition {
        Cycle(AdProfile.interstitial(ids = listOf(TestIds.unit(1)), loadTimeoutSeconds = 0, counters = listOf(3, 2), counterStart = 1)),
        Plain(AdProfile.interstitial(ids = listOf(TestIds.unit(2)), counter = 3, loadTimeoutSeconds = 0)),
        Timed(AdProfile.interstitial(ids = listOf(TestIds.unit(3)), timerSeconds = 30, loadTimeoutSeconds = 0)),
    }

    private val cycle = InterstitialPlacement("Cycle")
    private val plain = InterstitialPlacement("Plain")
    private val timed = InterstitialPlacement("Timed")

    private fun TestScope.harness(): EngineHarness = EngineHarness(
        this,
        engineConfig {
            frequency { minIntervalBetweenFullScreenAds = Duration.ZERO }
            profiles(Defs.entries, AdSettings())
        },
    ).apply { ready(); run(1_000) }

    private fun EngineHarness.remote(key: String, json: String) {
        val parsed = AdProfiles.parseRemote(Defs.entries.associate { it.name to AdFormat.INTERSTITIAL }, mapOf(key to json), null)
        assertEquals(emptyList<String>(), parsed.issues)
        live.update(live.current.withOverrides(parsed.overrides))
    }

    /** One `show` call: "shown" (and dismissed), or the error's code. */
    private fun EngineHarness.call(placement: InterstitialPlacement): String {
        results.clear()
        show(placement)
        run()
        adsSdk.fullScreenAds.lastOrNull { it.showCount > 0 && !it.released }?.simulateFullShow()
        run(1_000)
        return when (val result = results.single()) {
            is FullScreenResult.Dismissed -> "shown"
            is FullScreenResult.NotShown -> (result.error as? AdError.CounterNotReached)?.let { "wait ${it.remaining}" } ?: result.error.code
        }
    }

    private fun EngineHarness.loads() = adsSdk.loadCalls.size

    @Test
    fun `the counter starts at counterStart, then goes through the max counters in a loop`() = runTest {
        val h = harness()
        // Start 1, max 3: due on the 2nd call. Then from 0 to max 2, then from 0 to max 3 again.
        assertEquals(
            listOf("wait 1", "shown", "wait 1", "shown", "wait 2", "wait 1", "shown", "wait 1", "shown"),
            List(9) { h.call(cycle) },
        )
    }

    @Test
    fun `counterStep is what each call adds, and Firebase sets it`() = runTest {
        val h = harness()
        h.remote("Plain", """{ "counter": 1.5, "counterStep": 0.5 }""")
        assertEquals(listOf("wait 2", "wait 1", "shown", "wait 2"), List(4) { h.call(plain) })
    }

    @Test
    fun `a due ad that can't show is tried again on the next call, or counted again with counterResetOnFail`() = runTest {
        val keep = harness()
        keep.adsSdk.enqueueLoad(TestIds.unit(2), ScriptedLoad.Failure(AdError.NoFill(), retryable = true))
        assertEquals(listOf("wait 2", "wait 1", "not_ready", "shown"), List(4) { keep.call(plain) })

        val reset = harness()
        reset.remote("Plain", """{ "counterResetOnFail": true }""")
        reset.adsSdk.enqueueLoad(TestIds.unit(2), ScriptedLoad.Failure(AdError.NoFill(), retryable = true))
        assertEquals(listOf("wait 2", "wait 1", "not_ready", "wait 2", "wait 1", "shown"), List(6) { reset.call(plain) })
    }

    @Test
    fun `loadAhead says how many calls before it's due the ad loads`() = runTest {
        val default = harness()
        default.call(plain)
        assertEquals("two calls to go: not yet", 0, default.loads())
        default.call(plain)
        assertEquals("one call before it's due", 1, default.loads())

        val early = harness()
        early.remote("Plain", """{ "loadAhead": 2 }""")
        early.call(plain)
        assertEquals(1, early.loads())

        val late = harness()
        late.remote("Plain", """{ "loadAhead": 0 }""")
        assertEquals(listOf("wait 2", "wait 1"), List(2) { late.call(plain) })
        assertEquals(0, late.loads())
        assertEquals("loaded on the call it's due, shown on the next", listOf("not_ready", "shown"), List(2) { late.call(plain) })
    }

    @Test
    fun `an ad on a timer loads again when the timer ends, once, and waits to be shown`() = runTest {
        val h = harness()
        h.engine.preload(timed)
        h.run(1_000)
        assertEquals("shown", h.call(timed))
        h.engine.preload(timed)
        h.run(27_000)
        assertEquals("nothing loads while the timer runs", 1, h.loads())
        assertTrue(h.call(timed).startsWith("frequency"))
        h.run(3_000)
        assertEquals(2, h.loads())
        assertTrue(h.engine.isReady(timed))
        h.run(600_000)
        assertEquals("a loaded ad is kept: no further request until it's shown", 2, h.loads())
        assertEquals("shown", h.call(timed))
        h.run(31_000)
        assertEquals(3, h.loads())
    }

    @Test
    fun `the counter fields round-trip through a definition's JSON`() {
        val json = Defs.Cycle.defaults.toJson()
        assertTrue(json, """"counter":[3,2]""" in json && """"counterStart":1""" in json && """"loadAhead":1""" in json)
        assertTrue("an ad without a counter lists none of them", "counterStep" !in Defs.Timed.defaults.toJson())
    }
}
