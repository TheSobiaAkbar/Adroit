package io.github.thesobiaakbar.adroit.docs.guides.adsiap.compose

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import io.github.thesobiaakbar.adroit.billing.AdroitBilling
import io.github.thesobiaakbar.adroit.billing.PaywallDefinition
import io.github.thesobiaakbar.adroit.billing.ShowDecision
import io.github.thesobiaakbar.adroit.billing.compose.Paywall
import io.github.thesobiaakbar.adroit.docs.billing.AppPaywalls
import io.github.thesobiaakbar.adroit.docs.guides.AppAds
import io.github.thesobiaakbar.adroit.docs.guides.SplashScreen
import io.github.thesobiaakbar.adroit.docs.guides.percent
import io.github.thesobiaakbar.adroit.splash.SplashFlow
import io.github.thesobiaakbar.adroit.splash.SplashState
import io.github.thesobiaakbar.adroit.update.AdroitUpdate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// The splash of docs/guides/ADS_IAP_COMPOSE.md, step 6 (sample-compose's SplashActivity).

class MainActivity : ComponentActivity()

// region guide-ads-iap-splash-compose
/** The splash's steps after SplashFlow: each happens once, whatever the rotations. */
sealed interface SplashRoute {
    data object Splash : SplashRoute // the splash sequence runs
    data class Paywall(val definition: PaywallDefinition) : SplashRoute // the launch paywall is on screen
    data object Main : SplashRoute // open the main screen
}

/** Keeps the splash across rotations: the update check, consent, the timer, the splash ad and the routing run once. */
class SplashViewModel(application: Application) : AndroidViewModel(application) {
    // A premium user skips consent and the ad: SplashFlow reads Adroit.isAdFree, set by the billing bridge.
    val flow = SplashFlow.create(application, AppAds.SplashAd)

    /** Where the splash is after SplashFlow finished. Compose state: a rotation keeps it, the screen draws it. */
    var route: SplashRoute by mutableStateOf(SplashRoute.Splash)

    private var update: Job? = null

    /** Google Play's update check, once per launch: a rotation waits for the same check. */
    suspend fun checkForUpdate(activity: Activity) {
        val job = update ?: viewModelScope.launch { AdroitUpdate.check(activity) }.also { update = it }
        job.join()
    }

    override fun onCleared() = flow.cancel()
}

/** Its own Activity, not a navigation destination: the app open ad excludes it by class (`excludedActivities`). */
@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {
    private val viewModel: SplashViewModel by viewModels()
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            // The update first: a forced update must not let consent or an ad start.
            viewModel.checkForUpdate(this@SplashActivity)
            viewModel.flow.start(this@SplashActivity) // after a rotation, hands the new Activity to the running flow
        }
        setContent {
            MaterialTheme {
                val state by viewModel.flow.state.collectAsStateWithLifecycle()
                SplashScreen(state.percent())
                // The paywall is drawn here, over the splash. Closing Onboarding can offer ExitDiscount: onExitPaywall
                // runs right after onClose, in the same step, so it replaces Main before navigation sees it.
                (viewModel.route as? SplashRoute.Paywall)?.let { route ->
                    key(route.definition) {
                        Paywall(
                            route.definition,
                            placement = "onboarding",
                            onClose = { viewModel.route = SplashRoute.Main },
                            onExitPaywall = { exit -> viewModel.route = SplashRoute.Paywall(exit) },
                        )
                    }
                }
            }
        }
        lifecycleScope.launch {
            // STARTED: a splash sent to the background moves on when the user comes back.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.flow.state.collect { if (it is SplashState.Finished) route() } }
                snapshotFlow { viewModel.route }.first { it == SplashRoute.Main }
                goToMain()
            }
        }
    }

    /** Once per launch: the launch paywall if its rules allow, else straight to main. */
    private fun route() {
        if (viewModel.route != SplashRoute.Splash) return
        val paywall = AdroitBilling.paywalls.shouldShow(AppPaywalls.Onboarding, "onboarding") == ShowDecision.Show
        viewModel.route = if (paywall) SplashRoute.Paywall(AppPaywalls.Onboarding) else SplashRoute.Main
    }

    private fun goToMain() {
        if (navigated) return
        navigated = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
// endregion
