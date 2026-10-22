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
import io.github.thesobiaakbar.adroit.billing.AdroitBilling
import io.github.thesobiaakbar.adroit.billing.ShowDecision
import io.github.thesobiaakbar.adroit.billing.views.AdroitPaywall
import io.github.thesobiaakbar.adroit.billing.views.PaywallLaunch
import io.github.thesobiaakbar.adroit.sample.xml.billing.SamplePaywalls
import io.github.thesobiaakbar.adroit.sample.xml.databinding.ActivitySplashBinding
import io.github.thesobiaakbar.adroit.splash.SplashState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The launcher (the AdMob + IAP setup, docs/guides/ADS_IAP_XML.md):
 * 1. Google Play's update check (`AdroitUpdate`, Firebase `AppUpdate`): flexible, forced or off.
 * 2. [io.github.thesobiaakbar.adroit.splash.SplashFlow] (docs/SPLASH.md): consent (at most 8 s for the form), then the
 *    splash timer from Firebase with a progress bar, and the splash ad (`SampleAds.SplashAd`) if it loads in time.
 *    Premium users skip consent and the ad; offline users get the short timer.
 * 3. The routing: the Onboarding paywall when its show rules allow, then [MainActivity]. It never leaves while an ad
 *    is on screen.
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

    // Registered in every onCreate: after a rotation, the paywall's result arrives here.
    private val paywall = registerForActivityResult(AdroitPaywall.contract()) { viewModel.goToMain.value = true }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        animateLogo()

        lifecycleScope.launch {
            // The update first: a forced update must not let consent or an ad start. After a rotation this waits for
            // the same check, then hands the new Activity to the running flow.
            viewModel.checkForUpdate(this@SplashActivity)
            viewModel.flow.start(this@SplashActivity)
        }
        lifecycleScope.launch {
            // RESUMED, not STARTED: navigation waits until the screen is resumed, so a splash sent to the
            // background finishes when the user comes back. Android can take seconds to stop it after Home.
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch { viewModel.flow.state.collect(::render) }
                viewModel.goToMain.first { it }
                goToMain()
            }
        }
    }

    private fun render(state: SplashState) {
        when (state) {
            SplashState.Preparing -> showProgress(null)
            is SplashState.Loading -> showProgress(state.progress)
            SplashState.ShowingAd -> showProgress(100)
            is SplashState.Finished -> route()
        }
    }

    /** Once per launch: the Onboarding paywall if its rules allow, else straight to main. */
    private fun route() {
        if (viewModel.routing) return
        viewModel.routing = true
        if (AdroitBilling.paywalls.shouldShow(SamplePaywalls.Onboarding, "onboarding") == ShowDecision.Show) {
            paywall.launch(PaywallLaunch(SamplePaywalls.Onboarding, "onboarding"))
        } else {
            viewModel.goToMain.value = true
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
