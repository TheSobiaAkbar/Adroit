package io.github.thesobiaakbar.adroit.docs.guides.iap.compose

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import io.github.thesobiaakbar.adroit.billing.AdroitBilling
import io.github.thesobiaakbar.adroit.billing.compose.Paywall
import io.github.thesobiaakbar.adroit.docs.guides.SplashScreen
import io.github.thesobiaakbar.adroit.docs.guides.iap.AppPaywalls
import io.github.thesobiaakbar.adroit.update.AdroitUpdate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// The splash of docs/guides/IAP_COMPOSE.md, step 6.

class MainActivity : ComponentActivity()

// region guide-iap-splash-compose
/** Where the splash is. Compose state in the ViewModel: a rotation keeps it, the screen draws it. */
enum class SplashStep { Checking, Paywall, Main, Leave }

/** Keeps the splash across rotations: the update check and the paywall happen once per launch. */
class SplashViewModel(application: Application) : AndroidViewModel(application) {
    var step by mutableStateOf(SplashStep.Checking)

    private var update: Job? = null

    /** Google Play's update check, once per launch; then the hard paywall unless the user is subscribed. */
    fun start(activity: Activity) {
        if (update != null) return
        update = viewModelScope.launch {
            AdroitUpdate.check(activity) // a forced update closes the app if declined
            step = if (isSubscribed()) SplashStep.Main else SplashStep.Paywall
        }
    }

    /** Bought or restored: in. Otherwise the user left the hard paywall: leave the app. */
    fun onPaywallClosed() {
        step = if (isSubscribed()) SplashStep.Main else SplashStep.Leave
    }

    /** The saved purchases, read in Application.onCreate, then kept up to date with Google Play. */
    private fun isSubscribed() = AdroitBilling.entitlements.value.has("premium")
}

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {
    private val viewModel: SplashViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.start(this)
        setContent {
            MaterialTheme {
                SplashScreen(percent = null)
                // The hard paywall, drawn over the splash. It has no close button and back doesn't close it.
                if (viewModel.step == SplashStep.Paywall) {
                    Paywall(AppPaywalls.Subscribe, placement = "launch", onClose = { viewModel.onPaywallClosed() })
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                when (snapshotFlow { viewModel.step }.first { it == SplashStep.Main || it == SplashStep.Leave }) {
                    SplashStep.Main -> startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    else -> Unit
                }
                finish()
            }
        }
    }
}
// endregion
