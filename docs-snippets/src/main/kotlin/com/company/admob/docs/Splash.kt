@file:Suppress("unused")

package com.company.admob.docs

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.company.admob.splash.SplashFlow
import com.company.admob.splash.SplashState
import kotlinx.coroutines.launch

private object Premium {
    fun isPremium(context: android.content.Context): Boolean = false
}

// region splash-viewmodel
class SplashViewModel(application: Application) : AndroidViewModel(application) {
    // Survives rotation: consent, the timer and the splash ad run once per launch.
    val flow = SplashFlow.create(application, AppAds.SplashAd, isPremium = Premium.isPremium(application))

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
            // STARTED: a splash sent to the background moves on when the user comes back.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
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
