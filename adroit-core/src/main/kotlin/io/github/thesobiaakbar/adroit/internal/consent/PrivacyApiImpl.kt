package io.github.thesobiaakbar.adroit.internal.consent

import android.app.Activity
import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.ConsentState
import io.github.thesobiaakbar.adroit.PrivacyApi
import io.github.thesobiaakbar.adroit.config.AdEnvironment
import io.github.thesobiaakbar.adroit.internal.logging.AdLog
import io.github.thesobiaakbar.adroit.internal.threading.runOnMain
import kotlinx.coroutines.flow.StateFlow

/** [PrivacyApi] over [ConsentCoordinator]. Callable from any thread; work and callbacks run on the main thread. */
internal class PrivacyApiImpl(
    private val coordinator: ConsentCoordinator,
    private val environment: AdEnvironment,
    private val log: AdLog,
) : PrivacyApi {
    override val consentState: StateFlow<ConsentState> get() = coordinator.state

    override val isPrivacyOptionsRequired: StateFlow<Boolean> get() = coordinator.privacyOptionsRequired

    override fun showPrivacyOptions(activity: Activity, onDismissed: (AdError?) -> Unit) {
        runOnMain { coordinator.showPrivacyOptions(activity, onDismissed) }
    }

    override fun resetConsentForTesting() {
        if (environment == AdEnvironment.PRODUCTION) {
            log.e { "resetConsentForTesting() is ignored in PRODUCTION" }
            return
        }
        runOnMain { coordinator.resetConsent() }
    }
}
