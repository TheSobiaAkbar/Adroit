package com.company.admob.internal.fullscreen

import com.company.admob.AdError
import com.company.admob.AdFormat
import com.company.admob.Reward
import com.company.admob.RewardResult
import com.company.admob.RewardedShowOptions
import com.company.admob.config.TestIds
import com.company.admob.events.AdEvent
import com.company.admob.internal.logging.AdLog
import com.company.admob.logging.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

@RunWith(RobolectricTestRunner::class)
class RewardTest {
    private val coins = Reward("coins", 10)

    private fun TestScope.rewarded(): EngineHarness = EngineHarness(
        this,
        engineConfig {
            placements {
                rewarded(EXTRA_LIFE, TestIds.unit(3))
                interstitial(LEVEL_END, TestIds.unit(1))
                rewardedInterstitial(BONUS, TestIds.unit(4))
            }
        },
        FullScreenFormatStrategy.Rewarded(),
    ).apply {
        ready()
        run(1_000)
    }

    /** Shows [EXTRA_LIFE] and returns its fake ad. */
    private fun EngineHarness.showing(customData: String? = null) = run {
        showRewarded(EXTRA_LIFE, customData)
        run()
        adsSdk.fullScreenAds.single { it.showCount == 1 }
    }

    @Test
    fun `earned then dismissed - Earned once, with the same rewardId in the event and the result`() = runTest {
        val h = rewarded()
        val ad = h.showing()
        ad.simulateShown()
        ad.simulateReward(coins)
        ad.simulateDismissed()
        h.run(300)

        val earned = h.rewardResults.single() as RewardResult.Earned
        assertEquals(coins, earned.reward)
        val event = h.events.recent().filterIsInstance<AdEvent.RewardEarned>().single()
        assertEquals(earned.rewardId, event.rewardId)
        assertEquals(coins, event.reward)
    }

    @Test
    fun `dismissed without a reward - NotEarned`() = runTest {
        val h = rewarded()
        h.showing().simulateFullShow()
        h.run(300)
        assertEquals(listOf<RewardResult>(RewardResult.NotEarned), h.rewardResults)
    }

    @Test
    fun `two reward callbacks give one reward`() = runTest {
        val h = rewarded()
        val ad = h.showing()
        ad.simulateShown()
        ad.simulateReward(coins)
        ad.simulateReward(Reward("gems", 99))
        ad.simulateDismissed()
        h.run(300)

        assertEquals(coins, (h.rewardResults.single() as RewardResult.Earned).reward)
        assertEquals(1, h.events.recent().filterIsInstance<AdEvent.RewardEarned>().size)
    }

    @Test
    fun `a reward within the grace window after dismissal counts`() = runTest {
        val h = rewarded()
        val ad = h.showing()
        ad.simulateShown()
        ad.simulateDismissed()
        h.run(200)
        assertTrue("still within the window", h.rewardResults.isEmpty())
        ad.simulateReward(coins)
        h.run(100)

        assertEquals(coins, (h.rewardResults.single() as RewardResult.Earned).reward)
        assertTrue(ad.released)
    }

    @Test
    fun `a reward after the grace window doesn't count`() = runTest {
        val h = rewarded()
        val ad = h.showing()
        val listener = ad.listener!!
        ad.simulateShown()
        ad.simulateDismissed()
        h.run(300)
        listener.onRewardEarned(coins)
        h.run()

        assertEquals(listOf<RewardResult>(RewardResult.NotEarned), h.rewardResults)
        assertTrue(h.events.recent().none { it is AdEvent.RewardEarned })
    }

    @Test
    fun `the grace window is configurable`() = runTest {
        val h = EngineHarness(
            this,
            engineConfig { placements { rewarded(EXTRA_LIFE, TestIds.unit(3)) } },
            FullScreenFormatStrategy.Rewarded(dismissGrace = 50.milliseconds),
        ).apply { ready(); run(1_000) }
        h.showing().simulateFullShow()
        h.run(50)
        assertEquals(listOf<RewardResult>(RewardResult.NotEarned), h.rewardResults)
        assertEquals(300.milliseconds, FullScreenFormatStrategy.DEFAULT_REWARD_GRACE)
    }

    @Test
    fun `failed to show - Failed, no reward`() = runTest {
        val h = rewarded()
        val failure = AdError.ShowFailed()
        h.showing().simulateFailedToShow(failure)
        h.run()
        assertEquals(listOf<RewardResult>(RewardResult.Failed(failure)), h.rewardResults)
    }

    @Test
    fun `watchdog - Failed(Timeout)`() = runTest {
        val h = rewarded()
        h.showing()
        h.run(5_000)
        assertEquals(listOf<RewardResult>(RewardResult.Failed(AdError.Timeout())), h.rewardResults)
        assertFalse(h.coordinator.isShowing)
    }

    @Test
    fun `SSV custom data is set on the ad before show`() = runTest {
        val h = rewarded()
        val ad = h.showing(customData = "user-42:level-3")
        assertEquals("user-42:level-3", ad.serverSideVerificationDataAtShow)
    }

    @Test
    fun `rewarded skips the global interval after an interstitial, rewarded interstitial doesn't`() = runTest {
        val h = rewarded()
        val interstitial = h.engine(FullScreenFormatStrategy.Interstitial)
        val rewardedInterstitial = h.engine(FullScreenFormatStrategy.RewardedInterstitial())
        h.run(1_000)
        interstitial.show(h.activity(), LEVEL_END) {}
        h.run()
        h.adsSdk.fullScreenAds.single { it.showCount == 1 }.simulateFullShow()
        h.run(1_000)

        h.showRewarded(EXTRA_LIFE)
        h.run()
        assertTrue("rewarded showed", h.adsSdk.fullScreenAds.any { it.format == AdFormat.REWARDED && it.showCount == 1 })
        h.adsSdk.fullScreenAds.single { it.format == AdFormat.REWARDED && it.showCount == 1 }.simulateFullShow()
        h.run(1_000)

        val riResults = mutableListOf<RewardResult>()
        rewardedInterstitial.showRewarded(h.activity(), BONUS, null) { riResults += it }
        h.run()
        val capped = riResults.single() as RewardResult.Failed
        assertTrue(capped.error is AdError.FrequencyCapped)
    }

    // --- Suspend API and unclaimed rewards ---

    private fun TestScope.api(h: EngineHarness) = RewardedApiImpl(h.engine, UnclaimedRewards(h.log))

    @Test
    fun `showAndAwait returns the result`() = runTest {
        val h = rewarded()
        val api = api(h)
        val result = async { api.showAndAwait(h.activity(), EXTRA_LIFE, RewardedShowOptions()) }
        h.run()
        val ad = h.adsSdk.fullScreenAds.single { it.showCount == 1 }
        ad.simulateShown()
        ad.simulateReward(coins)
        ad.simulateDismissed()
        h.run(300)

        assertEquals(coins, (result.await() as RewardResult.Earned).reward)
    }

    @Test
    fun `caller cancelled mid-ad, then earned - one unclaimed reward, the caller isn't resumed`() = runTest {
        val h = rewarded()
        val unclaimed = UnclaimedRewards(h.log)
        val api = RewardedApiImpl(h.engine, unclaimed)
        var resumed = false
        val caller = launch { api.showAndAwait(h.activity(), EXTRA_LIFE, RewardedShowOptions()); resumed = true }
        h.run()
        val ad = h.adsSdk.fullScreenAds.single { it.showCount == 1 }
        ad.simulateShown()
        caller.cancel() // for example, a configuration change destroyed the lifecycleScope
        h.run()
        assertTrue("the ad keeps showing", h.coordinator.isShowing)

        ad.simulateReward(coins)
        ad.simulateDismissed()
        h.run(300)

        assertFalse(resumed)
        assertEquals(coins, unclaimed.poll()?.reward)
        assertNull(unclaimed.poll())
        assertFalse(h.coordinator.isShowing)
    }

    @Test
    fun `caller cancelled, then not earned - nothing is emitted`() = runTest {
        val h = rewarded()
        val unclaimed = UnclaimedRewards(h.log)
        val api = RewardedApiImpl(h.engine, unclaimed)
        val caller = launch { api.showAndAwait(h.activity(), EXTRA_LIFE, RewardedShowOptions()) }
        h.run()
        caller.cancel()
        h.adsSdk.fullScreenAds.single { it.showCount == 1 }.simulateFullShow()
        h.run(300)
        assertNull(unclaimed.poll())
    }

    @Test
    fun `race - resolution vs caller cancellation from different threads, 10,000 times`() {
        val pool = Executors.newFixedThreadPool(4)
        val dispatcher = pool.asCoroutineDispatcher()
        val log = AdLog({ _, _, _, _ -> }, LogLevel.NONE)
        try {
            repeat(10_000) { i ->
                val unclaimed = UnclaimedRewards(log)
                val started = CountDownLatch(1)
                var deliver: ((RewardResult) -> Unit)? = null
                val received = AtomicInteger()
                val caller = CoroutineScope(dispatcher).launch {
                    unclaimed.await { callback ->
                        deliver = callback
                        started.countDown()
                    }
                    received.incrementAndGet()
                }
                started.await()
                val go = CountDownLatch(1)
                val done = CountDownLatch(2)
                pool.execute { go.await(); caller.cancel(); done.countDown() }
                pool.execute { go.await(); deliver!!(RewardResult.Earned(coins, "reward-$i")); done.countDown() }
                go.countDown()
                done.await()
                runBlocking { caller.join() }

                val unclaimedCount = generateSequence { unclaimed.poll() }.count()
                assertEquals("iteration $i: exactly one destination", 1, received.get() + unclaimedCount)
            }
        } finally {
            dispatcher.close()
        }
    }
}
