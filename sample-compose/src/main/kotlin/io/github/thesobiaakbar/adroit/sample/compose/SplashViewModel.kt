package io.github.thesobiaakbar.adroit.sample.compose

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.github.thesobiaakbar.adroit.sample.compose.ads.SampleAds
import io.github.thesobiaakbar.adroit.splash.SplashFlow

/** Keeps the splash sequence across rotations: consent, the timer and the splash ad run once per launch. */
class SplashViewModel(application: Application) : AndroidViewModel(application) {
    val flow: SplashFlow = SplashFlow.create(application, SampleAds.SplashAd, PremiumStore.isPremium(application))

    override fun onCleared() {
        flow.cancel()
    }
}
