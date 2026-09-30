package io.github.thesobiaakbar.adroit.sample.compose.home

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.thesobiaakbar.adroit.compose.AdSlot
import io.github.thesobiaakbar.adroit.compose.rememberFullScreenAdLauncher
import io.github.thesobiaakbar.adroit.config.rewardedInterstitialPlacement
import io.github.thesobiaakbar.adroit.config.rewardedPlacement
import io.github.thesobiaakbar.adroit.sample.compose.R
import io.github.thesobiaakbar.adroit.sample.compose.ads.SampleAds

/**
 * The buttons show full-screen ads through [CoinsViewModel] and the Compose launcher. The home banner is an
 * [AdSlot] (a banner, or a native ad if Firebase swaps HomeBanner's format): it's destroyed when Home leaves the
 * screen, and a new one loads when Home comes back.
 */
@Composable
fun HomeScreen(coins: CoinsViewModel, onOpenDetails: () -> Unit) {
    val activity = LocalActivity.current
    val launcher = rememberFullScreenAdLauncher()
    val balance by coins.coins.collectAsStateWithLifecycle()
    var showBonusIntro by rememberSaveable { mutableStateOf(false) }
    // Each tap on "Refresh" changes the key: a new banner loads in the background, the shown one stays until then.
    var bannerRefresh by rememberSaveable { mutableIntStateOf(0) }

    // On every resume of this destination: load Home's full-screen ads (and Details' ads, ahead).
    LifecycleResumeEffect(coins) {
        coins.loadAds()
        onPauseOrDispose {}
    }
    // "Level complete" ends on Details, whether an ad showed or not: once Home is back in front after the ad.
    val currentOnOpenDetails by rememberUpdatedState(onOpenDetails)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(coins, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            coins.openDetails.collect { currentOnOpenDetails() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(stringResource(R.string.coins, balance), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { activity?.let(coins::onLevelComplete) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.level_complete)) }
            Button(
                onClick = { launcher.showRewarded(SampleAds.ExtraLifeRewarded.rewardedPlacement(), coins::onRewardResult) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.extra_life)) }
            Button(onClick = { showBonusIntro = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.bonus_round))
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { bannerRefresh++ }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.refresh_banner))
            }
        }
        // Collapsed without an ad. Size or template come from HomeBanner's style.
        AdSlot(SampleAds.HomeBanner, refreshKey = bannerRefresh)
    }

    if (showBonusIntro) {
        BonusIntroDialog(
            onAccept = {
                launcher.showRewardedInterstitial(
                    SampleAds.BonusRewardedInterstitial.rewardedInterstitialPlacement(),
                    coins::onRewardResult,
                )
            },
            onDismiss = { showBonusIntro = false },
        )
    }
}
