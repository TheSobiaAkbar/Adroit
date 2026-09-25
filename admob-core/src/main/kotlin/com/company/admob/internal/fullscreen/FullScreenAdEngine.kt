package com.company.admob.internal.fullscreen

import android.app.Activity
import com.company.admob.AdAvailability
import com.company.admob.AdError
import com.company.admob.AdPlacement
import com.company.admob.AdsState
import com.company.admob.FullScreenResult
import com.company.admob.GoogleSdkEscapeHatch
import com.company.admob.Reward
import com.company.admob.RewardResult
import com.company.admob.UnavailableReason
import com.company.admob.config.AdEnvironment
import com.company.admob.config.PreloadStrategy
import com.company.admob.events.AdEvent
import com.company.admob.events.redactAdUnitId
import com.company.admob.internal.AdsBlockedListener
import com.company.admob.internal.callbacks.OnceCallback
import com.company.admob.internal.config.EffectiveConfig
import com.company.admob.internal.config.EffectivePlacement
import com.company.admob.internal.events.EventBus
import com.company.admob.internal.init.Readiness
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.port.ActivityTracker
import com.company.admob.internal.port.AdsSdk
import com.company.admob.internal.port.AppForegroundSource
import com.company.admob.internal.port.LoadedFullScreenAd
import com.company.admob.internal.port.RequestExtras
import com.company.admob.internal.port.SdkLoadResult
import com.company.admob.internal.reliability.Cached
import com.company.admob.internal.reliability.FrequencyDecision
import com.company.admob.internal.reliability.FrequencyGate
import com.company.admob.internal.reliability.FullScreenCoordinator
import com.company.admob.internal.reliability.RetryScheduler
import com.company.admob.internal.reliability.ShowSession
import com.company.admob.internal.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Loads, caches, expires and shows one full-screen format (architecture §12, §13). Placements resolving to the same
 * ad unit share a [FullScreenSlot]. Nothing is requested unless [Readiness.canLoad]; earlier `preload` calls are
 * kept as intent and run when the library becomes ready.
 *
 * Main thread only (ADR-006), except [availability] and [isReady]. No Activity is kept beyond a `show` call.
 */
internal class FullScreenAdEngine(
    private val strategy: FullScreenFormatStrategy,
    private val config: EffectiveConfig,
    private val adsSdk: AdsSdk,
    private val readiness: Readiness,
    private val activityTracker: ActivityTracker,
    private val foreground: AppForegroundSource,
    private val retry: RetryScheduler,
    private val frequency: FrequencyGate,
    private val coordinator: FullScreenCoordinator,
    private val events: EventBus,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val log: AdLog,
    private val stagger: Duration = STAGGER,
) : AdsBlockedListener {
    private val format = strategy.format
    private val placements: Map<AdPlacement, EffectivePlacement> =
        config.allPlacements.filter { it.format == format }.associateBy { it.placement }
    private val slots: Map<AdPlacement, FullScreenSlot>
    private val disabled: StateFlow<AdAvailability> = MutableStateFlow(AdAvailability.Disabled).asStateFlow()
    private val unknown: StateFlow<AdAvailability> = MutableStateFlow(AdAvailability.Unavailable).asStateFlow()
    private val pendingPreloads = linkedSetOf<AdPlacement>()
    private var readyJob: Job? = null

    init {
        val byUnit = mutableMapOf<String, FullScreenSlot>()
        slots = placements.values.filter { it.enabled }.associate { p ->
            p.placement to byUnit.getOrPut(p.adUnitId) {
                FullScreenSlot(format, p.adUnitId, p.retry, p.expiry ?: error("$format has no TTL"))
            }
        }
    }

    /** Starts following [Readiness]: loads `AFTER_READY` placements and pending intents each time it's Ready. */
    fun start() {
        scope.launch {
            readiness.state.collect { if (it == AdsState.Ready) onReady() }
        }
    }

    fun availability(placement: AdPlacement): StateFlow<AdAvailability> {
        val p = placements[placement] ?: return unknown
        val slot = slots[placement] ?: return disabled
        checkExpiry(slot, p)
        return slot.availability
    }

    /** Thread-safe: a fresh ad is cached for [placement]. */
    fun isReady(placement: AdPlacement): Boolean {
        val slot = slots[placement] ?: return false
        return slot.availability.value == AdAvailability.Ready && clock.elapsedRealtimeMillis() <= slot.expiresAtElapsed
    }

    /** Explicit request: resets exhausted retries, unless a retry is already scheduled. */
    fun preload(placement: AdPlacement) {
        val p = placements[placement] ?: return log.w { "preload(): ${placement.key} isn't a configured $format placement" }
        val slot = slots[placement] ?: return log.d { "preload(): ${placement.key} is disabled" }
        if (!readiness.canLoad()) {
            pendingPreloads += placement
            return
        }
        if (retry.isScheduled(slot.key)) return
        slot.failedAttempts = 0
        load(slot, p)
    }

    fun clear(placement: AdPlacement) {
        val slot = slots[placement] ?: return
        pendingPreloads -= placement
        reset(slot, if (readiness.canLoad()) AdAvailability.Unavailable else slot.availability.value)
    }

    /** Ads were blocked (ad-free, consent withdrawn, remote disable): release everything and stop retrying. */
    override fun onAdsBlocked(reason: UnavailableReason) {
        log.d { "$format: ads blocked ($reason); releasing cached ads" }
        readyJob?.cancel()
        pendingPreloads.clear()
        slots.values.toSet().forEach { reset(it, AdAvailability.Disabled) }
    }

    /**
     * Runs the §13 gates in order and shows the cached ad. [onResult] is delivered exactly once, on the main thread,
     * and never synchronously inside this call. [prepare] runs on the ad just before it's shown, and [onRewardEarned]
     * receives the SDK's reward callback (both for the rewarded formats).
     */
    fun show(
        activity: Activity,
        placement: AdPlacement,
        prepare: (LoadedFullScreenAd) -> Unit = {},
        onRewardEarned: (Reward) -> Unit = {},
        onResult: (FullScreenResult) -> Unit,
    ) {
        val callback = OnceCallback(onResult)
        val p = placements[placement]
        p?.let { emit(AdEvent.ShowRequested(it.placement.key, format, it.adUnitId, now())) }
        val error = gateError(activity, placement, p)
        if (error != null) {
            log.i { "$format ${placement.key} not shown: ${error.code}" }
            p?.let { emit(AdEvent.NotShown(it.placement.key, format, it.adUnitId, now(), error)) }
            callback(FullScreenResult.NotShown(error))
            return
        }
        // Every gate passed, so the placement is configured, enabled and holds a fresh ad.
        val slot = slots.getValue(placement)
        val ad = checkNotNull(slot.take())
        val token = checkNotNull(coordinator.tryAcquire("$format:${placement.key}"))
        slot.availability.value = AdAvailability.Unavailable
        val show = FullScreenShow(
            p!!, ad, token, format, frequency, coordinator, events, clock, log, onRewardEarned, scope, strategy.dismissGrace,
        ) {
            reloadAfterShow(p)
        }
        show.session = ShowSession(callback, scope, config.source.advanced.showTimeout, { show.timeoutResult() }) {
            show.onTimeout()
        }
        prepare(ad)
        log.i { "Showing $format ${placement.key} (${logId(slot.adUnitId)})" }
        ad.show(activity, show)
    }

    /**
     * [show] for the rewarded formats: sets the server-side verification [customData] on the ad right before it's
     * shown, tracks the reward in a [RewardSession], and delivers the final [RewardResult] exactly once, posted to
     * the main thread. The callback reference is dropped once delivered.
     */
    fun showRewarded(activity: Activity, placement: AdPlacement, customData: String?, onResult: (RewardResult) -> Unit) {
        val p = placements[placement]
        val session = p?.let { RewardSession(it, format, events, clock, log) }
        show(
            activity,
            placement,
            prepare = { it.setServerSideVerification(customData) },
            onRewardEarned = { reward -> session?.onRewardEarned(reward) },
        ) { result ->
            onResult(session?.resolve(result) ?: RewardResult.Failed((result as FullScreenResult.NotShown).error))
        }
    }

    private fun gateError(activity: Activity, placement: AdPlacement, p: EffectivePlacement?): AdError? {
        readinessError(readiness.state.value)?.let { return it }
        if (p == null) return AdError.InvalidConfiguration("${placement.key} isn't a configured $format placement")
        val slot = slots[placement] ?: return AdError.Disabled(UnavailableReason.DISABLED_BY_CONFIG)
        if (!activityTracker.isValidForShow(activity)) return AdError.ActivityUnavailable
        if (coordinator.isShowing) return AdError.AlreadyShowing
        (frequency.check(placement) as? FrequencyDecision.Capped)?.let { return AdError.FrequencyCapped(it.retryAfter) }
        val cached = slot.cached
        if (cached == null) {
            if (p.preload != PreloadStrategy.NONE) preload(placement)
            return AdError.NotReady
        }
        if (cached.isExpired(clock)) {
            expire(slot, p)
            return AdError.Expired
        }
        return null
    }

    private fun readinessError(state: AdsState): AdError? = when (state) {
        AdsState.Ready -> null
        is AdsState.Unavailable ->
            if (state.reason == UnavailableReason.CONSENT_NOT_OBTAINED) AdError.ConsentRequired else AdError.Disabled(state.reason)
        is AdsState.Failed -> state.error
        else -> AdError.NotInitialized
    }

    private fun onReady() {
        val toLoad = placements.values.filter { it.enabled && it.preload == PreloadStrategy.AFTER_READY }
            .map { it.placement }
            .plus(pendingPreloads)
            .distinctBy { slots[it] }
        pendingPreloads.clear()
        slots.values.toSet().filter { it.availability.value == AdAvailability.Disabled }
            .forEach { it.availability.value = AdAvailability.Unavailable }
        readyJob?.cancel()
        readyJob = scope.launch {
            toLoad.forEachIndexed { index, placement ->
                if (index > 0) delay(stagger)
                preload(placement)
            }
        }
    }

    private fun load(slot: FullScreenSlot, p: EffectivePlacement) {
        if (!readiness.canLoad() || slot.loading || slot.cached != null) return
        slot.loading = true
        slot.availability.value = AdAvailability.Loading
        val generation = slot.generation
        val startedAt = clock.elapsedRealtimeMillis()
        emit(AdEvent.LoadRequested(p.placement.key, format, slot.adUnitId, now()))
        log.d { "Loading $format ${p.placement.key} (${logId(slot.adUnitId)})" }
        @OptIn(GoogleSdkEscapeHatch::class)
        val extras = RequestExtras(p.placement, config.source.advanced.googleRequestCustomizer)
        adsSdk.loadFullScreen(format, slot.adUnitId, extras) { result ->
            if (generation != slot.generation) {
                (result as? SdkLoadResult.Success)?.ad?.release()
                return@loadFullScreen
            }
            slot.loading = false
            when (result) {
                is SdkLoadResult.Success -> onLoaded(slot, p, result, startedAt)
                is SdkLoadResult.Failure -> onLoadFailed(slot, p, result)
            }
        }
    }

    private fun onLoaded(slot: FullScreenSlot, p: EffectivePlacement, result: SdkLoadResult.Success<LoadedFullScreenAd>, startedAt: Long) {
        slot.failedAttempts = 0
        slot.store(Cached(result.ad, clock.elapsedRealtimeMillis(), slot.ttl))
        slot.availability.value = AdAvailability.Ready
        val latency = clock.elapsedRealtimeMillis() - startedAt
        emit(AdEvent.Loaded(p.placement.key, format, slot.adUnitId, now(), latency, result.source))
        log.i { "$format ${p.placement.key} loaded in $latency ms (${logId(slot.adUnitId)})" }
        slot.expiryJob = scope.launch {
            delay((slot.ttl + 1.milliseconds))
            // In the background the ad is expired lazily, on the next availability check or show.
            if (foreground.isInForeground) checkExpiry(slot, p)
        }
    }

    private fun onLoadFailed(slot: FullScreenSlot, p: EffectivePlacement, result: SdkLoadResult.Failure) {
        val attempt = ++slot.failedAttempts
        val delay = retry.schedule(slot.key, attempt, slot.retryPolicy, result.error, result.retryable) { load(slot, p) }
        slot.availability.value = AdAvailability.Failed(result.error)
        emit(AdEvent.LoadFailed(p.placement.key, format, slot.adUnitId, now(), result.error, attempt, delay != null))
        log.w { "$format ${p.placement.key} failed to load: ${result.error.code}, attempt $attempt, retry in $delay" }
    }

    private fun checkExpiry(slot: FullScreenSlot, p: EffectivePlacement) {
        if (slot.cached?.isExpired(clock) == true) expire(slot, p)
    }

    private fun expire(slot: FullScreenSlot, p: EffectivePlacement) {
        slot.take()?.release()
        slot.availability.value = AdAvailability.Unavailable
        emit(AdEvent.Expired(p.placement.key, format, slot.adUnitId, now()))
        log.i { "$format ${p.placement.key}: cached ad expired" }
        if (p.preload != PreloadStrategy.NONE) load(slot, p)
    }

    private fun reset(slot: FullScreenSlot, availability: AdAvailability) {
        slot.generation++
        slot.loading = false
        slot.failedAttempts = 0
        slot.take()?.release()
        retry.cancel(slot.key)
        slot.availability.value = availability
    }

    private fun reloadAfterShow(p: EffectivePlacement) {
        val slot = slots[p.placement] ?: return
        if (p.preload != PreloadStrategy.NONE) {
            slot.failedAttempts = 0
            load(slot, p)
        }
    }

    private fun now(): Long = clock.currentTimeMillis()

    private fun emit(event: AdEvent) = events.emit(event)

    private fun logId(adUnitId: String): String =
        if (config.environment == AdEnvironment.PRODUCTION) redactAdUnitId(adUnitId) else adUnitId

    companion object {
        /** Spacing between the loads started when the library becomes ready, to avoid a burst at startup. */
        val STAGGER: Duration = 250.milliseconds
    }
}
