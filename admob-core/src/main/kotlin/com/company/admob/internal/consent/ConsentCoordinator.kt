package com.company.admob.internal.consent

import android.app.Activity
import com.company.admob.AdError
import com.company.admob.ConsentState
import com.company.admob.config.AdEnvironment
import com.company.admob.config.AgeTreatment
import com.company.admob.config.DebugGeography
import com.company.admob.events.AdEvent
import com.company.admob.internal.config.EffectiveConfig
import com.company.admob.internal.events.EventBus
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.port.ConsentForm
import com.company.admob.internal.port.ConsentParams
import com.company.admob.internal.port.ConsentSdk
import com.company.admob.internal.port.ConsentUpdateResult
import com.company.admob.internal.threading.postDelayedOnMain
import com.company.admob.internal.threading.postToMain
import com.company.admob.internal.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import kotlin.time.Duration

/**
 * Runs Google's UMP flow (architecture §11, §30) and tells the init coordinator when ads may be requested.
 *
 * [gather] sequence: update consent information on every launch; if `canRequestAds()` is already true (consent
 * from a previous session), call [onCanRequestAds] right away so SDK init runs in parallel with the form; load and
 * show the form if required; check `canRequestAds()` again. On an update error the form is skipped, but
 * `canRequestAds()` is still checked, because UMP falls back to the previous session's consent.
 *
 * [onCanRequestAds] is called only while `canRequestAds()` is true, and at most once per gather. The init
 * coordinator guards SDK init with its own atomic flag. [onConsentRevoked] is called when the privacy options form
 * withdraws consent that allowed ads.
 *
 * A gather with a form timeout (the splash screen's) finishes when the timeout passes and no form is on screen: the
 * steps still running are abandoned, and a form that loads afterwards is never shown (it's tried again on the next
 * launch). A form already on screen isn't interrupted.
 *
 * Consent is never cached here: every value is read from UMP. Main thread only. The Activity is held weakly, and
 * only while a gather is in flight.
 */
internal class ConsentCoordinator(
    private val consentSdk: ConsentSdk,
    private val config: EffectiveConfig,
    private val log: AdLog,
    private val events: EventBus,
    private val clock: Clock,
    private val onCanRequestAds: () -> Unit,
    private val onConsentRevoked: () -> Unit,
    /** Runs a block after a delay on the main thread; returns its cancellation. */
    private val schedule: (Duration, () -> Unit) -> (() -> Unit) = ::postDelayedOnMain,
) {
    private val mutableState = MutableStateFlow(ConsentState.Unknown)
    val state: StateFlow<ConsentState> = mutableState.asStateFlow()

    private val mutablePrivacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = mutablePrivacyOptionsRequired.asStateFlow()

    /** Callbacks waiting for the in-flight gather; null when no gather is running. */
    private var waiting: MutableList<(ConsentState) -> Unit>? = null

    /** The most recent Activity passed to [gather], for the form step. Cleared when the gather finishes. */
    private var activityRef: WeakReference<Activity>? = null
    private var lastError: AdError? = null
    private var initRequested = false

    /** Identifies the current gather, so the callbacks of an abandoned one are ignored. */
    private var generation = 0

    /** The form timeout of the current gather ([clock] elapsed time), or null when it has none. */
    private var formDeadline: Long? = null
    private var cancelDeadline: (() -> Unit)? = null
    private var formShowing = false

    val isGathering: Boolean get() = waiting != null

    /** Test hook: whether an Activity reference is held. */
    internal val holdsActivity: Boolean get() = activityRef != null

    /**
     * Gathers consent. If a gather is already in flight (for example, after a rotation during the form), [onFinished]
     * joins it instead of starting another, and [activity] replaces the older Activity for the remaining steps.
     */
    fun gather(activity: Activity, onFinished: (ConsentState) -> Unit) = gather(activity, null, onFinished)

    /**
     * [gather] that gives the form at most [formTimeout] to appear (null: no limit). When it passes with no form on
     * screen, [onFinished] runs at once with the consent known then (usually the previous session's), and the form
     * isn't shown this launch.
     */
    fun gather(activity: Activity, formTimeout: Duration?, onFinished: (ConsentState) -> Unit) {
        activityRef = WeakReference(activity)
        waiting?.let {
            log.d { "Consent gathering already in progress; waiting for it" }
            it += onFinished
            return
        }
        waiting = mutableListOf(onFinished)
        initRequested = false
        val gen = ++generation
        formDeadline = formTimeout?.let { clock.elapsedRealtimeMillis() + it.inWholeMilliseconds }
        cancelDeadline = formTimeout?.let { timeout -> schedule(timeout) { onFormTimeout(gen, timeout) } }
        log.d { "Requesting consent information" }
        consentSdk.requestUpdate(activity, consentParams(config)) { onUpdated(gen, it) }
    }

    private fun onFormTimeout(gen: Int, timeout: Duration) {
        if (gen != generation || waiting == null || formShowing) return
        log.i { "No consent form within $timeout; continuing without it. It's tried again on the next launch" }
        finish()
    }

    private fun pastFormDeadline(): Boolean = formDeadline?.let { clock.elapsedRealtimeMillis() >= it } == true

    private fun onUpdated(gen: Int, result: ConsentUpdateResult) {
        if (gen != generation || waiting == null) {
            // This gather was abandoned at its form timeout; the update may still allow ads.
            log.d { "Consent information arrived after the gather ended" }
            reevaluate()
            return
        }
        when (result) {
            is ConsentUpdateResult.Success -> {
                lastError = null
                val current = publish()
                log.d { "Consent information updated: status=${current.status}, canRequestAds=${current.canRequestAds}" }
                if (current.canRequestAds) requestInit()
                val activity = activityRef?.get()?.takeUnless { it.isFinishing || it.isDestroyed }
                if (activity == null) {
                    log.w { "No usable Activity for the consent form; skipping it" }
                    finish()
                } else if (formDeadline == null) {
                    log.d { "Loading the consent form if required" }
                    consentSdk.loadAndShowFormIfRequired(activity, ::onFormDone)
                } else if (pastFormDeadline()) {
                    finish()
                } else {
                    log.d { "Loading the consent form if required, within the form timeout" }
                    consentSdk.loadFormIfRequired(activity) { form, error -> onFormLoaded(gen, form, error) }
                }
            }
            is ConsentUpdateResult.Failure -> {
                lastError = result.error
                log.w { "Requesting consent information failed: ${result.error}. Using the previous session's consent" }
                finish()
            }
        }
    }

    private fun onFormLoaded(gen: Int, form: ConsentForm?, error: AdError?) {
        if (gen != generation || waiting == null) {
            if (form != null) log.i { "The consent form loaded after the form timeout; not showing it" }
            return
        }
        val activity = activityRef?.get()?.takeUnless { it.isFinishing || it.isDestroyed }
        when {
            error != null -> onFormDone(error)
            form == null -> onFormDone(null)
            pastFormDeadline() -> {
                log.i { "The consent form loaded after the form timeout; not showing it" }
                finish()
            }
            activity == null -> {
                log.w { "No usable Activity for the consent form; skipping it" }
                finish()
            }
            else -> {
                formShowing = true
                cancelDeadline?.invoke()
                cancelDeadline = null
                form.show(activity) { showError ->
                    formShowing = false
                    if (gen == generation && waiting != null) onFormDone(showError)
                }
            }
        }
    }

    private fun onFormDone(error: AdError?) {
        if (error != null) {
            lastError = error
            log.w { "Consent form failed: $error" }
        } else {
            log.d { "Consent form step finished" }
        }
        finish()
    }

    private fun finish() {
        cancelDeadline?.invoke()
        cancelDeadline = null
        formDeadline = null
        activityRef = null
        val current = publish()
        log.d { "Consent gathering finished: status=${current.status}, canRequestAds=${current.canRequestAds}" }
        if (current.canRequestAds) requestInit()
        val callbacks = waiting.orEmpty()
        waiting = null
        callbacks.forEach { deliver(it, current) }
    }

    private fun requestInit() {
        if (initRequested || !consentSdk.canRequestAds()) return
        initRequested = true
        onCanRequestAds()
    }

    /**
     * Shows the privacy options form, then re-evaluates consent: newly allowed ads trigger [onCanRequestAds],
     * withdrawn consent triggers [onConsentRevoked]. [onDismissed] runs on the main thread, always posted.
     */
    fun showPrivacyOptions(activity: Activity, onDismissed: (AdError?) -> Unit) {
        if (!consentSdk.privacyOptionsRequired()) {
            log.i { "Privacy options aren't required; not showing the form" }
            postToMain { deliver(onDismissed, null) }
            return
        }
        log.d { "Showing the privacy options form" }
        consentSdk.showPrivacyOptionsForm(activity) { error ->
            if (error != null) {
                lastError = error
                log.w { "Privacy options form failed: $error" }
            }
            reevaluate()
            deliver(onDismissed, error)
        }
    }

    /** Clears UMP's consent state so the form shows again on the next gather. Callers check the environment. */
    fun resetConsent() {
        log.d { "Resetting consent" }
        consentSdk.reset()
        lastError = null
        reevaluate()
    }

    private fun reevaluate() {
        val before = mutableState.value.canRequestAds
        val current = publish()
        log.d { "Consent re-evaluated: status=${current.status}, canRequestAds=${current.canRequestAds}" }
        when {
            !before && current.canRequestAds -> {
                if (consentSdk.canRequestAds()) onCanRequestAds()
            }
            before && !current.canRequestAds -> onConsentRevoked()
        }
    }

    /** Reads UMP and publishes a new [ConsentState] if anything changed. */
    private fun publish(): ConsentState {
        val next = ConsentState(
            status = consentSdk.consentStatus(),
            canRequestAds = consentSdk.canRequestAds(),
            privacyOptionsRequired = consentSdk.privacyOptionsRequired(),
            lastError = lastError,
        )
        mutablePrivacyOptionsRequired.value = next.privacyOptionsRequired
        if (next != mutableState.value) {
            mutableState.value = next
            events.emit(AdEvent.ConsentUpdated(next, clock.currentTimeMillis()))
        }
        return next
    }

    private fun <T> deliver(callback: (T) -> Unit, value: T) {
        try {
            callback(value)
        } catch (e: Exception) {
            log.e(e) { "A consent callback threw" }
        }
    }

    companion object {
        /** UMP request parameters. Debug settings are never sent in PRODUCTION, even if validation was bypassed. */
        fun consentParams(config: EffectiveConfig): ConsentParams {
            val privacy = config.source.privacy
            val production = config.environment == AdEnvironment.PRODUCTION
            return ConsentParams(
                debugGeography = if (production) DebugGeography.DISABLED else privacy.debugGeography,
                // GMA test device IDs are also UMP's hashed device IDs; EffectiveConfig empties them in PRODUCTION.
                testDeviceHashedIds = if (production) emptyList() else config.testDeviceIds.toList(),
                tagUnderAgeOfConsent = privacy.ageTreatment == AgeTreatment.CHILD,
                consentSyncId = privacy.consentSyncId,
            )
        }
    }
}
