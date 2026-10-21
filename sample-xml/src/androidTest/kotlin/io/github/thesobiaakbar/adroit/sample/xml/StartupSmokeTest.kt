package io.github.thesobiaakbar.adroit.sample.xml

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.thesobiaakbar.adroit.sample.xml.ads.SampleAds
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Launch → consent and SDK init → the splash ad if it's ready before the splash timer ends (closed here, as a user
 * would) → MainActivity. Without an ad the splash lasts its whole timer, `SampleAds.settings.splashSeconds`.
 */
@RequiresNetwork
@RunWith(AndroidJUnit4::class)
class StartupSmokeTest {
    @get:Rule
    val onboardingShown = OnboardingShownRule()

    @Test
    fun splashReachesMainWithinItsTimer() {
        ActivityScenario.launch(SplashActivity::class.java).use {
            dismissFullScreenAdIfShown(showTimeoutMillis = SPLASH_MILLIS)
            awaitResumed<MainActivity>(timeoutMillis = 5_000)
        }
    }

    private companion object {
        /** Consent and SDK init (about 2 s here), then the splash timer. */
        val SPLASH_MILLIS = (SampleAds.settings.splashSeconds + 4) * 1_000L
    }
}
