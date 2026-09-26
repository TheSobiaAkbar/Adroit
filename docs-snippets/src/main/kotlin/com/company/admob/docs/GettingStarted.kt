@file:Suppress("unused")

package com.company.admob.docs

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import com.company.admob.AdsClient
import com.company.admob.BannerPlacement
import com.company.admob.CompanyAdMob
import com.company.admob.InterstitialPlacement
import com.company.admob.NativePlacement
import com.company.admob.RewardedPlacement
import com.company.admob.config.AdEnvironment
import com.company.admob.config.adMobConfig
import kotlin.time.Duration.Companion.seconds

// region placements
/** Every place the app shows an ad. Keys are stable identifiers: remote overrides and analytics use them. */
object Placements {
    val HomeBanner = BannerPlacement("home_banner")
    val LevelComplete = InterstitialPlacement("level_complete")
    val ExtraLife = RewardedPlacement("extra_life")
    val FeedNative = NativePlacement("feed_native")
}
// endregion

private val isDebugBuild = true

// region initialize
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Cheap and synchronous: validates the configuration, no network and no Google SDK call.
        CompanyAdMob.initialize(
            this,
            adMobConfig {
                environment = if (isDebugBuild) AdEnvironment.DEVELOPMENT else AdEnvironment.PRODUCTION
                placements {
                    banner(Placements.HomeBanner, "ca-app-pub-0000000000000000/0000000001")
                    interstitial(Placements.LevelComplete, "ca-app-pub-0000000000000000/0000000002")
                    rewarded(Placements.ExtraLife, "ca-app-pub-0000000000000000/0000000003")
                    native(Placements.FeedNative, "ca-app-pub-0000000000000000/0000000004")
                }
                frequency { minIntervalBetweenFullScreenAds = 30.seconds }
            },
        )
    }
}
// endregion

// region start
class LauncherActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Gathers consent (UMP), then initializes the Google Mobile Ads SDK. Safe to call again after a rotation:
        // it joins the work already in progress.
        CompanyAdMob.start(this) { state ->
            // Ready, or Unavailable/Failed: continue into the app either way.
        }
    }
}
// endregion

// region first-interstitial
fun onLevelComplete(activity: Activity, goToNextLevel: () -> Unit) {
    CompanyAdMob.showInterstitial(activity, Placements.LevelComplete) {
        // Called exactly once, whether an ad was shown or not: always continue the flow here.
        goToNextLevel()
    }
}
// endregion

// region without-hilt
/** A ViewModel that receives the client instead of reaching for the CompanyAdMob object: easy to fake in tests. */
class LevelViewModel(private val ads: AdsClient = CompanyAdMob.client) : ViewModel() {
    fun onLevelComplete(activity: Activity) {
        ads.showInterstitial(activity, Placements.LevelComplete) { /* continue */ }
    }
}
// endregion
