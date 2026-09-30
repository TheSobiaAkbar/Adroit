@file:Suppress("unused")

package io.github.thesobiaakbar.adroit.docs

import io.github.thesobiaakbar.adroit.AppOpenPlacement
import io.github.thesobiaakbar.adroit.BannerPlacement
import io.github.thesobiaakbar.adroit.InterstitialPlacement
import io.github.thesobiaakbar.adroit.NativePlacement
import io.github.thesobiaakbar.adroit.RewardedInterstitialPlacement
import io.github.thesobiaakbar.adroit.RewardedPlacement
import io.github.thesobiaakbar.adroit.config.AdEnvironment
import io.github.thesobiaakbar.adroit.config.AdMobConfig
import io.github.thesobiaakbar.adroit.config.AgeTreatment
import io.github.thesobiaakbar.adroit.config.DebugGeography
import io.github.thesobiaakbar.adroit.config.MaxAdContentRating
import io.github.thesobiaakbar.adroit.config.PreloadStrategy
import io.github.thesobiaakbar.adroit.config.adMobConfig
import io.github.thesobiaakbar.adroit.config.remote.StaticConfigProvider
import io.github.thesobiaakbar.adroit.events.AdEventListener
import io.github.thesobiaakbar.adroit.logging.LogLevel
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private class ConfigSplashActivity : android.app.Activity()

// region full-dsl
fun fullConfig(analytics: AdEventListener): AdMobConfig = adMobConfig {
    environment = AdEnvironment.STAGING
    // Optional: must equal the manifest's com.google.android.gms.ads.APPLICATION_ID when set.
    appId = "ca-app-pub-0000000000000000~0000000000"

    testing {
        testDeviceIds += "33BE2250B43518CCDA7DE426D04EE231" // printed in Logcat by the Google SDK
        useDemoAdUnits = false // STAGING with real ad units: test devices are then required
    }
    privacy {
        ageTreatment = AgeTreatment.UNSPECIFIED
        maxAdContentRating = MaxAdContentRating.T
        restrictedDataProcessing = false
        debugGeography = DebugGeography.EEA // test devices only; must be DISABLED in PRODUCTION
        consentSyncId = null
    }
    frequency {
        minIntervalBetweenFullScreenAds = 30.seconds
        includeRewardedInGlobalInterval = false
    }
    logging { level = LogLevel.DEBUG }
    advanced {
        showTimeout = 5.seconds
        failFast = null // null: throw on invalid config in DEVELOPMENT and STAGING only
        disableSdkCrashReporting = false
    }
    eventListeners += analytics
    remoteConfig = StaticConfigProvider // the default: no remote overrides

    placements {
        banner(BannerPlacement("home_banner"), "ca-app-pub-0000000000000000/0000000001")
        interstitial(InterstitialPlacement("level_complete"), "ca-app-pub-0000000000000000/0000000002") {
            frequency {
                minInterval = 60.seconds
                maxPerSession = 10
            }
            preload = PreloadStrategy.AFTER_READY
        }
        rewarded(RewardedPlacement("extra_life"), "ca-app-pub-0000000000000000/0000000003")
        rewardedInterstitial(
            RewardedInterstitialPlacement("bonus_round"),
            "ca-app-pub-0000000000000000/0000000004",
        )
        appOpen(AppOpenPlacement("app_open"), "ca-app-pub-0000000000000000/0000000005") {
            cooldown = 4.minutes
            minLaunchesBeforeFirstShow = 2
            showOnColdStartAllowed = true
            suppressAfterFullScreenAd = 5.seconds
            excludeActivities(ConfigSplashActivity::class.java)
            expiry = 4.hours
        }
        native(NativePlacement("feed_native"), "ca-app-pub-0000000000000000/0000000006") {
            poolSize = 2
            enabled = true
        }
    }
}
// endregion

private val buildType = "debug"

// region per-build
val environment = when (buildType) {
    "debug" -> AdEnvironment.DEVELOPMENT // Google's demo ad units, whatever is configured
    "staging" -> AdEnvironment.STAGING // demo units by default; real units need test devices
    else -> AdEnvironment.PRODUCTION // real ad units only; demo IDs are rejected
}
// endregion
