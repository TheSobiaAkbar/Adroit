package com.company.admob.sample.mixed

import android.app.Application
import android.os.StrictMode
import com.company.admob.CompanyAdMob
import com.company.admob.sample.mixed.ads.MixedAdsConfig

class MixedApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        enableStrictMode()
        // The only initialize() call: the XML and Compose screens share this one CompanyAdMob instance and state.
        CompanyAdMob.initialize(this, MixedAdsConfig.create(BuildConfig.DEBUG))
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
}
