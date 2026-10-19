package io.github.thesobiaakbar.adroit.sample.compose

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.github.thesobiaakbar.adroit.billing.AdroitBilling
import io.github.thesobiaakbar.adroit.sample.compose.ads.SampleAds
import io.github.thesobiaakbar.adroit.splash.SplashFlow

/**
 * Keeps the splash sequence across rotations: consent, the timer and the splash ad run once per launch. A premium
 * user (the entitlements saved by the last launch, read synchronously) skips consent and the ad.
 */
class SplashViewModel(application: Application) : AndroidViewModel(application) {
    val flow: SplashFlow = SplashFlow.create(application, SampleAds.SplashAd, AdroitBilling.entitlements.value.adFree)

    override fun onCleared() {
        flow.cancel()
    }
}
