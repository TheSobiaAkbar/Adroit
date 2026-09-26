package com.company.admob.sample.compose.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.company.admob.AdAvailability
import com.company.admob.AdError
import com.company.admob.AdPlacement
import com.company.admob.FullScreenResult
import com.company.admob.compose.rememberAdAvailability
import com.company.admob.compose.rememberFullScreenAdLauncher
import com.company.admob.sample.compose.AppContainer
import com.company.admob.sample.compose.ads.Placements
import com.company.admob.sample.compose.ui.PlaygroundViewModel
import com.company.admob.sample.compose.ui.components.Hint
import com.company.admob.sample.compose.ui.components.LabeledValue
import com.company.admob.sample.compose.ui.components.ScreenColumn
import com.company.admob.sample.compose.ui.components.Section
import com.company.admob.sample.compose.ui.components.StatusChip
import com.company.admob.sample.compose.ui.components.describe
import com.company.admob.sample.compose.ui.components.label
import com.company.admob.sample.compose.ui.components.short
import com.company.admob.sample.compose.ui.components.tone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterstitialScreen(container: AppContainer, viewModel: PlaygroundViewModel) {
    val api = container.ads.interstitial
    val placement = Placements.Interstitial
    val launcher = rememberFullScreenAdLauncher()
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val lastResult by viewModel.lastInterstitial.collectAsStateWithLifecycle()

    ScreenColumn {
        AvailabilitySection(placement)
        Section("Actions") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { api.preload(placement) }) { Text("Preload") }
                Button(onClick = { launcher.showInterstitial(placement, viewModel::onInterstitialResult) }) {
                    Text("Show (callback)")
                }
                Button(onClick = {
                    // The result survives in the ViewModel. If this screen is left while the ad shows, the ad
                    // continues but this coroutine is cancelled, so the result isn't recorded.
                    activity?.let { scope.launch { viewModel.onInterstitialResult(api.showAndAwait(it, placement)) } }
                }) { Text("Show (suspend)") }
                TextButton(onClick = { api.clear(placement) }) { Text("Clear") }
            }
        }
        Section("Last result") {
            LabeledValue("Result", lastResult.describe())
            val capped = ((lastResult as? FullScreenResult.NotShown)?.error as? AdError.FrequencyCapped)
            if (capped != null) {
                Hint(
                    "Frequency capped: this placement allows one show per 45 s, and all full-screen ads share a " +
                        "30 s interval. Retry after ${capped.retryAfter.short()} (from the time of that result).",
                )
            } else {
                Hint("Show twice in a row to see the cooldown: the second result is NotShown(FrequencyCapped).")
            }
        }
    }
}

@Composable
fun RewardedScreen(container: AppContainer, viewModel: PlaygroundViewModel) {
    val placement = Placements.Rewarded
    val launcher = rememberFullScreenAdLauncher()
    val coins by viewModel.coins.collectAsStateWithLifecycle()
    val unclaimed by viewModel.unclaimedGrants.collectAsStateWithLifecycle()
    val lastResult by viewModel.lastReward.collectAsStateWithLifecycle()

    ScreenColumn {
        CoinsSection(coins, unclaimed)
        AvailabilitySection(placement)
        Section("Actions") {
            OutlinedButton(onClick = { container.ads.rewarded.preload(placement) }, Modifier.fillMaxWidth()) {
                Text("Preload")
            }
            Button(onClick = { launcher.showRewarded(placement, viewModel::onRewardResult) }, Modifier.fillMaxWidth()) {
                Text("Watch an ad for coins")
            }
            LabeledValue("Last result", lastResult.describe())
        }
    }
}

/** Google's rule for rewarded interstitials: an intro screen offering the reward, with a way to opt out, first. */
@Composable
fun RewardedInterstitialScreen(container: AppContainer, viewModel: PlaygroundViewModel) {
    val placement = Placements.RewardedInterstitial
    val launcher = rememberFullScreenAdLauncher()
    val coins by viewModel.coins.collectAsStateWithLifecycle()
    val unclaimed by viewModel.unclaimedGrants.collectAsStateWithLifecycle()
    val lastResult by viewModel.lastReward.collectAsStateWithLifecycle()
    // Saved, so a rotation during the countdown resumes it instead of restarting or skipping it.
    var countdown by rememberSaveable { mutableIntStateOf(NO_INTRO) }

    if (countdown > 0) {
        LaunchedEffect(countdown) {
            delay(1_000)
            countdown--
            if (countdown == 0) {
                launcher.showRewardedInterstitial(placement, viewModel::onRewardResult)
                countdown = NO_INTRO
            }
        }
    }

    ScreenColumn {
        CoinsSection(coins, unclaimed)
        AvailabilitySection(placement)
        if (countdown > 0) {
            Section("Bonus round") {
                Text("Watch a short ad to earn bonus coins?")
                Text("The ad starts in $countdown s")
                TextButton(onClick = { countdown = NO_INTRO }) { Text("No thanks") }
            }
        } else {
            Section("Actions") {
                OutlinedButton(
                    onClick = { container.ads.rewardedInterstitial.preload(placement) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Preload") }
                Button(onClick = { countdown = INTRO_SECONDS }, Modifier.fillMaxWidth()) { Text("Start bonus round") }
                LabeledValue("Last result", lastResult.describe())
            }
        }
    }
}

private const val INTRO_SECONDS = 5
private const val NO_INTRO = -1

@Composable
private fun CoinsSection(coins: Int, unclaimedGrants: Int) {
    Section("Coins") {
        Text("$coins coins")
        Hint(
            "Granted once per rewardId. $unclaimedGrants reward(s) arrived late through unclaimedResults " +
                "(the screen was gone when the ad closed).",
        )
    }
}

@Composable
internal fun AvailabilitySection(placement: AdPlacement) {
    val availability by rememberAdAvailability(placement)
    Section("Availability") {
        StatusChip(availability.label(), availability.tone())
        (availability as? AdAvailability.Failed)?.let { Hint(it.error.describe()) }
    }
}
