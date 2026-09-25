package com.company.admob.internal.fullscreen

import com.company.admob.AdError
import com.company.admob.AdPlacement
import com.company.admob.AdsState
import com.company.admob.RewardResult
import com.company.admob.RewardedInterstitialPlacement
import com.company.admob.RewardedPlacement
import com.company.admob.UnavailableReason
import com.company.admob.config.TestIds
import com.company.admob.fakes.FakeAdsSdk.ScriptedLoad
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters

/** The Prompt 08 gate, expiry and duplicate-show cases, once for each rewarded format. */
@RunWith(ParameterizedRobolectricTestRunner::class)
class RewardedFormatGatesTest(private val formatName: String) {
    private val rewarded = formatName == "rewarded"
    private val first: AdPlacement = if (rewarded) RewardedPlacement("a") else RewardedInterstitialPlacement("a")
    private val second: AdPlacement = if (rewarded) RewardedPlacement("b") else RewardedInterstitialPlacement("b")
    private val unknown: AdPlacement = if (rewarded) RewardedPlacement("nope") else RewardedInterstitialPlacement("nope")

    private fun TestScope.harness(ready: Boolean = true): EngineHarness = EngineHarness(
        this,
        engineConfig {
            placements {
                if (rewarded) {
                    rewarded(first as RewardedPlacement, TestIds.unit(1))
                    rewarded(second as RewardedPlacement, TestIds.unit(2))
                } else {
                    rewardedInterstitial(first as RewardedInterstitialPlacement, TestIds.unit(1))
                    rewardedInterstitial(second as RewardedInterstitialPlacement, TestIds.unit(2))
                }
            }
        },
        if (rewarded) FullScreenFormatStrategy.Rewarded() else FullScreenFormatStrategy.RewardedInterstitial(),
    ).apply {
        if (ready) {
            ready()
            run(1_000)
        }
    }

    private fun failed(error: AdError) = RewardResult.Failed(error)

    @Test
    fun `readiness gate`() = runTest {
        val h = harness(ready = false)
        h.showRewarded(first)
        h.readiness.state.value = AdsState.Unavailable(UnavailableReason.CONSENT_NOT_OBTAINED)
        h.showRewarded(first)
        h.run()
        assertEquals(listOf(failed(AdError.NotInitialized), failed(AdError.ConsentRequired)), h.rewardResults)
    }

    @Test
    fun `unknown placement`() = runTest {
        val h = harness()
        h.showRewarded(unknown)
        h.run()
        assertTrue(((h.rewardResults.single() as RewardResult.Failed).error) is AdError.InvalidConfiguration)
    }

    @Test
    fun `invalid Activity`() = runTest {
        val h = harness()
        val activity = h.activity()
        h.tracker.invalidActivities += activity
        h.showRewarded(first, activity = activity)
        h.run()
        assertEquals(listOf(failed(AdError.ActivityUnavailable)), h.rewardResults)
        assertTrue(h.engine.isReady(first))
    }

    @Test
    fun `duplicate show - AlreadyShowing, and one ad never shows twice`() = runTest {
        val h = harness()
        h.showRewarded(first)
        h.showRewarded(second)
        h.showRewarded(first)
        h.run()
        assertEquals(listOf(failed(AdError.AlreadyShowing), failed(AdError.AlreadyShowing)), h.rewardResults)
        assertEquals(listOf(1, 0), h.adsSdk.fullScreenAds.map { it.showCount })
    }

    @Test
    fun `not ready - NotReady and a load starts`() = runTest {
        val h = harness(ready = false)
        h.adsSdk.defaultLoad = ScriptedLoad.Pending
        h.ready()
        h.engine.clear(first)
        val before = h.adsSdk.loadCalls.size
        h.showRewarded(first)
        h.run()
        assertEquals(listOf(failed(AdError.NotReady)), h.rewardResults)
        assertEquals(before + 1, h.adsSdk.loadCalls.size)
    }

    @Test
    fun `expired - not shown, and a new one loads`() = runTest {
        val h = harness()
        val stale = h.adsSdk.fullScreenAds.first()
        h.foreground.moveToBackground()
        h.run(55 * 60_000L + 1)
        h.showRewarded(first)
        h.run()
        assertEquals(listOf(failed(AdError.Expired)), h.rewardResults)
        assertEquals(0, stale.showCount)
        assertTrue(stale.released)
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun formats(): List<Array<Any>> = listOf(arrayOf("rewarded"), arrayOf("rewarded interstitial"))
    }
}
