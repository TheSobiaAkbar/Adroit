@file:Suppress("unused")

package io.github.thesobiaakbar.adroit.docs

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.compose.runtime.Composable
import io.github.thesobiaakbar.adroit.AdSlots
import io.github.thesobiaakbar.adroit.BannerSize
import io.github.thesobiaakbar.adroit.CollapsibleBannerPosition
import io.github.thesobiaakbar.adroit.Adroit
import io.github.thesobiaakbar.adroit.FullScreenResult
import io.github.thesobiaakbar.adroit.NativeStyle
import io.github.thesobiaakbar.adroit.compose.AdBanner
import io.github.thesobiaakbar.adroit.compose.AdNative
import io.github.thesobiaakbar.adroit.compose.AdSlot
import io.github.thesobiaakbar.adroit.config.AdDefinition
import io.github.thesobiaakbar.adroit.config.AdEnvironment
import io.github.thesobiaakbar.adroit.config.AdProfile
import io.github.thesobiaakbar.adroit.config.AdSettings
import io.github.thesobiaakbar.adroit.config.adMobConfig
import io.github.thesobiaakbar.adroit.config.bannerPlacement
import io.github.thesobiaakbar.adroit.config.interstitialPlacement
import io.github.thesobiaakbar.adroit.config.nativePlacement
import io.github.thesobiaakbar.adroit.firebase.FirebaseRemoteConfigProvider
import io.github.thesobiaakbar.adroit.views.AdSlotView

// region profiles-enum
enum class AppAds(override val defaults: AdProfile) : AdDefinition {
    SplashAd(AdProfile.interstitial(ids = listOf("ca-app-pub-0000000000000000/0000000001"), splash = true)),
    HistoryAd(
        AdProfile.interstitial(
            ids = listOf("ca-app-pub-0000000000000000/0000000002", "ca-app-pub-0000000000000000/0000000003"),
            counter = 3,
            maxPerSession = 5,
            loadAgain = true, // the next ad unit loads right after each show
        ),
    ),
    HomeBanner(
        AdProfile.banner(
            ids = listOf("ca-app-pub-0000000000000000/0000000004"),
            style = BannerSize.Collapsible(CollapsibleBannerPosition.BOTTOM),
        ),
    ),
    FeedNative(AdProfile.native(ids = listOf("ca-app-pub-0000000000000000/0000000005"), style = NativeStyle.LARGE_COMPACT)),
}
// endregion

// region profiles-setup
class AdsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Adroit.initialize(
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
        Adroit.start(this) {
            // Waits up to SplashAd's splashTimeoutSeconds, then shows it (as an interstitial, or an app open ad if
            // Firebase swapped it) or gives up. Continue either way.
            AdSlots.showOnSplash(this, AppAds.SplashAd) { goToMain() }
        }
        // endregion
    }

    fun openHistorySnippet() {
        // region profiles-counter
        // With "counter": 3 in HistoryAd, this shows an ad on the 3rd, 6th, 9th… call. Other calls return at once
        // with NotShown(CounterNotReached), and the app just continues.
        AdSlots.showFullScreen(this, AppAds.HistoryAd) { result: FullScreenResult -> openHistory() }
        // endregion
    }

    private fun openHistory() = Unit

    // region profiles-load
    override fun onResume() {
        super.onResume()
        // Ads load only when asked: load HistoryAd now, so it's ready when the user opens the history. After a failed
        // load, the next resume asks again. Nothing happens if it's loaded or loading already.
        AdSlots.load(AppAds.HistoryAd)
    }
    // endregion
}

// region profiles-ui
@Composable
fun HomeScreenAds() {
    AdSlot(AppAds.HomeBanner) // a banner sized by HomeBanner's "style", or a native ad if Firebase swaps it
    AdSlot(AppAds.FeedNative) // a native ad in FeedNative's template, or a banner if Firebase swaps it
}
// endregion

// region profiles-preload-inline
fun preloadNextScreenAds() {
    // The next screen's banner and native ad, loaded now. Its AdSlotView (or any banner or native view) shows each at
    // once, or when it arrives if it's still loading; without this, each loads when its view appears.
    AdSlots.load(AppAds.HomeBanner)
    AdSlots.load(AppAds.FeedNative)
}
// endregion

// region profiles-refresh-views
fun refreshHomeAd(homeSlot: AdSlotView) {
    // Never a second request while the slot has an ad or one is loading. With refresh, the shown ad stays until a
    // new one has loaded and replaces it; if the new one fails, the shown one stays.
    homeSlot.load(refresh = true)
}
// endregion

// region profiles-refresh-compose
@Composable
fun RefreshableHomeAd(refreshCount: Int) {
    // Each new refreshCount asks for a new ad, replaced the same way; recomposition alone never requests one.
    AdSlot(AppAds.HomeBanner, refreshKey = refreshCount)
}
// endregion

// region profiles-typed
@Composable
fun TypedAds() {
    // The format-specific APIs still work, but don't follow a swap: this is always a banner.
    AdBanner(Ads.HomeBanner)
    AdNative(Ads.FeedNative)
}
// endregion
