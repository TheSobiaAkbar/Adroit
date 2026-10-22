@file:Suppress("unused")

package io.github.thesobiaakbar.adroit.docs

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.thesobiaakbar.adroit.splash.SplashFlow
import io.github.thesobiaakbar.adroit.splash.SplashState
import kotlinx.coroutines.launch

// region splash-viewmodel
class SplashViewModel(application: Application) : AndroidViewModel(application) {
    // Survives rotation: consent, the timer and the splash ad run once per launch. A premium user (Adroit.isAdFree,
    // from your Adroit.setAdFree call in Application.onCreate) skips consent and the ad.
    val flow = SplashFlow.create(application, AppAds.SplashAd)

    override fun onCleared() = flow.cancel()
}
// endregion

class SplashScreenActivity : AppCompatActivity() {
    private val viewModel: SplashViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(this)[SplashViewModel::class.java]
    }

    // region splash-activity
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.snippet_banner) // your splash layout: logo, app name, progress bar
        viewModel.flow.start(this) // after a rotation, hands the new Activity to the running flow
        lifecycleScope.launch {
            // RESUMED, not STARTED: a splash sent to the background moves on when the user comes back. Android
            // can take seconds to stop an Activity after Home; while it's paused but started, the timer could end
            // and open the next screen unseen.
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.flow.state.collect { state ->
                    when (state) {
                        SplashState.Preparing -> hideProgress() // consent: no progress bar yet
                        is SplashState.Loading -> showProgress(state.progress) // 0..100
                        SplashState.ShowingAd -> Unit // the splash ad is on screen
                        is SplashState.Finished -> goToMain()
                    }
                }
            }
        }
    }
    // endregion

    private fun hideProgress() = Unit

    private fun showProgress(percent: Int) = Unit

    private fun goToMain() {
        startActivity(Intent(this, AppCompatActivity::class.java))
        finish()
    }
}
