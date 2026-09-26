@file:Suppress("unused")

package com.company.admob.docs

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.company.admob.AdAvailability
import com.company.admob.AdError
import com.company.admob.AdsClient
import com.company.admob.CompanyAdMob
import com.company.admob.FullScreenResult
import com.company.admob.RewardResult
import com.company.admob.RewardedInterstitialPlacement
import com.company.admob.RewardedShowOptions
import com.company.admob.compose.rememberAdAvailability
import com.company.admob.compose.rememberFullScreenAdLauncher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

// region interstitial-result
fun showBetweenLevels(activity: Activity, next: () -> Unit) {
    CompanyAdMob.interstitial.show(activity, Placements.LevelComplete) { result ->
        when (result) {
            is FullScreenResult.Dismissed -> next()
            is FullScreenResult.NotShown -> {
                val capped = result.error as? AdError.FrequencyCapped
                if (capped != null) println("Next interstitial possible in ${capped.retryAfter}")
                next() // not ready, capped, offline, ad-free...: never block the user
            }
        }
    }
}
// endregion

// region interstitial-suspend
class LevelEndActivity : AppCompatActivity() {
    fun onNextLevelClicked() {
        lifecycleScope.launch {
            CompanyAdMob.interstitial.showAndAwait(this@LevelEndActivity, Placements.LevelComplete)
            startActivity(Intent(this@LevelEndActivity, LauncherActivity::class.java))
        }
    }
}
// endregion

// region interstitial-preload
fun preloadForUpcomingBreak() {
    // Loads now if the placement's preload strategy didn't already: for ON_DEMAND placements.
    CompanyAdMob.interstitial.preload(Placements.LevelComplete)
    val readyNow = CompanyAdMob.interstitial.isReady(Placements.LevelComplete)
}
// endregion

// region rewarded-viewmodel
/** Grant rewards in a ViewModel, once per rewardId, including rewards that arrive after the screen is gone. */
class CoinsViewModel(private val ads: AdsClient = CompanyAdMob.client) : ViewModel() {
    private val granted = mutableSetOf<String>()
    private val mutableCoins = MutableStateFlow(0)
    val coins: StateFlow<Int> = mutableCoins.asStateFlow()

    init {
        // Earned while this screen was gone (rotation, process kept alive): delivered here instead.
        viewModelScope.launch {
            merge(ads.rewarded.unclaimedResults, ads.rewardedInterstitial.unclaimedResults).collect(::grant)
        }
    }

    fun watchAdForCoins(activity: Activity) {
        ads.rewarded.show(activity, Placements.ExtraLife, RewardedShowOptions()) { result ->
            when (result) {
                is RewardResult.Earned -> grant(result)
                RewardResult.NotEarned -> Unit // closed early: no reward
                is RewardResult.Failed -> Unit // not shown: offer the reward another way, or hide the button
            }
        }
    }

    private fun grant(reward: RewardResult.Earned) {
        if (granted.add(reward.rewardId)) mutableCoins.value += reward.reward.amount
    }
}
// endregion

// region rewarded-ssv
fun showWithServerSideVerification(activity: Activity, userId: String) {
    CompanyAdMob.rewarded.show(
        activity,
        Placements.ExtraLife,
        RewardedShowOptions(serverSideVerificationCustomData = "user=$userId"),
    ) { /* grant on your server's SSV callback, not here */ }
}
// endregion

// region rewarded-compose
@Composable
fun WatchAdButton(onReward: (RewardResult.Earned) -> Unit) {
    val availability by rememberAdAvailability(Placements.ExtraLife)
    val launcher = rememberFullScreenAdLauncher()
    Button(
        enabled = availability == AdAvailability.Ready,
        onClick = { launcher.showRewarded(Placements.ExtraLife) { if (it is RewardResult.Earned) onReward(it) } },
    ) { Text("Watch an ad for 50 coins") }
}
// endregion

val BonusRound = RewardedInterstitialPlacement("bonus_round")

// region rewarded-interstitial-intro
/** Google requires an intro screen that tells the user about the reward and lets them opt out, before the ad. */
@Composable
fun BonusIntro(onDone: () -> Unit, onReward: (RewardResult.Earned) -> Unit) {
    val launcher = rememberFullScreenAdLauncher()
    var secondsLeft by rememberSaveable { mutableIntStateOf(5) }
    LaunchedEffect(secondsLeft) {
        if (secondsLeft == 0) {
            launcher.showRewardedInterstitial(BonusRound) { result ->
                if (result is RewardResult.Earned) onReward(result)
                onDone()
            }
        } else {
            delay(1.seconds)
            secondsLeft--
        }
    }
    Text("Watch a short ad for bonus coins? The ad starts in $secondsLeft s.")
    TextButton(onClick = onDone) { Text("No thanks") }
}
// endregion

// region app-open-cold-start
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CompanyAdMob.start(this) {
            // Waits up to 4 s for an app open ad, shows it if one is ready, then continues whatever happened.
            CompanyAdMob.appOpen.showOnColdStart(this, maxWait = 4.seconds) { goToMain() }
        }
    }

    private fun goToMain() {
        if (isFinishing) return
        startActivity(Intent(this, LauncherActivity::class.java))
        finish()
    }
}
// endregion

// region app-open-controls
fun openSharesheet(activity: Activity, share: Intent) {
    // Returning from the share sheet mustn't show an app open ad.
    CompanyAdMob.appOpen.skipNextForeground()
    activity.startActivity(Intent.createChooser(share, null))
}

class CheckoutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No app open ad while this screen is at least STARTED; released automatically when it's destroyed.
        CompanyAdMob.appOpen.suppressWhile(this)
    }
}

fun onUserBoughtPremium() {
    CompanyAdMob.appOpen.setEnabled(false) // or CompanyAdMob.setAdFree(true) to stop every format
}
// endregion
