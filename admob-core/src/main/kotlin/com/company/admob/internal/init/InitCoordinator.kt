package com.company.admob.internal.init

import android.app.Activity
import android.content.Context
import com.company.admob.AdError
import com.company.admob.AdsState
import com.company.admob.ConsentState
import com.company.admob.UnavailableReason
import com.company.admob.events.AdEvent
import com.company.admob.internal.AdFreeController
import com.company.admob.internal.callbacks.OnceCallback
import com.company.admob.internal.config.ConfigChangeListener
import com.company.admob.internal.config.EffectiveConfig
import com.company.admob.internal.consent.ConsentCoordinator
import com.company.admob.internal.consent.RdpWriter
import com.company.admob.internal.events.EventBus
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.port.AdsSdk
import com.company.admob.internal.port.ConsentSdk
import com.company.admob.internal.port.SdkInitResult
import com.company.admob.internal.port.SdkRequestConfig
import com.company.admob.internal.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Owns [AdsState] (architecture §11): runs `start()` through consent and SDK initialization, and re-evaluates the
 * state when the ad-free flag, consent, or the remote `adsEnabled` override changes. Precedence (§21): ad-free, then
 * remote disable, then consent. Every transition goes through [AdsStateTransitions].
 *
 * The SDK is initialized at most once at a time ([initStarted]); after a failure, a later `start()` retries, at
 * least [RETRY_INTERVAL] after the previous attempt began. `gad_rdp` is written before each attempt.
 *
 * Main thread only, except [state], [canLoad] and the suspend functions, which are thread-safe.
 */
internal class InitCoordinator(
    private val mutableState: MutableStateFlow<AdsState>,
    private val appContext: Context,
    private val appId: String?,
    private val config: EffectiveConfig,
    private val adsSdk: AdsSdk,
    private val consentSdk: ConsentSdk,
    private val rdpWriter: RdpWriter,
    private val adFree: AdFreeController,
    private val clock: Clock,
    private val events: EventBus,
    private val log: AdLog,
) : Readiness, ConfigChangeListener {
    override val state: StateFlow<AdsState> = mutableState.asStateFlow()

    val consent: ConsentCoordinator = ConsentCoordinator(
        consentSdk = consentSdk,
        config = config,
        log = log,
        events = events,
        clock = clock,
        onCanRequestAds = ::initSdkOnce,
        onConsentRevoked = ::onConsentRevoked,
    )

    /** Set while SDK initialization is in flight or done; cleared after a failure so `start()` can retry. */
    private val initStarted = AtomicBoolean(false)
    private var sdkInitialized = false
    private var lastInitAttemptMillis: Long? = null

    /**
     * Every ad is turned off: by the remote configuration (`AdOverrides.adsEnabled = false`), or from the start by the
     * ads file (`settings.adsEnabled = false`), until a remote override turns ads on.
     */
    private var remoteDisabled = !config.adsEnabled

    /** `start()` callbacks waiting for the state to settle. */
    private val waiters = mutableListOf<OnceCallback<AdsState>>()

    val isSdkInitialized: Boolean get() = sdkInitialized

    override fun canLoad(): Boolean = state.value == AdsState.Ready

    override suspend fun awaitReady() {
        state.first { it == AdsState.Ready }
    }

    /** The public `awaitReady`: true once [AdsState.Ready]; false on a settled non-Ready state or on [timeout]. */
    suspend fun awaitSettled(timeout: Duration): Boolean {
        if (state.value == AdsState.Ready) return true
        val settled = withTimeoutOrNull(timeout) {
            state.first { it == AdsState.Ready || it is AdsState.Unavailable || it is AdsState.Failed }
        }
        return settled == AdsState.Ready
    }

    fun onConfigValid() = transition(AdsState.AwaitingStart)

    fun onConfigInvalid(error: AdError.InvalidConfiguration) = transition(AdsState.Failed(error))

    fun start(activity: Activity, onComplete: ((AdsState) -> Unit)?) {
        val callback = onComplete?.let { OnceCallback(it) }
        // A rotation during consent: hand the newer Activity to the in-flight gather for the form step.
        if (consent.isGathering) consent.gather(activity) {}

        when (val current = state.value) {
            AdsState.NotInitialized -> {
                log.e { "start() called before initialize()" }
                callback?.invoke(current)
            }
            AdsState.AwaitingStart -> {
                callback?.let(waiters::add)
                gather(activity)
            }
            AdsState.GatheringConsent, AdsState.InitializingSdk -> {
                log.d { "start() joined the start in progress ($current)" }
                callback?.let(waiters::add)
            }
            AdsState.Ready -> callback?.invoke(current)
            is AdsState.Unavailable ->
                if (current.reason == UnavailableReason.CONSENT_NOT_OBTAINED) {
                    callback?.let(waiters::add)
                    gather(activity)
                } else {
                    callback?.invoke(current)
                }
            is AdsState.Failed -> {
                if (current.error is AdError.SdkInitFailed && retryAllowed()) {
                    log.i { "Retrying SDK initialization" }
                    callback?.let(waiters::add)
                    if (consentSdk.canRequestAds()) initSdkOnce() else gather(activity)
                } else {
                    log.i { "start(): staying $current" }
                    callback?.invoke(current)
                }
            }
        }
        settleIfDone()
    }

    fun setAdFree(value: Boolean) {
        if (!adFree.setAdFree(value)) return
        log.i { "Ad-free: $value" }
        if (value) adFree.notifyBlocked(UnavailableReason.AD_FREE_USER)
        reevaluate()
    }

    private fun gather(activity: Activity) {
        transition(AdsState.GatheringConsent)
        consent.gather(activity, ::onGatherFinished)
    }

    private fun onGatherFinished(consentState: ConsentState) {
        when (state.value) {
            // Consent didn't allow SDK init.
            AdsState.GatheringConsent -> transition(AdsState.Unavailable(blockedReason()))
            // The form withdrew previous-session consent after an early init.
            AdsState.Ready -> if (!consentState.canRequestAds) {
                transition(AdsState.Unavailable(UnavailableReason.CONSENT_NOT_OBTAINED))
                adFree.notifyBlocked(UnavailableReason.CONSENT_NOT_OBTAINED)
            }
            else -> Unit
        }
        settleIfDone()
    }

    /** A remote `adsEnabled` change: off blocks ads like ad-free does (with its own reason); on re-evaluates. */
    override fun onConfigChanged(previous: EffectiveConfig, current: EffectiveConfig) {
        val disabled = !current.adsEnabled
        if (disabled == remoteDisabled) return
        remoteDisabled = disabled
        log.i { "Remote configuration: ads ${if (disabled) "disabled" else "enabled again"}" }
        if (disabled) adFree.notifyBlocked(UnavailableReason.DISABLED_BY_REMOTE)
        reevaluate()
    }

    /** Called by [consent] only while UMP allows ads. */
    private fun initSdkOnce() {
        if (!consentSdk.canRequestAds()) return
        if (sdkInitialized) {
            reevaluate()
            return
        }
        if (!initStarted.compareAndSet(false, true)) return
        val id = appId
        if (id == null) {
            // Unreachable with a validated config: validation requires a manifest app ID.
            initStarted.set(false)
            transition(AdsState.Failed(AdError.SdkInitFailed()))
            return
        }
        lastInitAttemptMillis = clock.elapsedRealtimeMillis()
        if (state.value != AdsState.Unavailable(UnavailableReason.AD_FREE_USER)) transition(AdsState.InitializingSdk)
        val source = config.source
        val restrictedDataProcessing = source.privacy.restrictedDataProcessing
        log.d { "Initializing the Google Mobile Ads SDK" }
        adsSdk.initialize(
            appContext = appContext,
            appId = id,
            request = SdkRequestConfig(
                testDeviceIds = config.testDeviceIds.toList(),
                ageTreatment = source.privacy.ageTreatment,
                maxAdContentRating = source.privacy.maxAdContentRating,
            ),
            disableCrashReporting = source.advanced.disableSdkCrashReporting,
            // Off the main thread: RdpWriter commits to disk, and the SDK reads the value when it starts.
            beforeInit = { rdpWriter.apply(restrictedDataProcessing) },
            onComplete = ::onSdkInitResult,
        )
    }

    private fun onSdkInitResult(result: SdkInitResult) {
        when (result) {
            SdkInitResult.Success -> {
                sdkInitialized = true
                log.i { "Google Mobile Ads SDK initialized" }
                transition(
                    when {
                        adFree.isAdFree -> AdsState.Unavailable(UnavailableReason.AD_FREE_USER)
                        remoteDisabled -> AdsState.Unavailable(UnavailableReason.DISABLED_BY_REMOTE)
                        !consentSdk.canRequestAds() -> AdsState.Unavailable(UnavailableReason.CONSENT_NOT_OBTAINED)
                        else -> AdsState.Ready
                    },
                )
            }
            is SdkInitResult.Failure -> {
                initStarted.set(false)
                log.e { "Google Mobile Ads SDK initialization failed: ${result.error}. start() retries it later" }
                transition(AdsState.Failed(result.error))
            }
        }
        settleIfDone()
    }

    private fun onConsentRevoked() {
        adFree.notifyBlocked(UnavailableReason.CONSENT_NOT_OBTAINED)
        if (state.value == AdsState.Ready) transition(AdsState.Unavailable(UnavailableReason.CONSENT_NOT_OBTAINED))
    }

    /** After an ad-free or consent change. In-flight and failed states settle on their own. */
    private fun reevaluate() {
        val current = state.value
        if (current != AdsState.Ready && current !is AdsState.Unavailable) return
        when {
            adFree.isAdFree -> transition(AdsState.Unavailable(UnavailableReason.AD_FREE_USER))
            remoteDisabled -> transition(AdsState.Unavailable(UnavailableReason.DISABLED_BY_REMOTE))
            !consentSdk.canRequestAds() -> transition(AdsState.Unavailable(UnavailableReason.CONSENT_NOT_OBTAINED))
            sdkInitialized -> transition(AdsState.Ready)
            else -> initSdkOnce()
        }
    }

    private fun blockedReason(): UnavailableReason = when {
        adFree.isAdFree -> UnavailableReason.AD_FREE_USER
        remoteDisabled -> UnavailableReason.DISABLED_BY_REMOTE
        else -> UnavailableReason.CONSENT_NOT_OBTAINED
    }

    private fun retryAllowed(): Boolean {
        val last = lastInitAttemptMillis ?: return true
        return clock.elapsedRealtimeMillis() - last >= RETRY_INTERVAL.inWholeMilliseconds
    }

    /** Delivers the settled state to every waiting `start()` once consent and SDK init are both done. */
    private fun settleIfDone() {
        if (waiters.isEmpty() || consent.isGathering || (initStarted.get() && !sdkInitialized)) return
        val settled = state.value
        val pending = waiters.toList()
        waiters.clear()
        pending.forEach { it(settled) }
    }

    private fun transition(to: AdsState) {
        val from = state.value
        if (from == to) return
        if (!AdsStateTransitions.isAllowed(from, to)) {
            log.e { "Illegal state transition $from -> $to; ignored" }
            return
        }
        mutableState.value = to
        log.i { "State: $from -> $to" }
        events.emit(AdEvent.StateChanged(to, clock.currentTimeMillis()))
    }

    companion object {
        val RETRY_INTERVAL: Duration = 30.seconds
    }
}

/**
 * Every allowed [AdsState] transition: the diagram in architecture §11.1, plus the transitions marked "extension",
 * which the diagram leaves implicit. [InitCoordinator] refuses anything else.
 */
internal object AdsStateTransitions {
    enum class Kind { NOT_INITIALIZED, AWAITING_START, GATHERING_CONSENT, INITIALIZING_SDK, READY, UNAVAILABLE, FAILED }

    fun kindOf(state: AdsState): Kind = when (state) {
        AdsState.NotInitialized -> Kind.NOT_INITIALIZED
        AdsState.AwaitingStart -> Kind.AWAITING_START
        AdsState.GatheringConsent -> Kind.GATHERING_CONSENT
        AdsState.InitializingSdk -> Kind.INITIALIZING_SDK
        AdsState.Ready -> Kind.READY
        is AdsState.Unavailable -> Kind.UNAVAILABLE
        is AdsState.Failed -> Kind.FAILED
    }

    val allowed: Map<Kind, Set<Kind>> = mapOf(
        Kind.NOT_INITIALIZED to setOf(Kind.AWAITING_START, Kind.FAILED),
        Kind.AWAITING_START to setOf(Kind.GATHERING_CONSENT),
        Kind.GATHERING_CONSENT to setOf(Kind.INITIALIZING_SDK, Kind.UNAVAILABLE),
        // Extension: UNAVAILABLE when ad-free or consent revoked during init.
        Kind.INITIALIZING_SDK to setOf(Kind.READY, Kind.FAILED, Kind.UNAVAILABLE),
        Kind.READY to setOf(Kind.UNAVAILABLE),
        // Extensions: INITIALIZING_SDK when consent is granted later through privacy options (or ad-free is lifted)
        // before the SDK was initialized; UNAVAILABLE when only the reason changes.
        Kind.UNAVAILABLE to setOf(Kind.GATHERING_CONSENT, Kind.READY, Kind.INITIALIZING_SDK, Kind.UNAVAILABLE),
        // Extension: GATHERING_CONSENT when a retry finds that UMP no longer allows ads.
        Kind.FAILED to setOf(Kind.INITIALIZING_SDK, Kind.GATHERING_CONSENT),
    )

    fun isAllowed(from: AdsState, to: AdsState): Boolean {
        if (from is AdsState.Failed && from.error !is AdError.SdkInitFailed) return false
        if (from == AdsState.NotInitialized && to is AdsState.Failed) return to.error is AdError.InvalidConfiguration
        return kindOf(to) in allowed.getValue(kindOf(from))
    }
}
