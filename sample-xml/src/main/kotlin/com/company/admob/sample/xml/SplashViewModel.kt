package com.company.admob.sample.xml

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.company.admob.sample.xml.ads.SampleAds
import com.company.admob.splash.SplashFlow

/** Keeps the splash sequence across rotations: consent, the timer and the splash ad run once per launch. */
class SplashViewModel(application: Application) : AndroidViewModel(application) {
    val flow: SplashFlow = SplashFlow.create(application, SampleAds.SplashAd, PremiumStore.isPremium(application))

    override fun onCleared() {
        flow.cancel()
    }
}
