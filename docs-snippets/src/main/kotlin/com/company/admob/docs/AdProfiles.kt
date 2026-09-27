@file:Suppress("unused")

package com.company.admob.docs

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.compose.runtime.Composable
import com.company.admob.BannerPlacement
import com.company.admob.CompanyAdMob
import com.company.admob.FullScreenResult
import com.company.admob.InterstitialPlacement
import com.company.admob.NativePlacement
import com.company.admob.compose.AdBanner
import com.company.admob.compose.AdNative
import com.company.admob.config.AdEnvironment
import com.company.admob.config.adMobConfig
import com.company.admob.firebase.FirebaseRemoteConfigProvider

// region profiles-setup
class AdsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CompanyAdMob.initialize(
            this,
            adMobConfig {
                // Debug builds show only Google's test ads, whatever IDs the file lists.
                environment = if (BuildConfig.DEBUG) AdEnvironment.DEVELOPMENT else AdEnvironment.PRODUCTION
                profiles(this@AdsApp, R.raw.ads_config) // every ad and its default values
                remoteConfig = FirebaseRemoteConfigProvider() // Firebase values override the file, ad by ad
            },
        )
    }
}
// endregion

// region profiles-placements
object Ads {
    val Splash = InterstitialPlacement("SplashAd")
    val History = InterstitialPlacement("HistoryAd")
    val HomeBanner = BannerPlacement("HomeBanner")
    val FeedNative = NativePlacement("FeedNative")
}
// endregion

class ProfilesSplashActivity : Activity() {
    private fun goToMain() = startActivity(Intent(this, Activity::class.java))

    fun onCreateSnippet() {
        // region profiles-splash
        CompanyAdMob.start(this) {
            // Waits up to SplashAd's splashTimeoutSeconds, then shows it or gives up. Continue either way.
            CompanyAdMob.interstitial.showOnSplash(this, Ads.Splash) { goToMain() }
        }
        // endregion
    }

    fun openHistorySnippet() {
        // region profiles-counter
        // With "counter": 3 in HistoryAd, this shows an ad on the 3rd, 6th, 9th… call. Other calls return at once
        // with NotShown(CounterNotReached), and the app just continues.
        CompanyAdMob.interstitial.show(this, Ads.History) { result: FullScreenResult -> openHistory() }
        // endregion
    }

    private fun openHistory() = Unit
}

// region profiles-ui
@Composable
fun HomeScreenAds() {
    AdBanner(Ads.HomeBanner) // size from HomeBanner's "style"
    AdNative(Ads.FeedNative) // template from FeedNative's "style"
}
// endregion
