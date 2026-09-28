@file:Suppress("unused")

package com.company.admob.docs

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.compose.runtime.Composable
import com.company.admob.BannerSize
import com.company.admob.CollapsibleBannerPosition
import com.company.admob.CompanyAdMob
import com.company.admob.FullScreenResult
import com.company.admob.NativeStyle
import com.company.admob.compose.AdBanner
import com.company.admob.compose.AdNative
import com.company.admob.config.AdDefinition
import com.company.admob.config.AdEnvironment
import com.company.admob.config.AdProfile
import com.company.admob.config.AdSettings
import com.company.admob.config.adMobConfig
import com.company.admob.config.bannerPlacement
import com.company.admob.config.interstitialPlacement
import com.company.admob.config.nativePlacement
import com.company.admob.firebase.FirebaseRemoteConfigProvider
import com.company.admob.firebase.RemoteConfigSession

// region profiles-enum
enum class AppAds(override val defaults: AdProfile) : AdDefinition {
    SplashAd(AdProfile.interstitial(ids = listOf("ca-app-pub-0000000000000000/0000000001"), splash = true)),
    HistoryAd(
        AdProfile.interstitial(
            ids = listOf("ca-app-pub-0000000000000000/0000000002", "ca-app-pub-0000000000000000/0000000003"),
            counter = 3,
            maxPerSession = 5,
        ),
    ),
    HomeBanner(
        AdProfile.banner(
            ids = listOf("ca-app-pub-0000000000000000/0000000004"),
            style = BannerSize.Collapsible(CollapsibleBannerPosition.BOTTOM),
        ),
    ),
    FeedNative(AdProfile.native(ids = listOf("ca-app-pub-0000000000000000/0000000005"), style = NativeStyle.LARGE)),
}
// endregion

// region profiles-setup
class AdsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RemoteConfigSession.start(this) // fetches Firebase once this session, without making anything wait
        CompanyAdMob.initialize(
            this,
            adMobConfig {
                // Debug builds show only Google's test ads, whatever IDs the definitions list.
                environment = if (BuildConfig.DEBUG) AdEnvironment.DEVELOPMENT else AdEnvironment.PRODUCTION
                profiles(AppAds.entries, AdSettings()) // every ad and its default values
                remoteConfig = FirebaseRemoteConfigProvider() // Firebase values override the defaults, ad by ad
            },
        )
    }
}
// endregion

// region profiles-placements
object Ads {
    val Splash = AppAds.SplashAd.interstitialPlacement()
    val History = AppAds.HistoryAd.interstitialPlacement()
    val HomeBanner = AppAds.HomeBanner.bannerPlacement()
    val FeedNative = AppAds.FeedNative.nativePlacement()
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
