package io.github.thesobiaakbar.adroit.docs.guides.iap.xml

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.withStarted
import io.github.thesobiaakbar.adroit.billing.AdroitBilling
import io.github.thesobiaakbar.adroit.billing.views.AdroitPaywall
import io.github.thesobiaakbar.adroit.billing.views.PaywallLaunch
import io.github.thesobiaakbar.adroit.docs.R
import io.github.thesobiaakbar.adroit.docs.guides.iap.AppPaywalls
import io.github.thesobiaakbar.adroit.update.AdroitUpdate
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// The splash of docs/guides/IAP_XML.md, step 6.

class MainActivity : AppCompatActivity()

// region guide-iap-splash-xml
/** Keeps the splash across rotations: the update check and the paywall happen once per launch. */
class SplashViewModel(application: Application) : AndroidViewModel(application) {
    /** The paywall was opened: a rotation doesn't open it again. */
    var paywallOpened = false

    private var update: Job? = null

    /** Google Play's update check, once per launch: a rotation waits for the same check. */
    suspend fun checkForUpdate(activity: Activity) {
        val job = update ?: viewModelScope.launch { AdroitUpdate.check(activity) }.also { update = it }
        job.join()
    }
}

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    private val viewModel: SplashViewModel by viewModels()

    // Registered in every onCreate: after a rotation, the paywall's result arrives here.
    private val subscribe = registerForActivityResult(AdroitPaywall.contract()) {
        // Bought or restored: in. Otherwise the user left the hard paywall: leave the app.
        if (isSubscribed()) goToMain() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        lifecycleScope.launch {
            viewModel.checkForUpdate(this@SplashActivity) // a forced update closes the app if declined
            withStarted {
                when {
                    isSubscribed() -> goToMain()
                    !viewModel.paywallOpened -> {
                        viewModel.paywallOpened = true
                        subscribe.launch(PaywallLaunch(AppPaywalls.Subscribe, "launch"))
                    }
                }
            }
        }
    }

    /** The saved purchases, read in Application.onCreate, then kept up to date with Google Play. */
    private fun isSubscribed() = AdroitBilling.entitlements.value.has("premium")

    private fun goToMain() {
        if (isFinishing) return
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
// endregion
