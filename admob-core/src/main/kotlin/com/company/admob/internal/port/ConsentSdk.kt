package com.company.admob.internal.port

import android.app.Activity
import com.company.admob.AdError
import com.company.admob.ConsentStatus
import com.company.admob.config.DebugGeography

/**
 * The engines' only view of the UMP SDK (architecture §9). Main thread only; every callback is delivered on the main
 * thread, always posted, and at most once per call.
 */
internal interface ConsentSdk {
    /**
     * Called once during `initialize`, on a background thread: loads the SDK's stored consent state, so the first
     * call on the main thread in `start()` doesn't read it from disk.
     */
    fun warmUp() {}

    /** Requests up-to-date consent information. Call on every app launch. */
    fun requestUpdate(activity: Activity, params: ConsentParams, onDone: (ConsentUpdateResult) -> Unit)

    /** Loads and shows the consent form if one is required. [onDone] receives null on success. */
    fun loadAndShowFormIfRequired(activity: Activity, onDone: (AdError?) -> Unit)

    /** Whether ads may be requested. Always false before the first [requestUpdate]. */
    fun canRequestAds(): Boolean

    fun consentStatus(): ConsentStatus

    /** Whether the app must show a privacy options entry point. */
    fun privacyOptionsRequired(): Boolean

    fun showPrivacyOptionsForm(activity: Activity, onDone: (AdError?) -> Unit)

    /** Clears consent state. For testing only. */
    fun reset()
}

internal data class ConsentParams(
    val debugGeography: DebugGeography,
    val testDeviceHashedIds: List<String>,
    val tagUnderAgeOfConsent: Boolean,
    val consentSyncId: String?,
)

internal sealed interface ConsentUpdateResult {
    data object Success : ConsentUpdateResult

    /** UMP falls back to the previous session's consent, so check [ConsentSdk.canRequestAds] anyway. */
    data class Failure(val error: AdError) : ConsentUpdateResult
}
