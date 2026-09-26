package com.company.admob.docs

import android.app.Activity
import com.company.admob.AdError
import com.company.admob.FullScreenResult
import com.company.admob.Reward
import com.company.admob.RewardResult
import com.company.admob.testing.CompanyAdMobTestRule
import com.company.admob.testing.FakeAdsClient
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// region fake-client
@RunWith(RobolectricTestRunner::class)
class CoinsViewModelTest {
    private val ads = FakeAdsClient()
    private val activity = Activity() // FakeAdsClient never touches it

    @Test
    fun `an earned reward is granted once`() {
        val viewModel = CoinsViewModel(ads)
        ads.enqueueRewardResult(Placements.ExtraLife, RewardResult.Earned(Reward("coins", 50), "reward-1"))

        viewModel.watchAdForCoins(activity)

        assertEquals(50, viewModel.coins.value)
    }
}
// endregion

// region test-rule
/** For code that calls the CompanyAdMob object directly: the rule makes it a FakeAdsClient for each test. */
@RunWith(RobolectricTestRunner::class)
class LevelFlowTest {
    @get:Rule
    val admob = CompanyAdMobTestRule()

    @Test
    fun `the next level opens whether or not the interstitial shows`() {
        admob.client.enqueueInterstitialResult(
            Placements.LevelComplete,
            FullScreenResult.NotShown(AdError.NotReady),
        )
        var opened = false

        onLevelComplete(Activity()) { opened = true }

        assertEquals(true, opened)
    }
}
// endregion
