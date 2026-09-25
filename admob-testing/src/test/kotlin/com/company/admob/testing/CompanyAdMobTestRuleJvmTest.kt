package com.company.admob.testing

import android.app.Activity
import com.company.admob.CompanyAdMob
import com.company.admob.RewardResult
import com.company.admob.RewardedPlacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The rule as a host uses it: annotated, on the plain JVM (no Robolectric), with code that calls CompanyAdMob. */
class CompanyAdMobTestRuleJvmTest {
    @get:Rule
    val ads = CompanyAdMobTestRule()

    private val bonus = RewardedPlacement("bonus")

    /** Host code that calls the facade directly instead of an injected AdsClient. */
    private fun claimBonus(activity: Activity, onCoins: (Int) -> Unit) {
        CompanyAdMob.showRewarded(activity, bonus) { result ->
            onCoins(if (result is RewardResult.Earned) result.reward.amount else 0)
        }
    }

    @Test
    fun `code calling CompanyAdMob directly gets the scripted result`() {
        ads.client.enqueueRewardResult(bonus, RewardResult.Earned(com.company.admob.Reward("coins", 7), "id-1"))
        var coins = -1
        claimBonus(Activity()) { coins = it }
        assertEquals(7, coins)
        assertEquals(listOf<FakeCall>(FakeCall.ShowRewarded("bonus")), ads.client.recordedCalls)
    }
}
