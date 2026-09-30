package io.github.thesobiaakbar.adroit.fakes

import android.app.Activity
import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.ConsentStatus
import io.github.thesobiaakbar.adroit.internal.port.ConsentForm
import io.github.thesobiaakbar.adroit.internal.port.ConsentParams
import io.github.thesobiaakbar.adroit.internal.port.ConsentSdk
import io.github.thesobiaakbar.adroit.internal.port.ConsentUpdateResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Scriptable [ConsentSdk]. Results are delivered through [scope], never synchronously. [onFormShown] lets a test
 * change the consent state when the form is shown, as a real user would. Activities are only held weakly, so leak
 * tests stay meaningful.
 */
internal class FakeConsentSdk(private val scope: CoroutineScope) : ConsentSdk {
    /** How many times [warmUp] ran. */
    var warmUps = 0

    override fun warmUp() {
        warmUps++
    }

    var updateResult: ConsentUpdateResult = ConsentUpdateResult.Success
    var updateDelayMs: Long = 0
    var formResult: AdError? = null
    var privacyOptionsFormResult: AdError? = null
    var canRequestAds: Boolean = true
    var status: ConsentStatus = ConsentStatus.NOT_REQUIRED
    var privacyOptionsRequired: Boolean = false
    var onFormShown: (FakeConsentSdk.() -> Unit)? = null
    var onPrivacyOptionsFormShown: (FakeConsentSdk.() -> Unit)? = null

    val updateRequests = mutableListOf<ConsentParams>()
    var formShowCount: Int = 0
        private set
    var formActivity: WeakReference<Activity>? = null
        private set
    var privacyOptionsFormShowCount: Int = 0
        private set
    var resetCount: Int = 0
        private set

    override fun requestUpdate(activity: Activity, params: ConsentParams, onDone: (ConsentUpdateResult) -> Unit) {
        updateRequests += params
        val result = updateResult
        scope.launch {
            delay(updateDelayMs)
            onDone(result)
        }
    }

    override fun loadAndShowFormIfRequired(activity: Activity, onDone: (AdError?) -> Unit) {
        formShowCount++
        formActivity = WeakReference(activity)
        onFormShown?.invoke(this)
        val result = formResult
        scope.launch { onDone(result) }
    }

    /** How long [loadFormIfRequired] takes to load a required form. */
    var formLoadDelayMs: Long = 0

    /** How long the user keeps a shown form open (split path). */
    var formOpenMs: Long = 0

    /** Forms loaded by [loadFormIfRequired]. */
    var formLoads: Int = 0
        private set

    override fun loadFormIfRequired(activity: Activity, onLoaded: (ConsentForm?, AdError?) -> Unit) {
        scope.launch {
            if (status != ConsentStatus.REQUIRED) return@launch onLoaded(null, null)
            delay(formLoadDelayMs)
            formLoads++
            onLoaded(
                ConsentForm { showActivity, onDone ->
                    formShowCount++
                    formActivity = WeakReference(showActivity)
                    onFormShown?.invoke(this@FakeConsentSdk)
                    val result = formResult
                    scope.launch {
                        delay(formOpenMs)
                        onDone(result)
                    }
                },
                null,
            )
        }
    }

    override fun canRequestAds(): Boolean = canRequestAds

    override fun consentStatus(): ConsentStatus = status

    override fun privacyOptionsRequired(): Boolean = privacyOptionsRequired

    override fun showPrivacyOptionsForm(activity: Activity, onDone: (AdError?) -> Unit) {
        privacyOptionsFormShowCount++
        onPrivacyOptionsFormShown?.invoke(this)
        val result = privacyOptionsFormResult
        scope.launch { onDone(result) }
    }

    override fun reset() {
        resetCount++
    }
}
