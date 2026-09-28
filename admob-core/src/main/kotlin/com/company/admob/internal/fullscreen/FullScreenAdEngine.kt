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
import com.company.admob.internal.config.FormatDefaults
import com.company.admob.internal.events.EventBus
import com.company.admob.internal.init.Readiness
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.port.ActivityTracker
import com.company.admob.internal.port.AdsSdk
import com.company.admob.internal.port.AppForegroundSource
import com.company.admob.internal.port.LoadedFullScreenAd
import com.company.admob.internal.port.RequestExtras
import com.company.admob.internal.port.SdkLoadResult
import com.company.admob.internal.reliability.AdUnitRotation
import com.company.admob.internal.reliability.Cached
import com.company.admob.internal.reliability.FrequencyDecision
import com.company.admob.internal.reliability.FrequencyGate
import com.company.admob.internal.reliability.FullScreenCoordinator
import com.company.admob.internal.reliability.RetryScheduler
import com.company.admob.internal.reliability.ShowSession
import com.company.admob.internal.time.Clock
import kotlinx.coroutines.CancellationException
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

/**
 * Loads, caches, expires and shows one full-screen format (architecture §12, §13). Placements resolving to the same
 * ad unit list share a [FullScreenSlot], and each load takes the next ad unit of the list from [rotation]. Nothing is requested unless [Readiness.canLoad]; earlier `preload` calls are
 * kept as intent and run when the library becomes ready.
 *
 * Each time the library becomes ready, this engine's loads start after [readyDelay], then [stagger] apart, so its
 * placements don't all request at once. The spacing is per engine: each format's first load starts at the same time.
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
    private val rotation: AdUnitRotation = AdUnitRotation(),
    /** The "Loading ad…" screen of ad profiles with `showLoading` (PROMPT 29B); null shows none. */
    private val loadingScreens: LoadingScreens? = null,
) : AdsBlockedListener, ConfigChangeListener {
    val format: AdFormat = strategy.format

    /**
     * Whether a `show` call for [p] counts toward its counter: interstitials and rewarded interstitials only, judged
     * by the configured format, so an interstitial swapped to app open keeps counting.
     */
    private fun countsTriggers(p: EffectivePlacement): Boolean =
        p.frequencyPlacement.format == AdFormat.INTERSTITIAL || p.frequencyPlacement.format == AdFormat.REWARDED_INTERSTITIAL

    /** The `showOnSplash` wait in progress, if any. */
    private var splashJob: Job? = null

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
     *
     * An ad profile whose ad isn't loaded (or expired) is requested and waited for, up to its load timeout
     * (PROMPT 29): it shows if it arrives in time, otherwise the result is `NotShown(Timeout)` and the ad, once
     * loaded, is kept for the next show. Other placements, and shows the library starts itself ([mayWait] false, the
     * app open ad on return), answer `NotShown(NotReady)` at once.
     *
     * An ad profile with `showLoading` (not the splash ad, whose screen is the splash) gets a loading screen from the
     * wait, or from the call when the ad is ready, for at least its `loadingSeconds`; it's removed when the ad opens,
     * fails, or isn't shown.
     */
    fun show(
        activity: Activity,
        placement: AdPlacement,
        prepare: (LoadedFullScreenAd) -> Unit = {},
        onRewardEarned: (Reward) -> Unit = {},
        mayWait: Boolean = true,
        onResult: (FullScreenResult) -> Unit,
    ) {
        show(activity, placement, prepare, onRewardEarned, OnceCallback(onResult), afterWait = false, mayWait = mayWait)
    }

    private fun show(
        activity: Activity,
        placement: AdPlacement,
        prepare: (LoadedFullScreenAd) -> Unit,
        onRewardEarned: (Reward) -> Unit,
        callback: OnceCallback<FullScreenResult>,
        afterWait: Boolean,
        mayWait: Boolean = false,
        loading: LoadingScreen? = null,
    ) {
        val p = placements[placement]
        val requestedUnit = slots[placement]?.adUnitId ?: p?.adUnitId
        if (!afterWait) p?.let { emit(AdEvent.ShowRequested(it.placement.key, format, requestedUnit!!, now())) }
        // After a wait, the trigger was already counted by the call that started it.
        val error = gateError(activity, placement, p, countTrigger = !afterWait)
        val behavior = p?.behavior
        val timeout = behavior?.loadTimeout
        val waits = mayWait && !afterWait && (error == AdError.NotReady || error == AdError.Expired) &&
            timeout != null && timeout.isPositive()
        val withLoading = !afterWait && loadingScreens != null && behavior?.showLoading == true && !p.isSplash &&
            (error == null || waits)
        if (waits || withLoading) {
            prepareThenShow(activity, p!!, if (waits) timeout else null, withLoading, prepare, onRewardEarned, callback)
            return
        }
        if (error != null) {
            loading?.dismiss()
            log.i { "$format ${placement.key} not shown: ${error.code}" }
            p?.let { emit(AdEvent.NotShown(it.placement.key, format, requestedUnit!!, now(), error)) }
            callback(FullScreenResult.NotShown(error))
            return
        }
        // Every gate passed, so the placement is configured, enabled and holds a fresh ad.
        val slot = slots.getValue(placement)
        val ad = checkNotNull(slot.take())
        val token = checkNotNull(coordinator.tryAcquire("$format:${placement.key}"))
        slot.availability.value = AdAvailability.Unavailable
        val show = FullScreenShow(
            p!!, slot.adUnitId, ad, token, format, frequency, coordinator, events, clock, log, onRewardEarned, scope,
            strategy.dismissGrace,
            onOpened = { loading?.dismiss() },
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
        val session = p?.let { RewardSession(it, slots[placement]?.adUnitId ?: it.adUnitId, format, events, clock, log) }
        show(
            activity,
            placement,
            prepare = { it.setServerSideVerification(customData) },
            onRewardEarned = { reward -> session?.onRewardEarned(reward) },
        ) { result ->
            onResult(session?.resolve(result) ?: RewardResult.Failed((result as FullScreenResult.NotShown).error))
        }
    }

    /**
     * What comes before a profile's show (PROMPT 29): with [waitFor], requests [p]'s ad and waits for it that long;
     * with [withLoading], a loading screen covers the wait, and stays at least the ad's `loadingSeconds`. Every other
     * full-screen ad waits meanwhile (the coordinator is held, without counting as an ad's end). Then the ad shows,
     * or the result is `NotShown` (timeout, failed load, Activity gone) and the screen goes. A late ad is kept.
     */
    private fun prepareThenShow(
        activity: Activity,
        p: EffectivePlacement,
        waitFor: Duration?,
        withLoading: Boolean,
        prepare: (LoadedFullScreenAd) -> Unit,
        onRewardEarned: (Reward) -> Unit,
        callback: OnceCallback<FullScreenResult>,
    ) {
        val placement = p.placement
        fun notShown(error: AdError) {
            log.i { "$format ${placement.key} not shown: ${error.code}" }
            emit(AdEvent.NotShown(placement.key, format, slots[placement]?.adUnitId ?: p.adUnitId, now(), error))
            callback(FullScreenResult.NotShown(error))
        }
        val token = coordinator.tryAcquire("$format:${placement.key}:preparing") ?: return notShown(AdError.AlreadyShowing)
        val screen = if (withLoading) loadingScreens?.show(activity) else null
        val startedAt = clock.elapsedRealtimeMillis()
        if (waitFor != null) {
            log.i { "$format ${placement.key} isn't loaded; waiting for it up to $waitFor" }
            preload(placement)
        }
        scope.launch {
            var error: AdError? = null
            try {
                if (waitFor != null) {
                    val waited = withTimeoutOrNull(waitFor) { SplashWait(awaitAd(activity, placement)) }
                    error = if (waited == null) {
                        log.i { "$format ${placement.key}: not loaded within $waitFor; moving on (the ad is kept if it arrives)" }
                        AdError.Timeout()
                    } else {
                        waited.error
                    }
                }
                if (error == null && screen != null) {
                    val left = (p.behavior?.loadingTime ?: Duration.ZERO) - (clock.elapsedRealtimeMillis() - startedAt).milliseconds
                    if (left.isPositive()) delay(left)
                }
            } catch (e: CancellationException) {
                screen?.dismiss()
                throw e
            } finally {
                coordinator.release(token, adEnded = false)
            }
            if (error != null) {
                screen?.dismiss()
                notShown(error)
            } else {
                show(activity, placement, prepare, onRewardEarned, callback, afterWait = true, loading = screen)
            }
        }
    }

    private fun gateError(activity: Activity, placement: AdPlacement, p: EffectivePlacement?, countTrigger: Boolean = true): AdError? {
        readiness.state.value.toShowError()?.let { return it }
        if (p == null) return AdError.InvalidConfiguration("${placement.key} isn't a configured $format placement")
        val slot = slots[placement] ?: return AdError.Disabled(p.disabledReason ?: UnavailableReason.DISABLED_BY_CONFIG)
        when (val decision = frequency.check(p.frequencyPlacement, countTrigger = countTrigger && countsTriggers(p))) {
            is FrequencyDecision.CounterNotReached -> return AdError.CounterNotReached(decision.remaining)
            is FrequencyDecision.Capped -> return AdError.FrequencyCapped(decision.retryAfter)
            FrequencyDecision.Allowed -> Unit
        }
        if (!activityTracker.isValidForShow(activity)) return AdError.ActivityUnavailable
        if (coordinator.isShowing) return AdError.AlreadyShowing
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
        val pick = rotation.next(format, slot.adUnitIds)
        slot.adUnitId = pick.adUnitId
        emit(AdEvent.LoadRequested(p.placement.key, format, slot.adUnitId, now()))
        log.d { "Loading $format ${p.placement.key} (${logId(slot.adUnitId)})${pick.describe}" }
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
        // Ads that load only when asked (PreloadStrategy.NONE) never retry by themselves: the next ask is the retry.
        val delay = if (isManual(slot)) {
            log.d { "$format ${p.placement.key}: loads only when asked; not retrying" }
            null
        } else {
            retry.schedule(slot.key, attempt, slot.retryPolicy, result.error, result.retryable) { load(slot, p) }
        }
        slot.availability.value = AdAvailability.Failed(result.error)
        emit(AdEvent.LoadFailed(p.placement.key, format, slot.adUnitId, now(), result.error, attempt, delay != null))
        log.w { "$format ${p.placement.key} failed to load: ${result.error.code}, attempt $attempt, retry in $delay" }
    }

    /** Whether every placement sharing [slot] loads only on an explicit preload. */
    private fun isManual(slot: FullScreenSlot): Boolean =
        slots.filterValues { it === slot }.keys.all { placements[it]?.preload == PreloadStrategy.NONE }

    /**
     * The splash is over and won't show [placement]'s ad: drops its cached ad, its load in flight and its retries, so
     * no ad loads that nobody will show. Does nothing when another placement shares its ad unit.
     */
    fun abandon(placement: AdPlacement) {
        val slot = slots[placement] ?: return
        if (slots.any { (other, s) -> s === slot && other != placement }) return
        if (slot.cached == null && !slot.loading && !retry.isScheduled(slot.key)) return
        log.d { "$format ${placement.key}: splash over; dropping its ad, load and retries" }
        clear(placement)
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
        // An ad profile's loadAgain: the next ad unit now, whatever the preload strategy or retry policy.
        if (p.loadAgain) {
            log.d { "$format ${p.placement.key}: shown; loading the next one (loadAgain)" }
            slot.failedAttempts = 0
            load(slot, p)
            return
        }
        // A splash ad shows once per launch: no reload, unless another placement shares its ad unit.
        val shared = slots.any { (placement, other) -> other === slot && placement != p.placement }
        if (p.isSplash && !shared) {
            log.d { "$format ${p.placement.key}: splash ad shown; not reloading it" }
            return
        }
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
        // New slots, and placements a remote value just switched to "auto", load now.
        slots.entries.filter { (placement, slot) ->
            slot !in before || previous.placement(placement)?.preload != PreloadStrategy.AFTER_READY
        }
            .map { (placement, _) -> placements.getValue(placement) }
            .filter { it.preload == PreloadStrategy.AFTER_READY }
            .distinctBy { slots[it.placement] }
            .forEach { preload(it.placement) }
    }

    /** Recomputes [placements] and [slots] for [current], reusing the slot of every ad unit still in use. */
    private fun rebuild(current: EffectiveConfig) {
        val newPlacements = current.allPlacements.filter { it.format == format }.associateBy { it.placement }
        val reusable = slots.values.associateBy { it.adUnitIds }
        val byUnits = mutableMapOf<List<String>, FullScreenSlot>()
        val newSlots = newPlacements.values.filter { it.enabled }.associate { p ->
            p.placement to byUnits.getOrPut(p.adUnitIds) {
                reusable[p.adUnitIds] ?: FullScreenSlot(format, p.adUnitIds, p.retry, p.expiry ?: error("$format has no TTL"))
            }
        }
        val dropped = slots.values.toSet() - newSlots.values.toSet()
        dropped.forEach {
            log.d { "$format: releasing the slot for ${it.adUnitIds.joinToString { id -> logId(id) }}; no placement uses it any more" }
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

    /**
     * Shows [placement]'s ad on a splash screen (05_AD_PROFILES.md §4.4): waits up to the placement's splash timeout
     * for the library to be ready (consent included), an ad and a resumed [activity], then runs [show]. [onResult]
     * gets exactly one result, posted to the main thread; on timeout it's `NotShown(Timeout)` and the ad keeps
     * loading. A call while another is waiting completes at once with `NotShown(AlreadyShowing)`.
     */
    fun showOnSplash(activity: Activity, placement: AdPlacement, onResult: (FullScreenResult) -> Unit) {
        val callback = OnceCallback(onResult)
        val p = placements[placement]
        fun notShown(error: AdError) {
            log.i { "$format ${placement.key} not shown on splash: ${error.code}" }
            p?.let {
                emit(AdEvent.ShowRequested(it.placement.key, format, it.adUnitId, now()))
                emit(AdEvent.NotShown(it.placement.key, format, it.adUnitId, now(), error))
            }
            callback(FullScreenResult.NotShown(error))
        }
        when {
            splashJob?.isActive == true -> return notShown(AdError.AlreadyShowing)
            p == null -> return notShown(AdError.InvalidConfiguration("${placement.key} isn't a configured $format placement"))
            !p.enabled -> return notShown(AdError.Disabled(p.disabledReason ?: UnavailableReason.DISABLED_BY_CONFIG))
        }
        val timeout = p!!.splashTimeout ?: FormatDefaults.SPLASH_TIMEOUT
        // Runs now if Ready; otherwise kept as intent and loaded as soon as the library becomes ready.
        preload(placement)
        splashJob = scope.launch {
            val waited = withTimeoutOrNull(timeout) { SplashWait(awaitAd(activity, placement)) }
            when {
                waited == null -> notShown(AdError.Timeout())
                waited.error != null -> notShown(waited.error)
                else -> show(activity, placement) { callback(it) }
            }
        }
    }

    private class SplashWait(val error: AdError?)

    /** Null once Ready with a fresh ad and [activity] resumed; otherwise why the ad can't show (splash, load wait). */
    private suspend fun awaitAd(activity: Activity, placement: AdPlacement): AdError? {
        var wasResumed = false
        var sawLoading = false
        while (true) {
            val state = readiness.state.value
            if (state is AdsState.Unavailable || state is AdsState.Failed) return state.toShowError()
            if (activity.isFinishing || activity.isDestroyed) return AdError.ActivityUnavailable
            val resumed = activityTracker.isValidForShow(activity)
            if (wasResumed && !resumed) return AdError.ActivityUnavailable
            wasResumed = wasResumed || resumed
            if (resumed && state == AdsState.Ready && isReady(placement)) return null
            // The load this wait started failed, and nothing will retry it: stop waiting now.
            val slot = slots[placement]
            if (slot != null) {
                sawLoading = sawLoading || slot.loading
                val failed = slot.availability.value as? AdAvailability.Failed
                if (sawLoading && failed != null && !slot.loading && !retry.isScheduled(slot.key)) return failed.error
            }
            delay(SPLASH_POLL_INTERVAL)
        }
    }

    private fun now(): Long = clock.currentTimeMillis()

    private fun emit(event: AdEvent) = events.emit(event)

    private fun logId(adUnitId: String): String = config.current.displayAdUnitId(adUnitId)

    companion object {
        /** Spacing between the loads started when the library becomes ready, to avoid a burst at startup. */
        val STAGGER: Duration = 250.milliseconds

        /** How often `showOnSplash` re-checks its conditions, as the app open cold-start wait does. */
        val SPLASH_POLL_INTERVAL: Duration = 50.milliseconds
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
