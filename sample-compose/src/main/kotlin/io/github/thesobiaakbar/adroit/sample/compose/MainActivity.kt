package io.github.thesobiaakbar.adroit.sample.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.thesobiaakbar.adroit.AdSlots
import io.github.thesobiaakbar.adroit.billing.AdroitBilling
import io.github.thesobiaakbar.adroit.billing.ShowDecision
import io.github.thesobiaakbar.adroit.sample.compose.ads.SampleAds
import io.github.thesobiaakbar.adroit.sample.compose.billing.SamplePaywalls
import io.github.thesobiaakbar.adroit.sample.compose.ui.SampleApp
import io.github.thesobiaakbar.adroit.sample.compose.ui.SampleTheme

/**
 * Every screen after the splash, as navigation destinations ([SampleApp]): Home, Feed and Settings in a bottom bar,
 * then Details and Native styles. Each destination's banners and native ads follow its own lifecycle.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Right after the splash: the onboarding paywall, when its show rules allow (once per session, never to a
        // premium user). Not again after a rotation. Firebase's Onboarding key changes it on the next launch.
        val onboarding = savedInstanceState == null &&
            AdroitBilling.paywalls.shouldShow(SamplePaywalls.Onboarding, "onboarding") == ShowDecision.Show
        setContent {
            // Play's messages about the user's subscriptions, for example a declined payment.
            LifecycleResumeEffect(Unit) {
                AdroitBilling.showInAppMessages(this@MainActivity)
                onPauseOrDispose {}
            }
            SampleTheme { SampleApp(showOnboarding = onboarding) }
        }
        // Ads load only when asked: the app open ad now, for the user's first return to the app. It has loadAgain,
        // so each later return finds a new one. Nothing happens if it's loaded already.
        AdSlots.load(SampleAds.AppOpenAd)
    }
}
