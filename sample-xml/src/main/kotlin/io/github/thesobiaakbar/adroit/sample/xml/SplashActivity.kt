package io.github.thesobiaakbar.adroit.sample.xml

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.thesobiaakbar.adroit.sample.xml.databinding.ActivitySplashBinding
import io.github.thesobiaakbar.adroit.splash.SplashState
import kotlinx.coroutines.launch

/**
 * The launcher, driven by [io.github.thesobiaakbar.adroit.splash.SplashFlow] (docs/SPLASH.md): consent (at most 8 s for the form),
 * then the splash timer from Firebase with a progress bar, and the splash ad (`SampleAds.SplashAd`) if it loads in
 * time. Premium users skip consent and the ad; offline users get the short timer. It continues to [MainActivity]
 * whatever happens, and never while the ad is on screen.
 *
 * A loading screen by design (hence the lint suppression): it hosts consent and the splash ad, which the system
 * splash screen can't show. The sample doesn't add the SplashScreen library for this.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    private val viewModel: SplashViewModel by viewModels()
    private lateinit var binding: ActivitySplashBinding
    private var logoAnimation: ValueAnimator? = null
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        animateLogo()

        // Called again after a rotation: the ViewModel's flow keeps running and takes the new Activity.
        viewModel.flow.start(this)
        lifecycleScope.launch {
            // STARTED: navigation waits until the screen is in front, so a splash sent to the background finishes
            // when the user comes back.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.flow.state.collect(::render)
            }
        }
    }

    private fun render(state: SplashState) {
        when (state) {
            SplashState.Preparing -> showProgress(null)
            is SplashState.Loading -> showProgress(state.progress)
            SplashState.ShowingAd -> showProgress(100)
            is SplashState.Finished -> goToMain()
        }
    }

    private fun showProgress(percent: Int?) {
        binding.progress.isVisible = percent != null
        binding.progressLabel.isVisible = percent != null
        if (percent == null) return
        binding.progress.setProgressCompat(percent, true)
        binding.progressLabel.text = getString(R.string.splash_progress, percent)
    }

    /** A gentle pulse of the logo and a fade-in of the name: the sample's splash animation. */
    private fun animateLogo() {
        binding.appName.alpha = 0f
        binding.appName.animate().alpha(1f).setStartDelay(300).setDuration(600).start()
        logoAnimation = ObjectAnimator.ofPropertyValuesHolder(
            binding.logo,
            PropertyValuesHolder.ofFloat("scaleX", 0.9f, 1.08f),
            PropertyValuesHolder.ofFloat("scaleY", 0.9f, 1.08f),
            PropertyValuesHolder.ofFloat("rotation", -6f, 6f),
        ).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    override fun onDestroy() {
        logoAnimation?.cancel()
        super.onDestroy()
    }

    private fun goToMain() {
        if (navigated || isFinishing || isDestroyed) return
        navigated = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
