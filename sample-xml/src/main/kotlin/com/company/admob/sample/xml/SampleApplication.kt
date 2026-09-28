package com.company.admob.sample.xml

import android.app.Application
import android.os.StrictMode
import android.os.SystemClock
import android.util.Log
import com.company.admob.CompanyAdMob
import com.company.admob.sample.xml.ads.SampleAdsConfig
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
        val initializeStart = SystemClock.elapsedRealtimeNanos()
        CompanyAdMob.initialize(this, config)
        val end = SystemClock.elapsedRealtimeNanos()
        // The performance audit's measurements (docs/audits/PERFORMANCE_AUDIT.md): building the configuration, which
        // reads and parses the ads file, and initialize(). Logged in every build type: release numbers are the ones
        // that count, since debuggable builds skip ahead-of-time compilation.
        Log.i(PERF_TAG, "config took ${(initializeStart - configStart) / 1_000} µs, initialize took ${(end - initializeStart) / 1_000} µs")
    }

    // Debug and staging builds fetch fresh values on every launch, so a change in the Firebase console shows at once.
    // Release keeps Firebase's default 12-hour cache.
    private fun configureRemoteConfig() {
        if (!BuildConfig.DEBUG) return
        FirebaseRemoteConfig.getInstance().setConfigSettingsAsync(
            remoteConfigSettings { minimumFetchIntervalInSeconds = 0 },
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
        const val PERF_TAG = "CompanyAdMobPerf"
    }
}
