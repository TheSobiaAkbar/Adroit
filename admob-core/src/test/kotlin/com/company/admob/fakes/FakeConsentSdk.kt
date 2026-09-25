package com.company.admob.fakes

import android.app.Activity
import com.company.admob.AdError
import com.company.admob.ConsentStatus
import com.company.admob.internal.port.ConsentParams
import com.company.admob.internal.port.ConsentSdk
import com.company.admob.internal.port.ConsentUpdateResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Scriptable [ConsentSdk]. Results are delivered through [scope], never synchronously. [onFormShown] lets a test
 * change the consent state when the form is shown, as a real user would.
 */
internal class FakeConsentSdk(private val scope: CoroutineScope) : ConsentSdk {
    var updateResult: ConsentUpdateResult = ConsentUpdateResult.Success
    var updateDelayMs: Long = 0
    var formResult: AdError? = null
    var privacyOptionsFormResult: AdError? = null
    var canRequestAds: Boolean = true
    var status: ConsentStatus = ConsentStatus.NOT_REQUIRED
    var privacyOptionsRequired: Boolean = false
    var onFormShown: (FakeConsentSdk.() -> Unit)? = null

    val updateRequests = mutableListOf<ConsentParams>()
    var formShowCount: Int = 0
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
        onFormShown?.invoke(this)
        val result = formResult
        scope.launch { onDone(result) }
    }

    override fun canRequestAds(): Boolean = canRequestAds

    override fun consentStatus(): ConsentStatus = status

    override fun privacyOptionsRequired(): Boolean = privacyOptionsRequired

    override fun showPrivacyOptionsForm(activity: Activity, onDone: (AdError?) -> Unit) {
        privacyOptionsFormShowCount++
        val result = privacyOptionsFormResult
        scope.launch { onDone(result) }
    }

    override fun reset() {
        resetCount++
    }
}
