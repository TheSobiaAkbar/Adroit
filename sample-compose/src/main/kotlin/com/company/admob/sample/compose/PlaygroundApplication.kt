package com.company.admob.sample.compose

import android.app.Application
import android.content.Context
import com.company.admob.CompanyAdMob

class PlaygroundApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Cheap and synchronous: no network, no Google calls. Consent and SDK init happen in MainActivity (start).
        CompanyAdMob.initialize(this, container.config)
    }
}

/** The [AppContainer] of this app. */
val Context.appContainer: AppContainer
    get() = (applicationContext as PlaygroundApplication).container
