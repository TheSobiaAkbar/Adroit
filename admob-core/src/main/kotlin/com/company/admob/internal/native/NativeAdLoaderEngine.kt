package com.company.admob.internal.native

import com.company.admob.AdError
import com.company.admob.AdFormat
import com.company.admob.AdPlacement
import com.company.admob.AdsState
import com.company.admob.GoogleSdkEscapeHatch
import com.company.admob.NativeAdResult
import com.company.admob.NativePlacement
import com.company.admob.UnavailableReason
import com.company.admob.config.NativePlacementConfig
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
import com.company.admob.internal.fullscreen.toShowError
import com.company.admob.internal.init.Readiness
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.port.AdsSdk
import com.company.admob.internal.port.AppForegroundSource
import com.company.admob.internal.port.LoadedNativeAd
import com.company.admob.internal.port.RequestExtras
import com.company.admob.internal.port.SdkLoadResult
import com.company.admob.internal.reliability.AdUnitRotation
import com.company.admob.internal.reliability.Cached
import com.company.admob.internal.reliability.RetryScheduler
import com.company.admob.internal.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.WeakHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Loads native ads (architecture §17) through a small pool per ad unit list, shared by the placements that use it.
 * Each load takes the next ad unit of the list from [rotation].
 *
 * - The pool's target is its size (1..3) once preloading is on: from Ready for [PreloadStrategy.AFTER_READY], from
 *   the first `preload`/`load` for [PreloadStrategy.ON_DEMAND]. With [PreloadStrategy.NONE] only an explicit
 *   `preload` fills it, once.
 * - `load` hands out a fresh pooled ad at once, without an SDK call, then refills. With nothing pooled the caller
 *   waits for the next load; a failed load fails every waiting caller.
 * - One SDK request at a time per pool, always for a single ad: multi-ad loading doesn't work with mediation.
 * - Failed fills are retried with the native policy only while the pool is below its target.
 * - Pooled ads expire after the TTL (55 min): destroyed, reported as `Expired`, and refilled.
 * - Blocked ads (ad-free, consent withdrawn) destroy every pooled ad and block every live handle.
 *
 * Every SDK ad is destroyed exactly once: by the pool (expiry, block, surplus, stale load) or by its handle.
 * Main thread only (ADR-006).
 */
internal class NativeAdLoaderEngine(
    private val config: LiveConfig,
    private val adsSdk: AdsSdk,
    private val readiness: Readiness,
    private val foreground: AppForegroundSource,
    private val retry: RetryScheduler,
    private val events: EventBus,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val log: AdLog,
    private val stagger: Duration = STAGGER,
    private val rotation: AdUnitRotation = AdUnitRotation(),
) : AdsBlockedListener, ConfigChangeListener {
    private class Waiter(val placement: EffectivePlacement, val onResult: OnceCallback<NativeAdResult>)

    /** A pooled ad and the ad unit it was loaded from. */
    private class Pooled(val ad: LoadedNativeAd, val adUnitId: String)

    private inner class Pool(val adUnitIds: List<String>, placements: List<EffectivePlacement>) {
        /** The placements sharing this pool; replaced when a remote override changes them. */
        var placements: List<EffectivePlacement> = placements
        val owner: EffectivePlacement get() = placements.first()
        val size: Int get() = placements.maxOf { (it.config as? NativePlacementConfig)?.poolSize ?: FormatDefaults.NATIVE_POOL_SIZE }
        val strategy: PreloadStrategy get() = placements.minOf { it.preload }
        val ttl: Duration = owner.expiry ?: FormatDefaults.NATIVE_TTL
        val key = "NATIVE:" + adUnitIds.joinToString(",")

        /** The ad unit of the latest load request. */
        var adUnitId: String = adUnitIds.first()
        val items = ArrayDeque<Cached<Pooled>>()
        val waiters = ArrayDeque<Waiter>()
        var target = if (strategy == PreloadStrategy.AFTER_READY) size else 0
        var autoRefill = strategy == PreloadStrategy.AFTER_READY
        var loading = false
        var generation = 0
        var failedAttempts = 0
        var expiryJob: Job? = null
    }

    /** Enabled native placements in the current effective configuration. Rebuilt with [pools] on every change. */
    private var placements: Map<AdPlacement, EffectivePlacement> = emptyMap()

    /** The pool of each enabled placement. Placements sharing an effective ad unit share a pool. */
    private var pools: Map<AdPlacement, Pool> = emptyMap()

    /** Handed-out handles that are still active, to block them. Weak: a handle the host dropped isn't kept alive. */
    private val liveHandles: MutableSet<NativeAdHandleImpl> = Collections.newSetFromMap(WeakHashMap())
    private var readyJob: Job? = null

    init {
        rebuild(config.current)
        config.addListener(this)
    }

    /** The number of fresh ads pooled for [placement], for tests and diagnostics. */
    fun pooledCount(placement: NativePlacement): Int = pools[placement]?.items?.size ?: 0

    fun start() {
        scope.launch {
            readiness.state.collect { if (it == AdsState.Ready) onReady() }
        }
    }

    fun preload(placement: NativePlacement) {
        val pool = pools[placement] ?: return log.w { "native.preload(): ${placement.key} isn't an enabled native placement" }
        pool.target = pool.size
        if (pool.strategy != PreloadStrategy.NONE) pool.autoRefill = true
        if (!retry.isScheduled(pool.key)) pool.failedAttempts = 0
        pump(pool)
    }

    /**
     * Delivers one result to [onResult], posted to the main thread. Returns a function that withdraws the request
     * while it's still waiting; an ad that arrives afterwards goes to the pool or is destroyed.
     */
    fun load(placement: NativePlacement, onResult: (NativeAdResult) -> Unit): () -> Unit {
        val callback = OnceCallback(onResult)
        fun fail(error: AdError): () -> Unit {
            log.i { "Native ${placement.key} not loaded: ${error.code}" }
            callback(NativeAdResult.Failed(error))
            return {}
        }
        readiness.state.value.toShowError()?.let { return fail(it) }
        val p = placements[placement] ?: return fail(
            config.current.placement(placement)?.disabledReason?.let { AdError.Disabled(it) }
                ?: AdError.InvalidConfiguration("${placement.key} isn't a configured native placement"),
        )
        val pool = pools.getValue(placement)
        if (pool.strategy == PreloadStrategy.ON_DEMAND && !pool.autoRefill) {
            pool.autoRefill = true
            pool.target = pool.size
        }
        dropExpired(pool)
        val pooled = pool.items.removeFirstOrNull()
        if (pooled != null) {
            if (!pool.autoRefill) pool.target = (pool.target - 1).coerceAtLeast(0)
            log.d { "Native ${placement.key}: served from the pool" }
            handOut(p, pooled.value.ad, pooled.value.adUnitId, callback)
            rescheduleExpiry(pool)
            pump(pool)
            return {}
        }
        val waiter = Waiter(p, callback)
        pool.waiters.addLast(waiter)
        pump(pool)
        return { pool.waiters.remove(waiter) }
    }

    override fun onAdsBlocked(reason: UnavailableReason) {
        log.d { "Native: ads blocked ($reason); destroying pooled ads and live handles" }
        readyJob?.cancel()
        val error = if (reason == UnavailableReason.CONSENT_NOT_OBTAINED) AdError.ConsentRequired else AdError.Disabled(reason)
        pools.values.toSet().forEach { release(it, error) }
        liveHandles.toList().forEach { it.block(reason) }
    }

    /**
     * A remote override changed the configuration: pools whose ad unit is no longer used are released, live handles
     * of placements that are now disabled are blocked (their views removed), and new pools fill per their strategy.
     */
    override fun onConfigChanged(previous: EffectiveConfig, current: EffectiveConfig) {
        val before = pools.values.toSet()
        rebuild(current)
        for (handle in liveHandles.toList()) {
            val reason = current.placement(handle.placement)?.disabledReason ?: continue
            handle.block(reason)
        }
        if (!readiness.canLoad()) return
        pools.values.toSet().filter { it !in before }.forEach { pump(it) }
    }

    /** Recomputes [placements] and [pools] for [current], reusing the pool of every ad unit still in use. */
    private fun rebuild(current: EffectiveConfig) {
        val enabled = current.allPlacements.filter { it.format == AdFormat.NATIVE && it.enabled }
        val reusable = pools.values.associateBy { it.adUnitIds }
        val byUnits = enabled.groupBy { it.adUnitIds }.mapValues { (units, sharing) ->
            reusable[units]?.also { it.placements = sharing } ?: Pool(units, sharing)
        }
        val newPools = enabled.associate { it.placement to byUnits.getValue(it.adUnitIds) }
        (pools.values.toSet() - newPools.values.toSet()).forEach { pool ->
            val reason = current.placement(pool.owner.placement)?.disabledReason ?: UnavailableReason.DISABLED_BY_REMOTE
            log.d { "Native: releasing the pool for ${pool.adUnitIds.joinToString { current.displayAdUnitId(it) }}; no placement uses it any more" }
            release(pool, AdError.Disabled(reason))
        }
        placements = enabled.associateBy { it.placement }
        pools = newPools
    }

    /** Destroys [pool]'s ads, stops its loads and retries, and fails its waiters with [error]. */
    private fun release(pool: Pool, error: AdError) {
        pool.generation++
        pool.loading = false
        pool.failedAttempts = 0
        retry.cancel(pool.key)
        pool.expiryJob?.cancel()
        while (pool.items.isNotEmpty()) pool.items.removeFirst().value.ad.destroy()
        while (pool.waiters.isNotEmpty()) pool.waiters.removeFirst().onResult(NativeAdResult.Failed(error))
    }

    /** Pool and retry details of [placement], or null when it has none (disabled or not configured). */
    fun poolDiagnostics(placement: NativePlacement): PoolDiagnostics? {
        val pool = pools[placement] ?: return null
        return PoolDiagnostics(
            pooled = pool.items.size,
            oldestAge = pool.items.firstOrNull()?.age(clock),
            loading = pool.loading,
            retryAttempt = pool.failedAttempts,
            nextRetryIn = retry.nextRetryIn(pool.key),
        )
    }

    private fun onReady() {
        val toFill = pools.values.toSet().filter { it.target > 0 || it.waiters.isNotEmpty() }
        readyJob?.cancel()
        readyJob = scope.launch {
            toFill.forEachIndexed { index, pool ->
                if (index > 0) delay(stagger)
                pump(pool)
            }
        }
    }

    /** Starts the next load when a caller is waiting or the pool is below target, and none is running. */
    private fun pump(pool: Pool) {
        if (!readiness.canLoad() || pool.loading) return
        val needed = pool.waiters.size + (pool.target - pool.items.size)
        if (needed <= 0) return
        if (retry.isScheduled(pool.key)) {
            if (pool.waiters.isEmpty()) return
            retry.cancel(pool.key) // a caller is waiting: don't make it wait for the backoff
        }
        load(pool)
    }

    private fun load(pool: Pool) {
        pool.loading = true
        val generation = pool.generation
        val startedAt = clock.elapsedRealtimeMillis()
        val p = pool.waiters.firstOrNull()?.placement ?: pool.owner
        val pick = rotation.next(AdFormat.NATIVE, pool.adUnitIds)
        pool.adUnitId = pick.adUnitId
        val adUnitId = pick.adUnitId
        emit(AdEvent.LoadRequested(p.placement.key, AdFormat.NATIVE, adUnitId, now()))
        log.d { "Loading native ${p.placement.key} (${config.current.displayAdUnitId(adUnitId)})${pick.describe}" }
        @OptIn(GoogleSdkEscapeHatch::class)
        val extras = RequestExtras(p.placement, config.current.source.advanced.googleRequestCustomizer)
        adsSdk.loadNative(adUnitId, extras) { result ->
            if (generation != pool.generation) {
                (result as? SdkLoadResult.Success)?.ad?.destroy()
                return@loadNative
            }
            pool.loading = false
            when (result) {
                is SdkLoadResult.Success -> onLoaded(pool, p, adUnitId, result, startedAt)
                is SdkLoadResult.Failure -> onLoadFailed(pool, p, adUnitId, result)
            }
            pump(pool)
        }
    }

    private fun onLoaded(
        pool: Pool,
        p: EffectivePlacement,
        adUnitId: String,
        result: SdkLoadResult.Success<LoadedNativeAd>,
        startedAt: Long,
    ) {
        pool.failedAttempts = 0
        val latency = clock.elapsedRealtimeMillis() - startedAt
        emit(AdEvent.Loaded(p.placement.key, AdFormat.NATIVE, adUnitId, now(), latency, result.source))
        log.i { "Native ${p.placement.key} loaded in $latency ms" }
        val waiter = pool.waiters.removeFirstOrNull()
        when {
            waiter != null -> handOut(waiter.placement, result.ad, adUnitId, waiter.onResult)
            pool.items.size < pool.target -> {
                pool.items.addLast(Cached(Pooled(result.ad, adUnitId), clock.elapsedRealtimeMillis(), pool.ttl))
                rescheduleExpiry(pool)
            }
            else -> {
                log.d { "Native ${p.placement.key}: nobody wants the loaded ad any more; destroying it" }
                result.ad.destroy()
            }
        }
    }

    private fun onLoadFailed(pool: Pool, p: EffectivePlacement, adUnitId: String, result: SdkLoadResult.Failure) {
        val attempt = ++pool.failedAttempts
        while (pool.waiters.isNotEmpty()) pool.waiters.removeFirst().onResult(NativeAdResult.Failed(result.error))
        val delay = if (pool.items.size < pool.target) {
            retry.schedule(pool.key, attempt, p.retry, result.error, result.retryable) { pump(pool) }
        } else {
            null
        }
        emit(AdEvent.LoadFailed(p.placement.key, AdFormat.NATIVE, adUnitId, now(), result.error, attempt, delay != null))
        log.w { "Native ${p.placement.key} failed to load: ${result.error.code}, attempt $attempt, retry in $delay" }
    }

    private fun handOut(p: EffectivePlacement, ad: LoadedNativeAd, adUnitId: String, onResult: OnceCallback<NativeAdResult>) {
        val handle = NativeAdHandleImpl(p.placement as NativePlacement, ad, adUnitId, events, clock, log) {
            liveHandles.remove(it)
        }
        liveHandles += handle
        if (!onResult(NativeAdResult.Loaded(handle))) handle.destroy()
    }

    /** Destroys pooled ads past their TTL. */
    private fun dropExpired(pool: Pool) {
        val iterator = pool.items.iterator()
        while (iterator.hasNext()) {
            val cached = iterator.next()
            if (!cached.isExpired(clock)) continue
            iterator.remove()
            cached.value.ad.destroy()
            emit(AdEvent.Expired(pool.owner.placement.key, AdFormat.NATIVE, cached.value.adUnitId, now()))
            log.i { "Native ${pool.owner.placement.key}: a pooled ad expired" }
        }
    }

    /** Expires the oldest pooled ad on time while in the foreground; in the background it happens at the next load. */
    private fun rescheduleExpiry(pool: Pool) {
        pool.expiryJob?.cancel()
        val next = pool.items.minOfOrNull { it.expiresAtElapsed } ?: return
        pool.expiryJob = scope.launch {
            delay((next - clock.elapsedRealtimeMillis()).milliseconds + 1.milliseconds)
            if (!foreground.isInForeground) return@launch
            dropExpired(pool)
            rescheduleExpiry(pool)
            pump(pool)
        }
    }

    private fun now(): Long = clock.currentTimeMillis()

    private fun emit(event: AdEvent) = events.emit(event)

    companion object {
        /** Spacing between pools filled when the library becomes ready. */
        val STAGGER: Duration = 250.milliseconds
    }
}

/** What diagnostics report about one native placement's pool. */
internal class PoolDiagnostics(
    val pooled: Int,
    val oldestAge: Duration?,
    val loading: Boolean,
    val retryAttempt: Int,
    val nextRetryIn: Duration?,
)
