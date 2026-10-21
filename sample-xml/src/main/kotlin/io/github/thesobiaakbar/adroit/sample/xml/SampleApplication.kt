package io.github.thesobiaakbar.adroit.sample.xml

import android.app.Application
import android.os.StrictMode
import android.os.SystemClock
import android.util.Log
import io.github.thesobiaakbar.adroit.Adroit
import io.github.thesobiaakbar.adroit.billing.AdroitBilling
import io.github.thesobiaakbar.adroit.billing.BillingEventListener
import io.github.thesobiaakbar.adroit.billing.BillingSettings
import io.github.thesobiaakbar.adroit.billing.DemoBilling
import io.github.thesobiaakbar.adroit.billing.firebase.FirebaseBillingConfig
import io.github.thesobiaakbar.adroit.billing.firebase.FirebaseBillingEvents
import io.github.thesobiaakbar.adroit.sample.xml.billing.CoinWallet
import io.github.thesobiaakbar.adroit.sample.xml.billing.DemoMode
import io.github.thesobiaakbar.adroit.sample.xml.billing.SamplePaywalls
import io.github.thesobiaakbar.adroit.sample.xml.billing.SampleProducts
import io.github.thesobiaakbar.adroit.sample.xml.ads.SampleAdsConfig
import io.github.thesobiaakbar.adroit.views.NativeTemplates
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        enableStrictMode()
        configureRemoteConfig()
        // Cheap and synchronous: no network, no Google calls. Consent and SDK init happen in SplashActivity.
        val configStart = SystemClock.elapsedRealtimeNanos()
        val config = SampleAdsConfig.create(this, BuildConfig.BUILD_TYPE, BuildConfig.TEST_DEVICE_IDS)
        // The app's own native layout, which SampleAds or Firebase can pick by name: {"style": "sample_promo"}.
        NativeTemplates.register("sample_promo", R.layout.sample_native_promo)
        val initializeStart = SystemClock.elapsedRealtimeNanos()
        Adroit.initialize(this, config)
        val end = SystemClock.elapsedRealtimeNanos()
        val billingNanos = initializeBilling()
        // The performance audit's measurements (docs/audits/PERFORMANCE_AUDIT.md): building the configuration, which
        // reads and parses the ads file, initialize(), and AdroitBilling.initialize(). Logged in every build type:
        // release numbers are the ones that count, since debuggable builds skip ahead-of-time compilation.
        Log.i(
            PERF_TAG,
            "config took ${(initializeStart - configStart) / 1_000} µs, initialize took ${(end - initializeStart) / 1_000} µs, " +
                "billing initialize took ${billingNanos / 1_000} µs",
        )
    }

    // Billing (docs/firebase/sample_billing_remote_config.json for its Firebase keys). initialize reads the saved
    // entitlements synchronously, so a premium user from an earlier launch is ad-free before any screen asks for an ad.
    // Returns how long AdroitBilling.initialize took, in nanoseconds.
    private fun initializeBilling(): Long {
        val events = listOfNotNull(
            FirebaseBillingEvents(this),
            // Every event in logcat (tag SampleBilling), with its placement: home_banner, settings, onboarding…
            BillingEventListener { Log.i(BILLING_TAG, it.toString()) }.takeIf { BuildConfig.DEBUG },
        )
        val settings = BillingSettings(
            products = SampleProducts.entries,
            paywalls = SamplePaywalls.entries,
            config = FirebaseBillingConfig.source(),
            events = events,
            demo = DemoBilling(DemoMode.isOn(this)),
        )
        val start = SystemClock.elapsedRealtimeNanos()
        AdroitBilling.initialize(this, settings)
        val took = SystemClock.elapsedRealtimeNanos() - start
        // The ads bridge: premium means no ads anywhere, from the next ad request on.
        appScope.launch { AdroitBilling.entitlements.collect { Adroit.setAdFree(it.adFree) } }
        CoinWallet.start(this, appScope)
        return took
    }

    // Remote Config's settings, before the first Adroit Firebase module starts the shared fetch (RemoteConfigSession:
    // once per session, never waiting, retried on each new screen or network return). Debug and staging builds skip
    // Firebase's cache so a console change shows on the next launch; release keeps the default 12 hours.
    private fun configureRemoteConfig() {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings {
                if (BuildConfig.DEBUG) minimumFetchIntervalInSeconds = 0
                fetchTimeoutInSeconds = 10 // a slow attempt must not hold off the next screen's retry for a minute
            },
        )
    }

    // Debug builds only: logs every disk or network access on the main thread (tag StrictMode), so a library
    // regression shows up in logcat. See docs/audits/PERFORMANCE_AUDIT.md.
    private fun enableStrictMode() {
        if (!BuildConfig.DEBUG) return
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectActivityLeaks().penaltyLog().build(),
        )
    }

    private companion object {
        const val PERF_TAG = "AdroitPerf"
        const val BILLING_TAG = "SampleBilling"

        /** The process's scope; Main.immediate, so the ads bridge applies the saved entitlements at once. */
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}
