package com.company.admob.internal.fullscreen

import android.app.Activity
import com.company.admob.AdAvailability
import com.company.admob.AdError
import com.company.admob.AdFormat
import com.company.admob.AdPlacement
import com.company.admob.AdsState
import com.company.admob.FullScreenResult
import com.company.admob.GoogleSdkEscapeHatch
import com.company.admob.Reward
import com.company.admob.RewardResult
import com.company.admob.UnavailableReason
import com.company.admob.config.PreloadStrategy
import com.company.admob.events.AdEvent
import com.company.admob.internal.AdsBlockedListener
import com.company.admob.internal.callbacks.OnceCallback
import com.company.admob.internal.config.ConfigChangeListener
import com.company.admob.internal.config.EffectiveConfig
import com.company.admob.internal.config.LiveConfig
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
 * Each time the library becomes ready, loads start after [readyDelay], then [stagger] apart, so the engines don't
 * all request at once.
 *
 * Main thread only (ADR-006), except [availability] and [isReady]. No Activity is kept beyond a `show` call.
 */
internal class FullScreenAdEngine(
    private val strategy: FullScreenFormatStrategy,
    private val config: LiveConfig,
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
    private val readyDelay: Duration = Duration.ZERO,
) : AdsBlockedListener, ConfigChangeListener {
    val format: AdFormat = strategy.format

    /** This format's placements in the current effective configuration. Rebuilt with [slots] on every change. */
    private var placements: Map<AdPlacement, EffectivePlacement> = emptyMap()

    /** The slot of each enabled placement. Placements sharing an effective ad unit share a slot. */
    private var slots: Map<AdPlacement, FullScreenSlot> = emptyMap()

    /**
     * One availability flow per configured placement, handed to the host. The set of placements is fixed by the local
     * configuration (overrides can't add any), so these flows live as long as the engine.
     */
    private val placementAvailability: Map<AdPlacement, MutableStateFlow<AdAvailability>> =
        config.current.allPlacements.filter { it.format == format }
            .associate { it.placement to MutableStateFlow<AdAvailability>(AdAvailability.Disabled) }

    /** Read-only views of [placementAvailability], created once so every call returns the same instance. */
    private val publicAvailability: Map<AdPlacement, StateFlow<AdAvailability>> =
        placementAvailability.mapValues { it.value.asStateFlow() }
    private val unknown: StateFlow<AdAvailability> = MutableStateFlow(AdAvailability.Unavailable).asStateFlow()
    private val pendingPreloads = linkedSetOf<AdPlacement>()
    private var readyJob: Job? = null

    init {
        rebuild(config.current)
        config.addListener(this)
    }

    /** Starts following [Readiness]: loads `AFTER_READY` placements and pending intents each time it's Ready. */
    fun start() {
        scope.launch {
            readiness.state.collect { if (it == AdsState.Ready) onReady() }
        }
    }

    fun availability(placement: AdPlacement): StateFlow<AdAvailability> {
        val flow = publicAvailability[placement] ?: return unknown
        val p = placements[placement]
        val slot = slots[placement]
        if (p != null && slot != null) checkExpiry(slot, p)
        return flow
    }

    /** Cache and retry details of [placement]'s slot, or null when it has none (disabled or not configured). */
    fun slotDiagnostics(placement: AdPlacement): SlotDiagnostics? {
        val slot = slots[placement] ?: return null
        return SlotDiagnostics(
            availability = slot.availability.value,
            cachedAdAge = slot.cached?.age(clock),
            retryAttempt = slot.failedAttempts,
            nextRetryIn = retry.nextRetryIn(slot.key),
        )
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
        show.session = ShowSession(callback, scope, config.current.source.advanced.showTimeout, { show.timeoutResult() }) {
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
        readiness.state.value.toShowError()?.let { return it }
        if (p == null) return AdError.InvalidConfiguration("${placement.key} isn't a configured $format placement")
        val slot = slots[placement] ?: return AdError.Disabled(p.disabledReason ?: UnavailableReason.DISABLED_BY_CONFIG)
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
            if (toLoad.isNotEmpty()) delay(readyDelay)
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
        val extras = RequestExtras(p.placement, config.current.source.advanced.googleRequestCustomizer)
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

    /**
     * A remote override changed the configuration: slots whose ad unit is no longer used are released (their cached
     * ad and retries dropped), placements are re-attached to their new slot or marked Disabled, and new slots load
     * per their placements' preload strategy.
     */
    override fun onConfigChanged(previous: EffectiveConfig, current: EffectiveConfig) {
        val before = slots.values.toSet()
        rebuild(current)
        pendingPreloads.retainAll(slots.keys)
        if (!readiness.canLoad()) return
        slots.entries.filter { (_, slot) -> slot !in before }
            .map { (placement, _) -> placements.getValue(placement) }
            .filter { it.preload == PreloadStrategy.AFTER_READY }
            .distinctBy { slots[it.placement] }
            .forEach { preload(it.placement) }
    }

    /** Recomputes [placements] and [slots] for [current], reusing the slot of every ad unit still in use. */
    private fun rebuild(current: EffectiveConfig) {
        val newPlacements = current.allPlacements.filter { it.format == format }.associateBy { it.placement }
        val reusable = slots.values.associateBy { it.adUnitId }
        val byUnit = mutableMapOf<String, FullScreenSlot>()
        val newSlots = newPlacements.values.filter { it.enabled }.associate { p ->
            p.placement to byUnit.getOrPut(p.adUnitId) {
                reusable[p.adUnitId] ?: FullScreenSlot(format, p.adUnitId, p.retry, p.expiry ?: error("$format has no TTL"))
            }
        }
        val dropped = slots.values.toSet() - newSlots.values.toSet()
        dropped.forEach {
            log.d { "$format: releasing the slot for ${logId(it.adUnitId)}; no placement uses it any more" }
            reset(it, AdAvailability.Disabled)
        }
        for ((placement, flow) in placementAvailability) {
            slots[placement]?.availability?.detach(flow)
            val slot = newSlots[placement]
            if (slot != null) slot.availability.attach(flow) else flow.value = AdAvailability.Disabled
        }
        placements = newPlacements
        slots = newSlots
    }

    private fun now(): Long = clock.currentTimeMillis()

    private fun emit(event: AdEvent) = events.emit(event)

    private fun logId(adUnitId: String): String = config.current.displayAdUnitId(adUnitId)

    companion object {
        /** Spacing between the loads started when the library becomes ready, to avoid a burst at startup. */
        val STAGGER: Duration = 250.milliseconds
    }
}

/** Why a show can't proceed in this readiness state, or null when [AdsState.Ready]. */
internal fun AdsState.toShowError(): AdError? = when (this) {
    AdsState.Ready -> null
    is AdsState.Unavailable ->
        if (reason == UnavailableReason.CONSENT_NOT_OBTAINED) AdError.ConsentRequired else AdError.Disabled(reason)
    is AdsState.Failed -> error
    else -> AdError.NotInitialized
}

/** What diagnostics report about one placement's cache. */
internal class SlotDiagnostics(
    val availability: AdAvailability,
    val cachedAdAge: Duration?,
    val retryAttempt: Int,
    val nextRetryIn: Duration?,
)
