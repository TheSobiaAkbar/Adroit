package io.github.thesobiaakbar.adroit.docs.guides.ads.compose

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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import io.github.thesobiaakbar.adroit.docs.guides.AppAds
import io.github.thesobiaakbar.adroit.docs.guides.SplashScreen
import io.github.thesobiaakbar.adroit.docs.guides.percent
import io.github.thesobiaakbar.adroit.splash.SplashFlow
import io.github.thesobiaakbar.adroit.splash.SplashState
import io.github.thesobiaakbar.adroit.update.AdroitUpdate
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// The splash of docs/guides/ADS_COMPOSE.md, step 6.

// region guide-ads-splash-compose
/** Keeps the splash across rotations: the update check, consent, the timer and the splash ad run once per launch. */
class SplashViewModel(application: Application) : AndroidViewModel(application) {
    val flow = SplashFlow.create(application, AppAds.SplashAd)

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
            }
        }
        lifecycleScope.launch {
            // RESUMED, not STARTED: never navigate while the splash is in the background. Android can take
            // seconds to stop an Activity after Home, and a paused Activity is still STARTED.
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.flow.state.collect { if (it is SplashState.Finished) goToMain() }
            }
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
