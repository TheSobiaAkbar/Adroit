package com.company.admob.sample.xml.ads

import com.company.admob.config.AdEnvironment
import com.company.admob.config.AdMobConfig
import com.company.admob.config.adMobConfig
import com.company.admob.logging.LogLevel
import com.company.admob.sample.xml.SplashActivity
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The sample's CompanyAdMob configuration.
 *
 * This sample never contains real ad unit or app IDs. The IDs below are well-formed placeholders: DEVELOPMENT and
 * STAGING with `useDemoAdUnits = true` replace every one of them with Google's demo ad unit for the format, so the
 * app shows Google's test ads in every build type.
 */
object SampleAdsConfig {
    /** Placeholder for your real ad unit IDs (`ca-app-pub-<16 digits>/<10 digits>`). Replaced by demo IDs here. */
    private const val PLACEHOLDER_AD_UNIT = "ca-app-pub-0000000000000000/0000000000"

    /**
     * @param buildType `BuildConfig.BUILD_TYPE`.
     * @param testDeviceIds `BuildConfig.TEST_DEVICE_IDS` (from `local.properties`, empty by default).
     */
    fun create(buildType: String, testDeviceIds: Array<String>): AdMobConfig = adMobConfig {
        environment = when (buildType) {
            "debug" -> AdEnvironment.DEVELOPMENT
            // "staging" is a tester's build. "release" is STAGING too, only because this sample must never request
            // live ads. A real app uses AdEnvironment.PRODUCTION for release, with its real ad unit IDs below, and
            // without useDemoAdUnits (validation rejects demo IDs there).
            else -> AdEnvironment.STAGING
        }
        testing {
            useDemoAdUnits = true
            // Test devices get test ads even from real ad units. STAGING with real units requires at least one.
            this.testDeviceIds += testDeviceIds
        }
        frequency { minIntervalBetweenFullScreenAds = 30.seconds }
        placements {
            banner(Placements.HomeBanner, PLACEHOLDER_AD_UNIT)
            banner(Placements.DetailsBanner, PLACEHOLDER_AD_UNIT)
            interstitial(Placements.LevelCompleteInterstitial, PLACEHOLDER_AD_UNIT) {
                frequency { minInterval = 60.seconds }
            }
            rewarded(Placements.ExtraLifeRewarded, PLACEHOLDER_AD_UNIT)
            rewardedInterstitial(Placements.BonusRewardedInterstitial, PLACEHOLDER_AD_UNIT)
            appOpen(Placements.AppOpen, PLACEHOLDER_AD_UNIT) {
                cooldown = 4.minutes
                // The splash shows the cold-start ad itself; it must never get an automatic one on top.
                excludeActivities(SplashActivity::class.java)
            }
            native(Placements.FeedNative, PLACEHOLDER_AD_UNIT) { poolSize = 2 }
            native(Placements.ArticleNative, PLACEHOLDER_AD_UNIT)
        }
        logging { level = if (buildType == "release") LogLevel.WARN else LogLevel.DEBUG }
    }
}
