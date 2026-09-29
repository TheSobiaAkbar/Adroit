package com.company.admob.sample.xml

import android.app.Application
import android.os.StrictMode
import android.os.SystemClock
import android.util.Log
import com.company.admob.CompanyAdMob
import com.company.admob.sample.xml.ads.SampleAdsConfig
import com.company.admob.firebase.RemoteConfigSession
import com.company.admob.views.NativeTemplates
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

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
        CompanyAdMob.initialize(this, config)
        // A premium user from an earlier launch: no ads, before any screen asks for one.
        if (PremiumStore.isPremium(this)) CompanyAdMob.setAdFree(true)
        val end = SystemClock.elapsedRealtimeNanos()
        // The performance audit's measurements (docs/audits/PERFORMANCE_AUDIT.md): building the configuration, which
        // reads and parses the ads file, and initialize(). Logged in every build type: release numbers are the ones
        // that count, since debuggable builds skip ahead-of-time compilation.
        Log.i(PERF_TAG, "config took ${(initializeStart - configStart) / 1_000} µs, initialize took ${(end - initializeStart) / 1_000} µs")
    }

    // Fetches Remote Config (every ad's Firebase JSON) once this session, without making anything wait: at once, then
    // again on each new screen or network return until one fetch succeeds. Debug and staging builds skip Firebase's
    // cache so a console change shows on the next launch; release keeps the default 12 hours.
    private fun configureRemoteConfig() {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings {
                if (BuildConfig.DEBUG) minimumFetchIntervalInSeconds = 0
                fetchTimeoutInSeconds = 10 // a slow attempt must not hold off the next screen's retry for a minute
            },
        )
        RemoteConfigSession.start(this, remoteConfig)
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
        const val PERF_TAG = "CompanyAdMobPerf"
    }
}
