package io.github.thesobiaakbar.adroit.sample.xml

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** Launch → consent and SDK init → maybe a cold-start app open ad → MainActivity, within 10 s, ad or not. */
@RequiresNetwork
@RunWith(AndroidJUnit4::class)
class StartupSmokeTest {
    @Test
    fun splashReachesMainWithinTenSeconds() {
        ActivityScenario.launch(SplashActivity::class.java).use {
            awaitResumed<MainActivity>(timeoutMillis = 10_000)
        }
    }
}
