package io.github.thesobiaakbar.adroit.sample.xml

import androidx.test.platform.app.InstrumentationRegistry
import io.github.thesobiaakbar.adroit.billing.AdroitBilling
import io.github.thesobiaakbar.adroit.sample.xml.billing.SamplePaywalls
import org.junit.rules.ExternalResource

/**
 * Counts the onboarding paywall as shown this session, before the test starts the app. It shows at most once per
 * session, after the splash, and would otherwise cover MainActivity in a test about ads (PaywallLeakTest covers it).
 */
class OnboardingShownRule : ExternalResource() {
    override fun before() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AdroitBilling.paywalls.recordShown(SamplePaywalls.Onboarding, "onboarding")
        }
    }
}
