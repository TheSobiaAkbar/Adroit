package com.company.admob.sample.compose.ads

import android.content.Context
import androidx.core.content.edit
import com.company.admob.config.AdEnvironment
import com.company.admob.config.AdMobConfig
import com.company.admob.config.DebugGeography
import com.company.admob.config.PreloadStrategy
import com.company.admob.config.adMobConfig
import com.company.admob.config.remote.AdConfigProvider
import com.company.admob.logging.LogLevel
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The configuration profiles the playground can run with. The library's configuration is fixed for the life of the
 * process (`initialize` keeps the first one), so a new profile applies at the next app start.
 */
enum class ConfigProfile(val label: String, val description: String) {
    DEVELOPMENT(
        "Development",
        "DEVELOPMENT: Google's demo ad units, verbose logs, consent debug geography EEA (test devices only).",
    ),
    STAGING(
        "Staging with test devices",
        "STAGING: demo ad units, plus the test devices from local.properties (companyAdmob.sample.testDeviceIds).",
    ),
}

/** Remembers the chosen [ConfigProfile] in SharedPreferences, and counts app launches for the App open screen. */
class PlaygroundPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("playground", Context.MODE_PRIVATE)

    /** The profile for this process. Release builds always use STAGING. */
    fun profile(debuggable: Boolean): ConfigProfile {
        if (!debuggable) return ConfigProfile.STAGING
        val stored = prefs.getString(KEY_PROFILE, null)
        return ConfigProfile.entries.firstOrNull { it.name == stored } ?: ConfigProfile.DEVELOPMENT
    }

    /** Stores [profile] for the next start. Written synchronously, because the app restarts right after. */
    fun storeProfile(profile: ConfigProfile) {
        prefs.edit(commit = true) { putString(KEY_PROFILE, profile.name) }
    }

    /** Counts this launch and returns the new total. Called once per process. */
    fun countLaunch(): Int {
        val launches = prefs.getInt(KEY_LAUNCHES, 0) + 1
        prefs.edit { putInt(KEY_LAUNCHES, launches) }
        return launches
    }

    private companion object {
        const val KEY_PROFILE = "config_profile"
        const val KEY_LAUNCHES = "launches"
    }
}

/**
 * The playground's CompanyAdMob configuration.
 *
 * It never contains real ad unit or app IDs: the IDs below are well-formed placeholders, and both profiles set
 * `useDemoAdUnits = true`, which replaces each with Google's demo ad unit for its format. A real app uses PRODUCTION
 * in release builds, with its real ad unit IDs and without `useDemoAdUnits`.
 */
object PlaygroundAdsConfig {
    /** Placeholder for real ad unit IDs (`ca-app-pub-<16 digits>/<10 digits>`). Replaced by demo IDs. */
    private const val PLACEHOLDER_AD_UNIT = "ca-app-pub-0000000000000000/0000000000"

    /** Launches before the first automatic app open ad. */
    const val APP_OPEN_MIN_LAUNCHES = 1

    fun create(
        profile: ConfigProfile,
        testDeviceIds: Array<String>,
        remoteConfig: AdConfigProvider,
    ): AdMobConfig = adMobConfig {
        environment = when (profile) {
            ConfigProfile.DEVELOPMENT -> AdEnvironment.DEVELOPMENT
            ConfigProfile.STAGING -> AdEnvironment.STAGING
        }
        testing {
            useDemoAdUnits = true
            if (profile == ConfigProfile.STAGING) this.testDeviceIds += testDeviceIds
        }
        privacy {
            // Shows the consent form as if in the EEA. UMP applies it only on test devices.
            if (profile == ConfigProfile.DEVELOPMENT) debugGeography = DebugGeography.EEA
        }
        frequency { minIntervalBetweenFullScreenAds = 30.seconds }
        this.remoteConfig = remoteConfig
        placements {
            banner(Placements.AnchoredBanner, PLACEHOLDER_AD_UNIT)
            banner(Placements.InlineBanner, PLACEHOLDER_AD_UNIT)
            banner(Placements.RectangleBanner, PLACEHOLDER_AD_UNIT)
            interstitial(Placements.Interstitial, PLACEHOLDER_AD_UNIT) {
                // Short enough to try, long enough to see FrequencyCapped.retryAfter on the Interstitial screen.
                frequency { minInterval = 45.seconds }
            }
            rewarded(Placements.Rewarded, PLACEHOLDER_AD_UNIT)
            rewardedInterstitial(Placements.RewardedInterstitial, PLACEHOLDER_AD_UNIT)
            appOpen(Placements.AppOpen, PLACEHOLDER_AD_UNIT) {
                cooldown = 1.minutes
                minLaunchesBeforeFirstShow = APP_OPEN_MIN_LAUNCHES
            }
            native(Placements.TemplatesNative, PLACEHOLDER_AD_UNIT) { poolSize = 1 }
            native(Placements.FeedNative, PLACEHOLDER_AD_UNIT) {
                poolSize = 2
                preload = PreloadStrategy.ON_DEMAND
            }
            native(Placements.CustomNative, PLACEHOLDER_AD_UNIT) { preload = PreloadStrategy.ON_DEMAND }
        }
        logging { level = if (profile == ConfigProfile.DEVELOPMENT) LogLevel.DEBUG else LogLevel.INFO }
    }
}
