package io.github.thesobiaakbar.adroit.sample.compose

import android.app.Activity
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.thesobiaakbar.adroit.billing.PaywallDefinition
import io.github.thesobiaakbar.adroit.sample.compose.ads.SampleAds
import io.github.thesobiaakbar.adroit.splash.SplashFlow
import io.github.thesobiaakbar.adroit.update.AdroitUpdate
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Keeps the splash sequence across rotations: the update check, consent, the timer, the splash ad and the routing run
 * once per launch. A premium user skips consent and the ad: SplashFlow reads `Adroit.isAdFree`, which
 * SampleApplication's billing bridge has already set from the entitlements saved by the last launch.
 */
class SplashViewModel(application: Application) : AndroidViewModel(application) {
    val flow: SplashFlow = SplashFlow.create(application, SampleAds.SplashAd)

    /** Where the splash is after [SplashFlow] finished. Compose state: a rotation keeps it, the screen draws it. */
    var route: SplashRoute by mutableStateOf(SplashRoute.Splash)

    private var update: Job? = null

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

/** The splash's steps after [SplashFlow]: each happens once, whatever the rotations. */
sealed interface SplashRoute {
    /** The splash sequence runs. */
    data object Splash : SplashRoute

    /** The launch paywall is on screen. */
    data class Paywall(val definition: PaywallDefinition) : SplashRoute

    /** Open the main screen. */
    data object Main : SplashRoute
}
