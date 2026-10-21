package io.github.thesobiaakbar.adroit.docs.guides.ads.compose

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thesobiaakbar.adroit.AdSlots
import io.github.thesobiaakbar.adroit.Adroit
import io.github.thesobiaakbar.adroit.RewardResult
import io.github.thesobiaakbar.adroit.compose.AdSlot
import io.github.thesobiaakbar.adroit.compose.rememberFullScreenAdLauncher
import io.github.thesobiaakbar.adroit.config.rewardedPlacement
import io.github.thesobiaakbar.adroit.docs.guides.AppAds
import io.github.thesobiaakbar.adroit.docs.guides.UpdateSnackbar

// The main screen of docs/guides/ADS_COMPOSE.md and ADS_IAP_COMPOSE.md, step 7.

// region guide-ads-main-compose
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val snackbars = remember { SnackbarHostState() }
                UpdateSnackbar(snackbars) // a flexible update downloaded in the background: offer "Restart"
                Scaffold(snackbarHost = { SnackbarHost(snackbars) }) { padding ->
                    HomeScreen(Modifier.padding(padding))
                }
            }
        }
        // The app open ad shows by itself when the user comes back to the app. Load it once.
        AdSlots.load(AppAds.AppOpenAd)
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val launcher = rememberFullScreenAdLauncher()
    val privacyRequired by Adroit.privacy.isPrivacyOptionsRequired.collectAsStateWithLifecycle()

    // Ads load only when asked: each time this screen comes to the front, its full-screen ads (ready when a button is
    // tapped) and the next screen's banner (shown the moment it opens). Nothing happens if one is already loaded.
    LifecycleResumeEffect(Unit) {
        AdSlots.load(AppAds.LevelCompleteAd)
        AdSlots.load(AppAds.ExtraLifeRewarded)
        AdSlots.load(AppAds.DetailsBanner)
        onPauseOrDispose {}
    }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            // Interstitial: the callback runs whether an ad showed or not. Always continue the app's flow in it.
            Button(
                onClick = { if (activity != null) AdSlots.showFullScreen(activity, AppAds.LevelCompleteAd) { openNextLevel() } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Level complete") }

            // Rewarded: give the reward only for Earned (in a ViewModel in a real app, so a rotation can't lose it).
            Button(
                onClick = {
                    launcher.showRewarded(AppAds.ExtraLifeRewarded.rewardedPlacement()) { result ->
                        when (result) {
                            is RewardResult.Earned -> giveLives(result.reward.amount)
                            RewardResult.NotEarned -> Toast.makeText(context, "Watch the whole ad", Toast.LENGTH_LONG).show()
                            is RewardResult.Failed -> Toast.makeText(context, "No ad right now", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Extra life (watch an ad)") }

            // Privacy options: Google requires a way to change consent, where consent applies.
            if (privacyRequired && activity != null) {
                Button(onClick = { Adroit.privacy.showPrivacyOptions(activity) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Privacy options")
                }
            }

            // A native ad inside the page: its template comes from HomeNative's style.
            AdSlot(AppAds.HomeNative, Modifier.fillMaxWidth().padding(top = 16.dp))
        }
        // The banner, pinned to the bottom. A banner or a native ad, whichever Firebase says.
        AdSlot(AppAds.HomeBanner)
    }
}

private fun openNextLevel() = Unit // your app's next screen

private fun giveLives(amount: Int) = Unit // your app's reward
// endregion
