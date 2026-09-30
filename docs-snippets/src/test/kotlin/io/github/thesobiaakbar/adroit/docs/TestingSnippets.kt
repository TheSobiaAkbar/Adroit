package io.github.thesobiaakbar.adroit.docs

import android.app.Activity
import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.FullScreenResult
import io.github.thesobiaakbar.adroit.Reward
import io.github.thesobiaakbar.adroit.RewardResult
import io.github.thesobiaakbar.adroit.testing.AdroitTestRule
import io.github.thesobiaakbar.adroit.testing.FakeAdsClient
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
/** For code that calls the Adroit object directly: the rule makes it a FakeAdsClient for each test. */
@RunWith(RobolectricTestRunner::class)
class LevelFlowTest {
    @get:Rule
    val admob = AdroitTestRule()

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
