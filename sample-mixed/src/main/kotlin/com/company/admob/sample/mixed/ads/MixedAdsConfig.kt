package com.company.admob.sample.mixed.ads

import com.company.admob.config.AdEnvironment
import com.company.admob.config.AdMobConfig
import com.company.admob.config.adMobConfig
import com.company.admob.logging.LogLevel

/**
 * The mixed sample's configuration: the same one for its XML and Compose screens, which share one `CompanyAdMob`.
 *
 * It never contains real ad unit or app IDs. The IDs are placeholders replaced by Google's demo ad units, also in
 * the mediation build: demo units only serve Google ads, so the AppLovin adapter is present but never called here.
 * Testing mediation needs STAGING, real ad units, registered test devices and each network's test mode (see
 * MEDIATION_DEMO.md). A real app uses PRODUCTION in release, with its real IDs and without `useDemoAdUnits`.
 */
object MixedAdsConfig {
    private const val PLACEHOLDER_AD_UNIT = "ca-app-pub-0000000000000000/0000000000"

    fun create(debuggable: Boolean): AdMobConfig = adMobConfig {
        environment = if (debuggable) AdEnvironment.DEVELOPMENT else AdEnvironment.STAGING
        testing { useDemoAdUnits = true }
        placements {
            banner(Placements.LegacyBanner, PLACEHOLDER_AD_UNIT)
            native(Placements.ComposeInXmlNative, PLACEHOLDER_AD_UNIT)
            native(Placements.XmlInComposeNative, PLACEHOLDER_AD_UNIT)
            interstitial(Placements.Interstitial, PLACEHOLDER_AD_UNIT)
        }
        logging { level = if (debuggable) LogLevel.DEBUG else LogLevel.WARN }
    }
}
