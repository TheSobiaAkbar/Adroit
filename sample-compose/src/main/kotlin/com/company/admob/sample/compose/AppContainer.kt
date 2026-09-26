package com.company.admob.sample.compose

import android.app.Application
import com.company.admob.AdsClient
import com.company.admob.CompanyAdMob
import com.company.admob.config.AdMobConfig
import com.company.admob.config.remote.AdConfigProvider
import com.company.admob.config.remote.AdOverrides
import com.company.admob.sample.compose.ads.ConfigProfile
import com.company.admob.sample.compose.ads.PlaygroundAdsConfig
import com.company.admob.sample.compose.ads.PlaygroundPreferences
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A tiny manual dependency container: screens and ViewModels receive [ads] as an [AdsClient] instead of reaching for
 * the `CompanyAdMob` object, which keeps them testable with admob-testing's `FakeAdsClient`.
 *
 * With Hilt, the same binding is a provider in a `SingletonComponent` module, and `CompanyAdMob.initialize` stays in
 * `Application.onCreate`:
 * ```
 * @Module @InstallIn(SingletonComponent::class)
 * object AdsModule {
 *     @Provides fun adsClient(): AdsClient = CompanyAdMob.client
 * }
 * ```
 */
class AppContainer(application: Application) {
    val preferences = PlaygroundPreferences(application)

    /** The profile this process runs with. Changing it takes effect at the next start. */
    val profile: ConfigProfile = preferences.profile(debuggable = BuildConfig.DEBUG)

    /** This launch's number, counted by the playground (the library's own launch count isn't public). */
    val launchNumber: Int = preferences.countLaunch()

    /**
     * A local stand-in for a remote config service such as Firebase Remote Config: the Configuration screen edits
     * it, and the library applies each new value live.
     */
    val remoteOverrides = MutableStateFlow(AdOverrides())

    val config: AdMobConfig = PlaygroundAdsConfig.create(
        profile = profile,
        testDeviceIds = BuildConfig.TEST_DEVICE_IDS,
        remoteConfig = AdConfigProvider { remoteOverrides },
    )

    val ads: AdsClient = CompanyAdMob.client

    /** Collected from the start of the process, before `start()`, so no state change is missed. */
    val eventLog = EventLog(MainScope(), ads.events)
}
