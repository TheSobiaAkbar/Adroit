package io.github.thesobiaakbar.adroit.docs.guides.adsiap.xml

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import io.github.thesobiaakbar.adroit.billing.AdroitBilling
import io.github.thesobiaakbar.adroit.billing.ShowDecision
import io.github.thesobiaakbar.adroit.billing.views.AdroitPaywall
import io.github.thesobiaakbar.adroit.billing.views.PaywallLaunch
import io.github.thesobiaakbar.adroit.docs.R
import io.github.thesobiaakbar.adroit.docs.billing.AppPaywalls
import io.github.thesobiaakbar.adroit.docs.guides.AppAds
import io.github.thesobiaakbar.adroit.docs.guides.showSplashProgress
import io.github.thesobiaakbar.adroit.splash.SplashFlow
import io.github.thesobiaakbar.adroit.splash.SplashState
import io.github.thesobiaakbar.adroit.update.AdroitUpdate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// The splash of docs/guides/ADS_IAP_XML.md, step 6 (sample-xml's SplashActivity).

class MainActivity : AppCompatActivity()

// region guide-ads-iap-splash-xml
/** Keeps the splash across rotations: the update check, consent, the timer, the splash ad and the routing run once. */
class SplashViewModel(application: Application) : AndroidViewModel(application) {
    // A premium user skips consent and the ad: SplashFlow reads Adroit.isAdFree, set by the billing bridge.
    val flow = SplashFlow.create(application, AppAds.SplashAd)

    /** The routing after the splash has started (the paywall is up): a rotation doesn't repeat it. */
    var routing = false

    /** The routing is over: open the main screen, from whichever Activity is current. */
    val goToMain = MutableStateFlow(false)

    private var update: Job? = null

    /** Google Play's update check, once per launch: a rotation waits for the same check. */
    suspend fun checkForUpdate(activity: Activity) {
        val job = update ?: viewModelScope.launch { AdroitUpdate.check(activity) }.also { update = it }
        job.join()
    }

    override fun onCleared() = flow.cancel()
}

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    private val viewModel: SplashViewModel by viewModels()
    private var navigated = false

    // Registered in every onCreate: after a rotation, the paywall's result arrives here.
    private val paywall = registerForActivityResult(AdroitPaywall.contract()) { viewModel.goToMain.value = true }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        lifecycleScope.launch {
            // The update first: a forced update must not let consent or an ad start.
            viewModel.checkForUpdate(this@SplashActivity)
            viewModel.flow.start(this@SplashActivity) // after a rotation, hands the new Activity to the running flow
        }
        lifecycleScope.launch {
            // RESUMED, not STARTED: a splash sent to the background moves on when the user comes back. Android
            // can take seconds to stop an Activity after Home; while it's paused but started, the timer could end
            // and open the next screen unseen.
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    viewModel.flow.state.collect { state ->
                        when (state) {
                            SplashState.Preparing -> showSplashProgress(null) // update check, consent: no bar yet
                            is SplashState.Loading -> showSplashProgress(state.progress) // 0..100
                            SplashState.ShowingAd -> showSplashProgress(100) // the splash ad is on screen
                            is SplashState.Finished -> route()
                        }
                    }
                }
                viewModel.goToMain.first { it }
                goToMain()
            }
        }
    }

    /** Once per launch: the launch paywall if its rules allow, else straight to main. */
    private fun route() {
        if (viewModel.routing) return
        viewModel.routing = true
        if (AdroitBilling.paywalls.shouldShow(AppPaywalls.Onboarding, "onboarding") == ShowDecision.Show) {
            paywall.launch(PaywallLaunch(AppPaywalls.Onboarding, "onboarding"))
        } else {
            viewModel.goToMain.value = true
        }
    }

    private fun goToMain() {
        if (navigated) return
        navigated = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
// endregion
