package com.company.admob.sample.xml

import android.app.Application
import com.company.admob.CompanyAdMob
import com.company.admob.sample.xml.ads.SampleAdsConfig

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Cheap and synchronous: no network, no Google calls. Consent and SDK init happen in SplashActivity.
        CompanyAdMob.initialize(this, SampleAdsConfig.create(BuildConfig.BUILD_TYPE, BuildConfig.TEST_DEVICE_IDS))
    }
}
