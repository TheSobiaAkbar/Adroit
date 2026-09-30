package io.github.thesobiaakbar.adroit.internal.port

import android.app.Activity
import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.ConsentStatus
import io.github.thesobiaakbar.adroit.config.DebugGeography

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

    /**
     * Loads the consent form without showing it, if one is required, so the caller can still decide not to show it
     * (a splash that stopped waiting). [onLoaded] receives the form, or null with no error when none is required, or
     * null with the error. The default loads and shows in one step, for ports that can't split them.
     */
    fun loadFormIfRequired(activity: Activity, onLoaded: (ConsentForm?, AdError?) -> Unit) {
        onLoaded(ConsentForm { showActivity, onDone -> loadAndShowFormIfRequired(showActivity, onDone) }, null)
    }

    /** Whether ads may be requested. Always false before the first [requestUpdate]. */
    fun canRequestAds(): Boolean

    fun consentStatus(): ConsentStatus

    /** Whether the app must show a privacy options entry point. */
    fun privacyOptionsRequired(): Boolean

    fun showPrivacyOptionsForm(activity: Activity, onDone: (AdError?) -> Unit)

    /** Clears consent state. For testing only. */
    fun reset()
}

/** A loaded consent form. [show] delivers null on success, on the main thread, at most once. */
internal fun interface ConsentForm {
    fun show(activity: Activity, onDone: (AdError?) -> Unit)
}

internal data class ConsentParams(
    val debugGeography: DebugGeography,
    val testDeviceHashedIds: List<String>,
    val tagUnderAgeOfConsent: Boolean,
    val consentSyncId: String?,
    /** Adds this device to [testDeviceHashedIds] (debuggable builds), so [debugGeography] applies on a phone too. */
    val includeThisDevice: Boolean = false,
)

internal sealed interface ConsentUpdateResult {
    data object Success : ConsentUpdateResult

    /** UMP falls back to the previous session's consent, so check [ConsentSdk.canRequestAds] anyway. */
    data class Failure(val error: AdError) : ConsentUpdateResult
}
