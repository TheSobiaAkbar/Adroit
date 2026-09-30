package io.github.thesobiaakbar.adroit.testing

import android.app.Activity
import io.github.thesobiaakbar.adroit.Adroit
import io.github.thesobiaakbar.adroit.RewardResult
import io.github.thesobiaakbar.adroit.RewardedPlacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The rule as a host uses it: annotated, on the plain JVM (no Robolectric), with code that calls Adroit. */
class AdroitTestRuleJvmTest {
    @get:Rule
    val ads = AdroitTestRule()

    private val bonus = RewardedPlacement("bonus")

    /** Host code that calls the facade directly instead of an injected AdsClient. */
    private fun claimBonus(activity: Activity, onCoins: (Int) -> Unit) {
        Adroit.showRewarded(activity, bonus) { result ->
            onCoins(if (result is RewardResult.Earned) result.reward.amount else 0)
        }
    }

    @Test
    fun `code calling Adroit directly gets the scripted result`() {
        ads.client.enqueueRewardResult(bonus, RewardResult.Earned(io.github.thesobiaakbar.adroit.Reward("coins", 7), "id-1"))
        var coins = -1
        claimBonus(Activity()) { coins = it }
        assertEquals(7, coins)
        assertEquals(listOf<FakeCall>(FakeCall.ShowRewarded("bonus")), ads.client.recordedCalls)
    }
}
