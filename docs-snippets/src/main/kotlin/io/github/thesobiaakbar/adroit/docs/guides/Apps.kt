package io.github.thesobiaakbar.adroit.docs.guides

import android.app.Application
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import io.github.thesobiaakbar.adroit.Adroit
import io.github.thesobiaakbar.adroit.billing.AdroitBilling
import io.github.thesobiaakbar.adroit.billing.BillingSettings
import io.github.thesobiaakbar.adroit.billing.DemoBilling
import io.github.thesobiaakbar.adroit.billing.firebase.FirebaseBillingConfig
import io.github.thesobiaakbar.adroit.billing.firebase.FirebaseBillingEvents
import io.github.thesobiaakbar.adroit.config.AdEnvironment
import io.github.thesobiaakbar.adroit.config.adMobConfig
import io.github.thesobiaakbar.adroit.docs.BuildConfig
import io.github.thesobiaakbar.adroit.docs.billing.AppPaywalls
import io.github.thesobiaakbar.adroit.docs.billing.AppProducts
import io.github.thesobiaakbar.adroit.firebase.FirebaseRemoteConfigProvider
import io.github.thesobiaakbar.adroit.logging.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import io.github.thesobiaakbar.adroit.docs.guides.iap.AppPaywalls as IapPaywalls
import io.github.thesobiaakbar.adroit.docs.guides.iap.AppProducts as IapProducts

// The Application classes of docs/guides/*.md, step 5. Each guide shows its own as `class MyApp`.

// region guide-ads-app
class AdsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Firebase Remote Config: debug builds see a console change on the next launch. Adroit fetches it once per
        // launch, in the background: nothing waits for it.
        FirebaseRemoteConfig.getInstance().setConfigSettingsAsync(
            remoteConfigSettings {
                if (BuildConfig.DEBUG) minimumFetchIntervalInSeconds = 0
                fetchTimeoutInSeconds = 10
            },
        )
        // The ads: cheap and synchronous, no network. Consent and the SDK start on the splash.
        Adroit.initialize(
            this,
            adMobConfig {
                // Debug builds show only Google's test ads, and ask for consent as in Europe.
                environment = if (BuildConfig.DEBUG) AdEnvironment.DEVELOPMENT else AdEnvironment.PRODUCTION
                profiles(AppAds.entries, AppAds.settings) // every ad and its defaults
                remoteConfig = FirebaseRemoteConfigProvider() // Firebase values override the defaults, ad by ad
                logging { level = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN }
            },
        )
    }
}
// endregion

// region guide-iap-app
class IapApp : Application() {
    // For app-wide work, such as crediting consumables (step 7). Remove it if you sell none.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // Firebase Remote Config: debug builds see a console change on the next launch. Adroit fetches it once per
        // launch, in the background: nothing waits for it.
        FirebaseRemoteConfig.getInstance().setConfigSettingsAsync(
            remoteConfigSettings {
                if (BuildConfig.DEBUG) minimumFetchIntervalInSeconds = 0
                fetchTimeoutInSeconds = 10
            },
        )
        // Billing: reads the saved purchases at once (a subscriber is a subscriber from the first screen) and asks
        // Google Play in the background.
        AdroitBilling.initialize(
            this,
            BillingSettings(
                products = IapProducts.entries,
                paywalls = IapPaywalls.entries,
                config = FirebaseBillingConfig.source(), // the paywalls' Firebase JSON
                events = listOf(FirebaseBillingEvents(this)), // purchases and paywalls in Analytics
                demo = DemoBilling(BuildConfig.DEBUG), // made-up products until Play Console has yours
            ),
        )
        // Consumables (step 7): Coins.start(this, appScope)
    }
}
// endregion

// region guide-ads-iap-app
class AdsIapApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // Firebase Remote Config: debug builds see a console change on the next launch. Adroit fetches it once per
        // launch, for ads and billing together, in the background: nothing waits for it.
        FirebaseRemoteConfig.getInstance().setConfigSettingsAsync(
            remoteConfigSettings {
                if (BuildConfig.DEBUG) minimumFetchIntervalInSeconds = 0
                fetchTimeoutInSeconds = 10
            },
        )
        // The ads: cheap and synchronous, no network. Consent and the SDK start on the splash.
        Adroit.initialize(
            this,
            adMobConfig {
                environment = if (BuildConfig.DEBUG) AdEnvironment.DEVELOPMENT else AdEnvironment.PRODUCTION
                profiles(AppAds.entries, AppAds.settings)
                remoteConfig = FirebaseRemoteConfigProvider()
                logging { level = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN }
            },
        )
        // Billing: reads the saved purchases at once and asks Google Play in the background.
        AdroitBilling.initialize(
            this,
            BillingSettings(
                products = AppProducts.entries,
                paywalls = AppPaywalls.entries,
                config = FirebaseBillingConfig.source(),
                events = listOf(FirebaseBillingEvents(this)),
                demo = DemoBilling(BuildConfig.DEBUG),
            ),
        )
        // The one line between billing and ads: premium means no ads, from the next ad request on. The saved
        // purchases are already read, so a premium user is ad-free before the splash asks for its ad.
        appScope.launch { AdroitBilling.entitlements.collect { Adroit.setAdFree(it.adFree) } }
    }
}
// endregion
