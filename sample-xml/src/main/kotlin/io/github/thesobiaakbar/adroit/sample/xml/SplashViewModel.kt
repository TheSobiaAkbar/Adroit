package io.github.thesobiaakbar.adroit.sample.xml

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.github.thesobiaakbar.adroit.sample.xml.ads.SampleAds
import io.github.thesobiaakbar.adroit.splash.SplashFlow

/**
 * Keeps the splash sequence across rotations: consent, the timer and the splash ad run once per launch. A premium
 * user skips consent and the ad: SplashFlow reads `Adroit.isAdFree`, which SampleApplication's billing bridge has
 * already set from the entitlements saved by the last launch.
 */
class SplashViewModel(application: Application) : AndroidViewModel(application) {
    val flow: SplashFlow = SplashFlow.create(application, SampleAds.SplashAd)

    override fun onCleared() {
        flow.cancel()
    }
}
