package io.github.thesobiaakbar.adroit.splash

import android.app.Activity
import android.app.Application
import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.AdFormat
import io.github.thesobiaakbar.adroit.AdsState
import io.github.thesobiaakbar.adroit.Adroit
import io.github.thesobiaakbar.adroit.FullScreenResult
import io.github.thesobiaakbar.adroit.config.AdDefinition
import io.github.thesobiaakbar.adroit.config.SplashTimings
import io.github.thesobiaakbar.adroit.internal.threading.MainDispatcher
import io.github.thesobiaakbar.adroit.logging.LogLevel
import io.github.thesobiaakbar.adroit.network.NetworkMonitor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A splash screen's whole ads sequence (docs/SPLASH.md), which never gets stuck:
 * 1. [SplashState.Preparing]: consent (UMP), for at most `consentTimeoutSeconds` for the form to appear; after that,
 *    the form is dropped for this launch. A form that appeared stays until the user answers, and nothing else starts
 *    meanwhile. Until the user answers, every splash asks again. Then the library starts (`Adroit.start`),
 *    whatever the answer. Skipped for a premium user.
 * 2. [SplashState.Loading]: the splash timer, with a [SplashState.Loading.progress] from 0 to 100 for a progress bar.
 *    It lasts `splashSeconds` (Firebase `AdSettings`, read when the timer starts), `premiumSplashSeconds` for a premium
 *    user or `offlineSplashSeconds` without internet. Meanwhile, the splash ad loads (not for premium or offline
 *    users). When it's ready and the screen is in front, progress jumps to 100 and the timer stops.
 * 3. [SplashState.ShowingAd] while the ad is on screen: the splash waits for the user to close it.
 * 4. [SplashState.Finished]: go to the next screen. It's reached when the timer ends (an ad that loads later isn't
 *    shown on the splash), or when the ad is closed or fails to show.
 *
 * Firebase Remote Config isn't waited for: the timer and the ad use the values active when they start (see
 * `RemoteConfigSession`), except that a fetch during the timer that swaps the ad's format requests it in the new one.
 *
 * Keep it in a ViewModel, so a rotation doesn't restart it: call [start] from every `onCreate` (it hands the new
 * Activity to the running flow), collect [state] while the screen is started, and [cancel] in `onCleared`. Main
 * thread only.
 */
public class SplashFlow internal constructor(
    private val splashAdKey: String?,
    private val isPremium: Boolean,
    private val ports: SplashPorts,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<SplashState>(SplashState.Preparing)
    private var activityRef: WeakReference<Activity>? = null
    private var job: Job? = null
    private val adsStarted = CompletableDeferred<Unit>()

    /** Where the splash is. [SplashState.Finished] is final: navigate on it, from any Activity that collects it. */
    public val state: StateFlow<SplashState> = mutableState.asStateFlow()

    /** Starts the flow for [activity], or, after a rotation, hands the running flow the new Activity. Main thread. */
    public fun start(activity: Activity) {
        activityRef = WeakReference(activity)
        if (job != null) {
            // Recreated during consent: the new Activity takes over the form, which is shown again if it was lost.
            if (mutableState.value == SplashState.Preparing && !isPremium && !adsStarted.isCompleted) startAds(activity)
            return
        }
        job = scope.launch { run() }
    }

    /** Stops the flow, for example in `ViewModel.onCleared`. An ad already on screen isn't closed. */
    public fun cancel() {
        scope.cancel()
    }

    private val activity: Activity? get() = activityRef?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    private suspend fun run() {
        if (!isPremium) {
            val current = activity
            if (current == null) adsStarted.complete(Unit) else startAds(current)
            // The consent step has its own form timeout. This guard bounds the rest (an update request or an SDK start
            // that never answers), but never runs while the form is on screen: the timer starts after the answer.
            val limit = ports.timings().consentTimeout + CONSENT_GUARD
            var waited = Duration.ZERO
            while (!adsStarted.isCompleted && waited < limit) {
                withTimeoutOrNull(TICK) { adsStarted.await() }
                waited = if (ports.consentFormShowing()) Duration.ZERO else waited + TICK
            }
        }
        // Read now, not before consent: a Remote Config fetch may have finished meanwhile.
        val timings = ports.timings()
        val online = ports.isOnline()
        val duration = when {
            isPremium -> timings.premiumSplash
            !online -> timings.offlineSplash
            else -> timings.splash
        }
        var wantsAd = !isPremium && online && splashAdKey != null && ports.requestAd(splashAdKey)
        var requestedFormat = if (wantsAd) ports.servingFormat(checkNotNull(splashAdKey)) else null
        ports.log("Splash timer: $duration${if (wantsAd) ", waiting for the splash ad" else ""}")
        var elapsed = Duration.ZERO
        var adReady = false
        while (elapsed < duration) {
            mutableState.value = SplashState.Loading(progressOf(elapsed, duration))
            if (wantsAd && ports.adsUnavailable()) wantsAd = false
            // A Firebase activation during the timer can swap the ad's format, which releases the ad requested before:
            // request it in its new format, or none would be ready until the timer ends.
            if (wantsAd) {
                val format = ports.servingFormat(checkNotNull(splashAdKey))
                if (format != requestedFormat) {
                    requestedFormat = format
                    wantsAd = ports.requestAd(splashAdKey)
                }
            }
            val current = activity
            if (wantsAd && current != null && ports.isResumed(current) && ports.isAdReady(checkNotNull(splashAdKey))) {
                adReady = true
                break
            }
            delay(TICK)
            elapsed += TICK
        }
        mutableState.value = SplashState.Loading(100)
        val current = activity
        if (!adReady || current == null) {
            // An ad that loads after the splash would never be shown: stop its load and retries.
            if (splashAdKey != null && !isPremium) ports.abandonAd(splashAdKey)
            return finish(adShown = false)
        }
        mutableState.value = SplashState.ShowingAd
        val result = suspendCancellableCoroutine { continuation ->
            ports.showAd(current, checkNotNull(splashAdKey)) { if (continuation.isActive) continuation.resume(it) }
        }
        finish(adShown = result is FullScreenResult.Dismissed)
    }

    private fun startAds(activity: Activity) {
        ports.startAds(activity, ports.timings().consentTimeout) { adsStarted.complete(Unit) }
    }

    private fun finish(adShown: Boolean) {
        ports.log("Splash finished (ad shown: $adShown)")
        mutableState.value = SplashState.Finished(adShown)
    }

    /** Creating a flow. */
    public companion object {
        /** How often the progress updates. */
        internal val TICK: Duration = 100.milliseconds

        /** Added to the consent form timeout to bound a consent update that never answers. */
        internal val CONSENT_GUARD: Duration = 2.seconds

        /**
         * A flow for [application]'s splash screen, after `Adroit.initialize`.
         *
         * @param splashAd the splash ad (an interstitial, or an app open ad through a Firebase swap); null for none.
         * @param isPremium the user's premium (ad-free) status: no consent form, no ad, the premium timer. Also call
         *   `Adroit.setAdFree`.
         */
        @JvmStatic
        @JvmOverloads
        public fun create(application: Application, splashAd: AdDefinition?, isPremium: Boolean = false): SplashFlow =
            SplashFlow(
                splashAd?.name,
                isPremium,
                RuntimeSplashPorts(NetworkMonitor.getInstance(application)),
                CoroutineScope(SupervisorJob() + MainDispatcher),
            )

        internal fun progressOf(elapsed: Duration, total: Duration): Int =
            if (!total.isPositive()) 100 else ((elapsed / total) * 100).toInt().coerceIn(0, 100)
    }
}

/** Where a splash is, as [SplashFlow.state]. */
public sealed class SplashState {
    /** Consent and the library's start are being handled: keep the progress bar hidden. */
    public data object Preparing : SplashState()

    /**
     * The splash timer runs.
     *
     * @property progress the share of the timer elapsed, 0 to 100; 100 as soon as the splash ad is ready.
     */
    public class Loading(public val progress: Int) : SplashState() {
        override fun equals(other: Any?): Boolean = other is Loading && other.progress == progress

        override fun hashCode(): Int = progress

        override fun toString(): String = "Loading($progress%)"
    }

    /** The splash ad is on screen; the splash continues when the user closes it. */
    public data object ShowingAd : SplashState()

    /**
     * Go to the next screen.
     *
     * @property adShown whether a splash ad was shown and closed.
     */
    public class Finished(public val adShown: Boolean) : SplashState() {
        override fun equals(other: Any?): Boolean = other is Finished && other.adShown == adShown

        override fun hashCode(): Int = adShown.hashCode()

        override fun toString(): String = "Finished(adShown=$adShown)"
    }
}

/** What [SplashFlow] needs from the library, behind a seam for tests. Main thread. */
internal interface SplashPorts {
    fun timings(): SplashTimings

    fun isOnline(): Boolean

    /** Consent (at most [consentTimeout] for the form to appear) and the library's start; [onDone] once, when settled. */
    fun startAds(activity: Activity, consentTimeout: Duration, onDone: () -> Unit)

    /** A consent form is on screen, waiting for the user's answer. */
    fun consentFormShowing(): Boolean

    /** Starts loading the ad [key] in its current format. False when it can't be requested (unknown, off). */
    fun requestAd(key: String): Boolean

    /** The format [key] is served in now: a Firebase swap can change it while the splash runs. */
    fun servingFormat(key: String): AdFormat?

    fun isAdReady(key: String): Boolean

    /** Ads won't become available this launch (no consent, ad-free, remote off, failed). */
    fun adsUnavailable(): Boolean

    fun isResumed(activity: Activity): Boolean

    fun showAd(activity: Activity, key: String, onResult: (FullScreenResult) -> Unit)

    /** The splash is over without showing the ad [key]: drop its load, retries and cached ad, unless shared. */
    fun abandonAd(key: String)

    fun log(message: String)
}

/** [SplashPorts] over the initialized library. */
internal class RuntimeSplashPorts(private val network: NetworkMonitor) : SplashPorts {
    private val runtime get() = Adroit.liveRuntime

    override fun timings(): SplashTimings = runtime?.liveConfig?.current?.splashTimings ?: SplashTimings()

    override fun isOnline(): Boolean = network.isOnline

    override fun startAds(activity: Activity, consentTimeout: Duration, onDone: () -> Unit) {
        val runtime = runtime
        if (runtime == null) {
            // Not initialized, or a test client stands in: the public start reports it.
            Adroit.start(activity) { onDone() }
            return
        }
        runtime.init.start(activity, { onDone() }, consentTimeout)
    }

    override fun consentFormShowing(): Boolean = runtime?.init?.consent?.isFormShowing == true

    private fun serving(key: String) = runtime?.let { rt ->
        rt.liveConfig.current.active(key)?.let { p ->
            val engine = when (p.format) {
                AdFormat.INTERSTITIAL -> rt.interstitialEngine
                AdFormat.APP_OPEN -> rt.appOpenEngine
                else -> null
            }
            engine?.let { p to it }
        }
    }

    override fun requestAd(key: String): Boolean {
        val (p, engine) = serving(key) ?: return false
        if (!p.enabled) return false
        engine.preload(p.placement)
        return true
    }

    override fun servingFormat(key: String): AdFormat? = serving(key)?.first?.format

    override fun isAdReady(key: String): Boolean = serving(key)?.let { (p, engine) -> engine.isReady(p.placement) } == true

    override fun adsUnavailable(): Boolean {
        val state = runtime?.init?.state?.value ?: return true
        return state is AdsState.Unavailable || state is AdsState.Failed
    }

    override fun isResumed(activity: Activity): Boolean =
        runtime?.deps?.activityTracker?.isValidForShow(activity) == true

    override fun abandonAd(key: String) {
        // Both formats: a swap may have changed it since the request.
        val runtime = runtime ?: return
        runtime.liveConfig.current.allPlacements.filter { it.placement.key == key }.forEach { p ->
            when (p.format) {
                AdFormat.INTERSTITIAL -> runtime.interstitialEngine.abandon(p.placement)
                AdFormat.APP_OPEN -> runtime.appOpenEngine.abandon(p.placement)
                else -> Unit
            }
        }
    }

    override fun showAd(activity: Activity, key: String, onResult: (FullScreenResult) -> Unit) {
        val (p, engine) = serving(key) ?: return onResult(FullScreenResult.NotShown(AdError.NotReady))
        engine.show(activity, p.placement, onResult = onResult)
    }

    override fun log(message: String) {
        Adroit.log(LogLevel.INFO, message)
    }
}
