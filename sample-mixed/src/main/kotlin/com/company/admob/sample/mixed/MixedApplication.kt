package com.company.admob.sample.mixed

import android.app.Application
import com.company.admob.CompanyAdMob
import com.company.admob.sample.mixed.ads.MixedAdsConfig

class MixedApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // The only initialize() call: the XML and Compose screens share this one CompanyAdMob instance and state.
        CompanyAdMob.initialize(this, MixedAdsConfig.create(BuildConfig.DEBUG))
    }
}
