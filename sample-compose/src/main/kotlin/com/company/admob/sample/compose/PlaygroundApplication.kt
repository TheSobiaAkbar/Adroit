package com.company.admob.sample.compose

import android.app.Application
import android.content.Context
import android.os.StrictMode
import com.company.admob.CompanyAdMob

class PlaygroundApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        enableStrictMode()
        container = AppContainer(this)
        // Cheap and synchronous: no network, no Google calls. Consent and SDK init happen in MainActivity (start).
        CompanyAdMob.initialize(this, container.config)
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

/** The [AppContainer] of this app. */
val Context.appContainer: AppContainer
    get() = (applicationContext as PlaygroundApplication).container
