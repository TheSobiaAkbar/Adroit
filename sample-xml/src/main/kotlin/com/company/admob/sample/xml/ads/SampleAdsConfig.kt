package com.company.admob.sample.xml.ads

import android.content.Context
import com.company.admob.config.AdEnvironment
import com.company.admob.config.AdMobConfig
import com.company.admob.config.adMobConfig
import com.company.admob.logging.LogLevel
import com.company.admob.sample.xml.R

/**
 * The sample's CompanyAdMob configuration. Every ad is defined in one file, `res/raw/ads_config.json` (an ad profile
 * per placement key; see docs/AD_PROFILES.md). With the `admob-firebase` module, each ad can then be changed from a
 * Firebase Remote Config parameter of the same name; this sample doesn't use Firebase.
 *
 * This sample never contains real ad unit or app IDs. The IDs in the file are well-formed placeholders: DEVELOPMENT
 * and STAGING with `useDemoAdUnits = true` replace every one of them with Google's demo ad unit for the format, so
 * the app shows Google's test ads in every build type.
 */
object SampleAdsConfig {
    /**
     * @param buildType `BuildConfig.BUILD_TYPE`.
     * @param testDeviceIds `BuildConfig.TEST_DEVICE_IDS` (from `local.properties`, empty by default).
     */
    fun create(context: Context, buildType: String, testDeviceIds: Array<String>): AdMobConfig = adMobConfig {
        environment = when (buildType) {
            "debug" -> AdEnvironment.DEVELOPMENT
            // "staging" is a tester's build. "release" is STAGING too, only because this sample must never request
            // live ads. A real app uses AdEnvironment.PRODUCTION for release, with its real ad unit IDs in the ads
            // file, and without useDemoAdUnits (validation rejects demo IDs there).
            else -> AdEnvironment.STAGING
        }
        testing {
            useDemoAdUnits = true
            // Test devices get test ads even from real ad units. STAGING with real units requires at least one.
            this.testDeviceIds += testDeviceIds
        }
        profiles(context, R.raw.ads_config)
        logging { level = if (buildType == "release") LogLevel.WARN else LogLevel.DEBUG }
    }
}
