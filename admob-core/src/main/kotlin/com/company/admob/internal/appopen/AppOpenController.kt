package com.company.admob.internal.appopen

import android.app.Activity
import androidx.lifecycle.LifecycleOwner
import com.company.admob.AdAvailability
import com.company.admob.AdError
import com.company.admob.AdFormat
import com.company.admob.AdsState
import com.company.admob.FullScreenResult
import com.company.admob.UnavailableReason
import com.company.admob.config.AppOpenPlacementConfig
import com.company.admob.config.PreloadStrategy
import com.company.admob.internal.AdsBlockedListener
import com.company.admob.internal.callbacks.OnceCallback
import com.company.admob.internal.config.LiveConfig
import com.company.admob.internal.config.EffectivePlacement
import com.company.admob.internal.fullscreen.FullScreenAdEngine
import com.company.admob.internal.fullscreen.toShowError
import com.company.admob.internal.init.Readiness
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.port.ActivityTracker
import com.company.admob.internal.port.AppForegroundSource
import com.company.admob.internal.port.ForegroundEvent
import com.company.admob.internal.reliability.FrequencyDecision
import com.company.admob.internal.reliability.FrequencyGate
import com.company.admob.internal.reliability.FullScreenCoordinator
import com.company.admob.internal.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Application-scoped app open ads (architecture §15). A warm foreground runs the gates in order and shows the cached
 * ad only if every gate passes; the first failing gate is logged and a preload is ensured for next time. An ad that
 * finishes loading after the foreground event is never shown for it, which is what prevents loops.
 *
 * The process's first ON_START (cold start) is ignored here: only [showOnColdStart], called from a loading screen,
 * shows on a cold start. Cache, load, expiry and show are delegated to the app open [engine], or to
 * [interstitialEngine] while a remote value swaps the app open ad to an interstitial (docs/AD_PROFILES.md): the same
 * gates apply, and the app open ad's own settings (exclusions, launch count) still decide when.
 *
 * Main thread only (ADR-006). No Activity is referenced outside a show or a [showOnColdStart] wait, and suppressors
 * are released on their owner's ON_DESTROY.
 */
internal class AppOpenController(
    private val engine: FullScreenAdEngine,
    private val interstitialEngine: FullScreenAdEngine? = null,
    private val config: LiveConfig,
    private val readiness: Readiness,
    private val activityTracker: ActivityTracker,
    private val foreground: AppForegroundSource,
    private val frequency: FrequencyGate,
    private val coordinator: FullScreenCoordinator,
    private val suppression: AppOpenSuppression,
    private val launchCount: () -> Int,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val log: AdLog,
) : AdsBlockedListener {
    /** Why the gates held an ad back: [error] for [showOnColdStart] results, [reason] for the log. */
    private class Blocked(val error: AdError, val reason: String)

    /** A [showOnColdStart] call, from the call until its result is delivered. */
    private class ColdStart(val callback: OnceCallback<FullScreenResult>) {
        var job: Job? = null
        var showing = false
    }

    /** The app's app open placement (never a swap alternate): read at every decision, never kept. */
    private val configured: EffectivePlacement?
        get() = config.current.allPlacements.firstOrNull { it.format == AdFormat.APP_OPEN && !it.isAlternate }

    /** The placement serving app open moments now: [configured], or its interstitial alternate while swapped. */
    private val placement: EffectivePlacement?
        get() = configured?.let { config.current.active(it.placement.key) }?.takeIf { engineFor(it) != null }
            ?: configured

    /** Local-only settings of the app open ad (overrides don't change them), also while swapped. */
    private val settings: AppOpenPlacementConfig? get() = configured?.config as? AppOpenPlacementConfig
    private val noPlacement: StateFlow<AdAvailability> = MutableStateFlow(AdAvailability.Disabled).asStateFlow()
    private var enabled = true
    private var warmCheck: Job? = null
    private var coldStart: ColdStart? = null
    private var lastShownAtElapsed: Long? = null

    init {
        val appOpen = config.current.allPlacements.filter { it.format == AdFormat.APP_OPEN && !it.isAlternate }
        if (appOpen.size > 1) {
            log.w { "${appOpen.size} app open placements are configured; only ${appOpen.first().placement.key} is used" }
        }
    }

    fun start() {
        scope.launch {
            foreground.foregroundEvents.collect(::onForeground)
        }
    }

    fun availability(): StateFlow<AdAvailability> =
        placement?.let { p -> engineFor(p)?.availability(p.placement) } ?: noPlacement

    /** The engine that loads and shows [p]: app open, or interstitial for a swapped app open ad. */
    private fun engineFor(p: EffectivePlacement): FullScreenAdEngine? =
        if (p.format == AdFormat.INTERSTITIAL) interstitialEngine else engine

    fun skipNextForeground() = suppression.skipNextForeground()

    fun suppressWhile(owner: LifecycleOwner) = suppression.suppressWhile(owner)

    /** Off stops automatic shows and ends a pending [showOnColdStart] wait; the cached ad is kept either way. */
    fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) return
        this.enabled = enabled
        log.d { "App open ${if (enabled) "enabled" else "disabled"}" }
        if (!enabled) {
            warmCheck?.cancel()
            val pending = coldStart
            if (pending != null && !pending.showing) {
                pending.job?.cancel()
                finish(pending, FullScreenResult.NotShown(AdError.Disabled(UnavailableReason.DISABLED_BY_CONFIG)))
            }
        }
    }

    /** A pending [showOnColdStart] wait sees the readiness change itself; only the warm check is cancelled here. */
    override fun onAdsBlocked(reason: UnavailableReason) {
        warmCheck?.cancel()
    }

    /**
     * Waits up to [maxWait] for [AdsState.Ready], a loaded ad and a resumed [activity], then applies the gates and
     * shows. [onComplete] gets exactly one result, posted to the main thread. On timeout the ad keeps loading and is
     * kept for later. A second call while one is pending or showing completes at once with
     * [AdError.AlreadyShowing]; the first call is unaffected.
     */
    fun showOnColdStart(activity: Activity, maxWait: Duration, onComplete: (FullScreenResult) -> Unit) {
        val callback = OnceCallback(onComplete)
        val current = placement
        val staticBlock = when {
            coldStart != null -> Blocked(AdError.AlreadyShowing, "showOnColdStart() is already in progress")
            current == null -> noPlacementBlock()
            !current.enabled || settings?.showOnColdStartAllowed == false || !enabled ->
                Blocked(AdError.Disabled(current.disabledReason ?: UnavailableReason.DISABLED_BY_CONFIG), "app open on cold start is disabled")
            else -> launchCountGate()
        }
        if (staticBlock != null) {
            log.i { "App open on cold start not shown: ${staticBlock.reason}" }
            callback(FullScreenResult.NotShown(staticBlock.error))
            return
        }
        val p = checkNotNull(placement)
        val request = ColdStart(callback)
        coldStart = request
        // Runs now if Ready; otherwise kept as intent and loaded as soon as the library becomes ready.
        engineFor(p)?.preload(p.placement)
        request.job = scope.launch {
            val blocked = awaitColdStartAd(activity, p, maxWait) ?: showGates(activity, fromColdStart = true)
            if (blocked != null) {
                log.i { "App open on cold start not shown: ${blocked.reason}" }
                finish(request, FullScreenResult.NotShown(blocked.error))
                return@launch
            }
            request.showing = true
            show(activity, p) { finish(request, it) }
        }
    }

    private fun finish(request: ColdStart, result: FullScreenResult) {
        if (coldStart === request) coldStart = null
        request.callback(result)
    }

    private fun onForeground(event: ForegroundEvent) {
        if (event.isColdStart) {
            log.d { "App open: cold start, not shown automatically (see showOnColdStart)" }
            return
        }
        // Always consumed, so a stale request can't skip some later, unrelated return to the app.
        val skipped = suppression.consumeSkip()
        warmCheck?.cancel()
        stateGates(skipped, fromColdStart = false)?.let { return notShown(it) }
        warmCheck = scope.launch {
            // ON_START can arrive before the Activity's onResume.
            val activity = awaitResumedActivity()
            val blocked = when {
                !foreground.isInForeground -> Blocked(AdError.ActivityUnavailable, "the app left the foreground")
                else -> showGates(activity, fromColdStart = false)
            }
            if (blocked != null) return@launch notShown(blocked)
            show(checkNotNull(activity), checkNotNull(placement)) { log.d { "App open result: $it" } }
        }
    }

    private fun notShown(blocked: Blocked) {
        log.i { "App open not shown: ${blocked.reason}" }
        ensurePreload()
    }

    private fun show(activity: Activity, p: EffectivePlacement, onResult: (FullScreenResult) -> Unit) {
        val startedAt = clock.elapsedRealtimeMillis()
        checkNotNull(engineFor(p)).show(activity, p.placement, mayWait = false) { result ->
            if (result is FullScreenResult.Dismissed) lastShownAtElapsed = startedAt
            onResult(result)
        }
    }

    /** Gates 1–5, which need no Activity. */
    private fun stateGates(skipped: Boolean, fromColdStart: Boolean): Blocked? {
        // Gate 1: ready (which also means not ad-free), configured, enabled.
        readiness.state.value.toShowError()?.let { return Blocked(it, "ads aren't ready (${readiness.state.value})") }
        val p = placement ?: return noPlacementBlock()
        if (!p.enabled || !enabled) {
            return Blocked(AdError.Disabled(p.disabledReason ?: UnavailableReason.DISABLED_BY_CONFIG), "app open is disabled")
        }
        // Gate 2: never on the first launches.
        launchCountGate()?.let { return it }
        // Gate 3: the host is about to leave the app on purpose (billing, share sheet, sign-in...).
        if (skipped) return Blocked(AdError.ActivityUnavailable, "skipNextForeground() was requested")
        // Gate 4: a screen asked not to be covered.
        if (suppression.isSuppressed) return Blocked(AdError.ActivityUnavailable, "suppressed by suppressWhile()")
        // Gate 5: no other full-screen ad showing, or just ended or clicked (for example, a click-through return).
        if (coordinator.isShowing) {
            return Blocked(AdError.AlreadyShowing, "another full-screen ad is showing (${coordinator.currentOwner})")
        }
        if (!fromColdStart && coldStart != null) {
            return Blocked(AdError.AlreadyShowing, "a showOnColdStart() call is in progress")
        }
        val window = settings?.suppressAfterFullScreenAd ?: Duration.ZERO
        suppression.afterFullScreenRemaining(coordinator, window)?.let {
            return Blocked(AdError.FrequencyCapped(it), "another full-screen ad ended or was clicked $it too recently")
        }
        return null
    }

    /** Gates 1–8 for showing over [activity]. */
    private fun showGates(activity: Activity?, fromColdStart: Boolean): Blocked? {
        stateGates(skipped = false, fromColdStart = fromColdStart)?.let { return it }
        val p = checkNotNull(placement)
        // Gate 6: a resumed Activity that isn't excluded. showOnColdStart's Activity was chosen by the host, so the
        // exclusion list (usually naming that very splash screen) applies only to the automatic path.
        if (activity == null) return Blocked(AdError.ActivityUnavailable, "no Activity resumed in time")
        if (!activityTracker.isValidForShow(activity)) {
            return Blocked(AdError.ActivityUnavailable, "${activity.javaClass.simpleName} isn't resumed")
        }
        if (!fromColdStart && activity.javaClass.name in settings?.excludedActivityClassNames.orEmpty()) {
            return Blocked(AdError.ActivityUnavailable, "${activity.javaClass.name} is excluded")
        }
        // Gate 7: cooldown and session cap, plus a hard rule that two app open ads never show within the cooldown,
        // even when no impression was recorded.
        (frequency.check(p.frequencyPlacement) as? FrequencyDecision.Capped)?.let {
            return Blocked(AdError.FrequencyCapped(it.retryAfter), "cooldown or session cap, retry after ${it.retryAfter}")
        }
        lastShownAtElapsed?.let { last ->
            val remaining = p.frequency.minInterval - (clock.elapsedRealtimeMillis() - last).milliseconds
            if (remaining.isPositive()) return Blocked(AdError.FrequencyCapped(remaining), "cooldown, $remaining left")
        }
        // Gate 8: a fresh cached ad. Checking availability drops an expired ad, which starts its reload.
        val serving = checkNotNull(engineFor(p))
        serving.availability(p.placement)
        if (!serving.isReady(p.placement)) return Blocked(AdError.NotReady, "no fresh ad is cached")
        return null
    }

    private fun launchCountGate(): Blocked? {
        val minLaunches = settings?.minLaunchesBeforeFirstShow ?: return null
        val launches = launchCount()
        if (launches == 0) return Blocked(AdError.NotReady, "the launch count isn't read yet")
        if (launches > minLaunches) return null
        return Blocked(AdError.FrequencyCapped(Duration.INFINITE), "launch $launches, first show after $minLaunches")
    }

    private fun noPlacementBlock() = Blocked(AdError.InvalidConfiguration("no app open placement is configured"), "no placement")

    /** Starts a load for next time, unless one is cached or running, or app open is off. */
    private fun ensurePreload() {
        val p = placement ?: return
        if (!enabled || !p.enabled || p.preload == PreloadStrategy.NONE || !readiness.canLoad()) return
        val serving = engineFor(p) ?: return
        serving.availability(p.placement)
        if (!serving.isReady(p.placement)) serving.preload(p.placement)
    }

    private suspend fun awaitResumedActivity(): Activity? = withTimeoutOrNull(RESUME_WAIT) {
        var activity = activityTracker.resumedActivity
        while (activity == null) {
            delay(POLL_INTERVAL)
            activity = activityTracker.resumedActivity
        }
        activity
    }

    /**
     * Waits until Ready, a fresh ad and a resumed [activity], or returns why it can't show: readiness settled on
     * something else, the Activity was paused or destroyed during the wait, or [maxWait] elapsed.
     */
    private suspend fun awaitColdStartAd(activity: Activity, p: EffectivePlacement, maxWait: Duration): Blocked? =
        when (val outcome = withTimeoutOrNull(maxWait) { pollColdStart(activity, p) }) {
            null -> Blocked(AdError.Timeout(), "no ad within $maxWait; it's kept for later")
            READY -> null
            else -> outcome
        }

    private suspend fun pollColdStart(activity: Activity, p: EffectivePlacement): Blocked {
        var wasResumed = false
        while (true) {
            val state = readiness.state.value
            if (state is AdsState.Unavailable || state is AdsState.Failed) {
                return Blocked(checkNotNull(state.toShowError()), "ads are off ($state)")
            }
            if (activity.isFinishing || activity.isDestroyed) {
                return Blocked(AdError.ActivityUnavailable, "the Activity was destroyed")
            }
            val resumed = activityTracker.isValidForShow(activity)
            if (wasResumed && !resumed) return Blocked(AdError.ActivityUnavailable, "the Activity was paused")
            wasResumed = wasResumed || resumed
            if (resumed && state == AdsState.Ready && engineFor(p)?.isReady(p.placement) == true) return READY
            delay(POLL_INTERVAL)
        }
    }

    companion object {
        /** How long a warm foreground waits for an Activity to resume after ON_START. */
        val RESUME_WAIT: Duration = 1.seconds

        /** How often the waits re-check their conditions: cheap, and short enough to go unnoticed. */
        val POLL_INTERVAL: Duration = 50.milliseconds

        /** [pollColdStart]'s "go ahead" outcome. */
        private val READY = Blocked(AdError.NotReady, "ready")
    }
}
