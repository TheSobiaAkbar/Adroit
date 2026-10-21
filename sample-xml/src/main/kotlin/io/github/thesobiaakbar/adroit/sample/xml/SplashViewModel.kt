package io.github.thesobiaakbar.adroit.sample.xml

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.thesobiaakbar.adroit.sample.xml.ads.SampleAds
import io.github.thesobiaakbar.adroit.splash.SplashFlow
import io.github.thesobiaakbar.adroit.update.AdroitUpdate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Keeps the splash sequence across rotations: the update check, consent, the timer, the splash ad and the routing run
 * once per launch. A premium user skips consent and the ad: SplashFlow reads `Adroit.isAdFree`, which
 * SampleApplication's billing bridge has already set from the entitlements saved by the last launch.
 */
class SplashViewModel(application: Application) : AndroidViewModel(application) {
    val flow: SplashFlow = SplashFlow.create(application, SampleAds.SplashAd)

    private var update: Job? = null

    /** The routing after [SplashFlow] finished has started (the paywall or the ad is up): a rotation doesn't repeat it. */
    var routing: Boolean = false

    /** The routing is over: open the main screen, from whichever Activity is current. */
    val goToMain: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /**
     * Google Play's update check, once per launch (docs/APP_UPDATE.md). The first Activity runs it; after a rotation
     * the new one waits for the same check. A forced update the user declines closes the app, so this never returns.
     */
    suspend fun checkForUpdate(activity: Activity) {
        val job = update ?: viewModelScope.launch { AdroitUpdate.check(activity) }.also { update = it }
        job.join()
    }

    override fun onCleared() {
        flow.cancel()
    }
}
