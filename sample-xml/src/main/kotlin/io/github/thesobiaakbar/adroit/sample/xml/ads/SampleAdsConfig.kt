package io.github.thesobiaakbar.adroit.sample.xml.ads

import android.content.Context
import android.util.Log
import io.github.thesobiaakbar.adroit.config.AdEnvironment
import io.github.thesobiaakbar.adroit.config.AdMobConfig
import io.github.thesobiaakbar.adroit.config.adMobConfig
import io.github.thesobiaakbar.adroit.events.AdEvent
import io.github.thesobiaakbar.adroit.events.AdEventListener
import io.github.thesobiaakbar.adroit.firebase.FirebaseRemoteConfigProvider
import io.github.thesobiaakbar.adroit.logging.LogLevel

/**
 * The sample's Adroit configuration. Every ad is an entry of [SampleAds], with its default settings (see
 * docs/AD_PROFILES.md). Each ad can then be changed from the Firebase Remote Config parameter named after its entry
 * (Firebase project `sample-ads-plugin`, through `adroit-firebase`).
 *
 * This sample never contains real ad unit or app IDs. The entries list no IDs, so each uses Google's test ad unit for
 * its format, and DEVELOPMENT and STAGING with `useDemoAdUnits = true` also replace any ID set from Firebase, so the
 * app shows Google's test ads in every build type.
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
        profiles(SampleAds.entries, SampleAds.settings)
        // Firebase values override the enum's defaults, ad by ad. The shared RemoteConfigSession fetches them.
        remoteConfig = FirebaseRemoteConfigProvider()
        logging { level = if (buildType == "release") LogLevel.WARN else LogLevel.DEBUG }
        // Each ad's life in logcat (filter by AdEvents): requested, loaded, failed, not shown, displayed. A real app
        // sends these to its analytics instead (docs/ANALYTICS.md).
        eventListeners += AdEventListener { event ->
            when (event) {
                is AdEvent.LoadRequested, is AdEvent.Loaded, is AdEvent.LoadFailed, is AdEvent.NotShown,
                is AdEvent.Impression -> Log.d("AdEvents", event.toString())
                else -> Unit
            }
        }
    }
}
