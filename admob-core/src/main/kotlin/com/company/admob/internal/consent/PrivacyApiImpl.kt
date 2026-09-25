package com.company.admob.internal.consent

import android.app.Activity
import com.company.admob.AdError
import com.company.admob.ConsentState
import com.company.admob.PrivacyApi
import com.company.admob.config.AdEnvironment
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.threading.runOnMain
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
