package com.company.admob.testing.sample

import android.app.Activity
import com.company.admob.AdAvailability
import com.company.admob.AdError
import com.company.admob.FullScreenResult
import com.company.admob.Reward
import com.company.admob.RewardResult
import com.company.admob.testing.FakeAdsClient
import com.company.admob.testing.FakeCall
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

/**
 * A host app's ViewModel test on the plain JVM: no Robolectric, no Google SDK, no main looper; only admob-testing.
 * The Activity is never used by the fake, so a bare instance is enough.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LevelViewModelTest {
    private val ads = FakeAdsClient()
    private val activity = Activity()

    @Test
    fun `preloads both placements on creation`() = runTest {
        LevelViewModel(ads, backgroundScope)
        assertEquals(listOf<FakeCall>(FakeCall.Preload(listOf("level_end", "extra_coins"))), ads.recordedCalls)
    }

    @Test
    fun `the next level starts whether or not the interstitial showed`() = runTest {
        val vm = LevelViewModel(ads, backgroundScope)
        ads.enqueueInterstitialResult(Placements.LevelEnd, FullScreenResult.Dismissed(clicked = false))

        vm.onLevelComplete(activity) // shown
        vm.onLevelComplete(activity) // queue empty: NotShown(NotReady)

        assertEquals(3, vm.level.value)
        assertEquals(
            listOf(FakeCall.ShowInterstitial("level_end"), FakeCall.ShowInterstitial("level_end")),
            ads.recordedCalls.filterIsInstance<FakeCall.ShowInterstitial>(),
        )
    }

    @Test
    fun `an earned reward grants coins, a skipped or failed one doesn't`() = runTest {
        val vm = LevelViewModel(ads, backgroundScope)
        ads.enqueueRewardResult(Placements.ExtraCoins, RewardResult.Earned(Reward("coins", 50), rewardId = "r1"))
        ads.enqueueRewardResult(Placements.ExtraCoins, RewardResult.NotEarned)
        ads.enqueueRewardResult(Placements.ExtraCoins, RewardResult.Failed(AdError.NoFill()))

        repeat(3) { vm.onWatchAdForCoins(activity) }

        assertEquals(50, vm.coins.value)
        assertEquals(3, ads.recordedCalls.count { it is FakeCall.ShowRewarded })
    }

    @Test
    fun `a reward delivered late through unclaimedResults is granted once`() = runTest {
        val vm = LevelViewModel(ads, backgroundScope)
        val reward = RewardResult.Earned(Reward("coins", 20), rewardId = "late-1")
        runCurrent()

        ads.emitUnclaimedReward(reward)
        ads.emitUnclaimedReward(reward) // the same reward twice must not grant twice
        runCurrent()

        assertEquals(20, vm.coins.value)
    }

    @Test
    fun `the watch-ad button follows availability`() = runTest {
        val vm = LevelViewModel(ads, backgroundScope)
        assertFalse(vm.rewardAvailable)
        ads.setAvailability(Placements.ExtraCoins, AdAvailability.Ready)
        assertTrue(vm.rewardAvailable)
    }

    @Test
    fun `with a callback executor, results arrive when it runs them, not inside the call`() = runTest {
        val queued = mutableListOf<Runnable>()
        val deferred = FakeAdsClient(callbackExecutor = Executor { queued += it })
        val vm = LevelViewModel(deferred, backgroundScope)

        vm.onLevelComplete(activity)
        assertEquals(1, vm.level.value)
        queued.forEach(Runnable::run)
        assertEquals(2, vm.level.value)
    }
}
