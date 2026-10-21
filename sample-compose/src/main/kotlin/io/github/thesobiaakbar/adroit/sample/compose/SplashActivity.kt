package io.github.thesobiaakbar.adroit.sample.compose

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.repeatOnLifecycle
import io.github.thesobiaakbar.adroit.billing.AdroitBilling
import io.github.thesobiaakbar.adroit.billing.ShowDecision
import io.github.thesobiaakbar.adroit.billing.compose.Paywall
import io.github.thesobiaakbar.adroit.sample.compose.billing.SamplePaywalls
import io.github.thesobiaakbar.adroit.sample.compose.ui.SampleTheme
import io.github.thesobiaakbar.adroit.splash.SplashState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The launcher (the AdMob + IAP setup, docs/guides/ADS_IAP_COMPOSE.md):
 * 1. Google Play's update check (`AdroitUpdate`, Firebase `AppUpdate`): flexible, forced or off.
 * 2. [io.github.thesobiaakbar.adroit.splash.SplashFlow] (docs/SPLASH.md): consent, then the splash timer from Firebase
 *    with a progress bar, and the splash ad (`SampleAds.SplashAd`) if it loads in time. Premium users skip consent
 *    and the ad; offline users get the short timer.
 * 3. The routing ([SplashRoute]): the Onboarding paywall, drawn here, when its show rules allow; then [MainActivity].
 *    It never leaves while an ad is on screen.
 *
 * Its own Activity, not a navigation destination of [MainActivity]: the app open ad excludes it by class name
 * (`SampleAds.AppOpenAd`), and the consent form and splash ad need an Activity that nothing else is drawn over.
 *
 * A loading screen by design (hence the lint suppression): it hosts consent and the splash ad, which the system
 * splash screen can't show. The sample doesn't add the SplashScreen library for this.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {
    private val viewModel: SplashViewModel by viewModels()
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            // The update first: a forced update must not let consent or an ad start. After a rotation this waits for
            // the same check, then hands the new Activity to the running flow.
            viewModel.checkForUpdate(this@SplashActivity)
            viewModel.flow.start(this@SplashActivity)
        }
        setContent {
            SampleTheme {
                val state by viewModel.flow.state.collectAsStateWithLifecycle()
                SplashScreen(state)
                // The paywall is drawn here, over the splash (a dialog template shows the splash behind it). Closing
                // Onboarding can offer ExitDiscount: onExitPaywall runs right after onClose, in the same step, so it
                // replaces Main before navigation sees it.
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
            // STARTED: navigation waits until the screen is in front, so a splash sent to the background finishes
            // when the user comes back.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.flow.state.collect { if (it is SplashState.Finished) route() } }
                snapshotFlow { viewModel.route }.first { it == SplashRoute.Main }
                goToMain()
            }
        }
    }

    /** Once per launch: the Onboarding paywall if its rules allow, else straight to main. */
    private fun route() {
        if (viewModel.route != SplashRoute.Splash) return
        viewModel.route = if (AdroitBilling.paywalls.shouldShow(SamplePaywalls.Onboarding, "onboarding") == ShowDecision.Show) {
            SplashRoute.Paywall(SamplePaywalls.Onboarding)
        } else {
            SplashRoute.Main
        }
    }

    private fun goToMain() {
        if (navigated || isFinishing || isDestroyed) return
        navigated = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

/** The logo, the app's name, and the progress bar once the timer runs (hidden while consent is settled). */
@Composable
private fun SplashScreen(state: SplashState) {
    val progress = when (state) {
        SplashState.Preparing -> null
        is SplashState.Loading -> state.progress
        SplashState.ShowingAd, is SplashState.Finished -> 100
    }
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.align(BiasAlignment(0f, -0.16f)), horizontalAlignment = Alignment.CenterHorizontally) {
                PulsingLogo()
                Spacer(Modifier.height(24.dp))
                AppName()
            }
            if (progress != null) {
                val animated by animateFloatAsState(progress / 100f, label = "splash progress")
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(start = 48.dp, end = 48.dp, bottom = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.splash_progress, progress), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth().height(6.dp))
                }
            }
        }
    }
}

/** A gentle pulse and sway of the logo: the sample's splash animation. */
@Composable
private fun PulsingLogo() {
    val transition = rememberInfiniteTransition(label = "logo")
    val spec = infiniteRepeatable<Float>(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    val scale by transition.animateFloat(0.9f, 1.08f, spec, label = "scale")
    val rotation by transition.animateFloat(-6f, 6f, spec, label = "rotation")
    Image(
        painterResource(R.drawable.splash_logo),
        contentDescription = null,
        modifier = Modifier.size(120.dp).graphicsLayer {
            scaleX = scale
            scaleY = scale
            rotationZ = rotation
        },
    )
}

/** The app's name, fading in. */
@Composable
private fun AppName() {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(600, delayMillis = 300), label = "name")
    LaunchedEffect(Unit) { visible = true }
    Text(stringResource(R.string.app_name), Modifier.alpha(alpha), style = MaterialTheme.typography.headlineSmall)
}
