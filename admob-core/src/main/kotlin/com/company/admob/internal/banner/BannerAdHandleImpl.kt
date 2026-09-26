package com.company.admob.internal.banner

import android.annotation.SuppressLint
import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.company.admob.AdError
import com.company.admob.AdFormat
import com.company.admob.AdsState
import com.company.admob.BannerAdHandle
import com.company.admob.BannerOptions
import com.company.admob.BannerState
import com.company.admob.GoogleSdkEscapeHatch
import com.company.admob.UnavailableReason
import com.company.admob.events.AdEvent
import com.company.admob.events.AdRevenue
import com.company.admob.events.AdSourceInfo
import com.company.admob.internal.config.EffectiveConfig
import com.company.admob.internal.config.EffectivePlacement
import com.company.admob.internal.config.LiveConfig
import com.company.admob.internal.config.bannerAdUnitId
import com.company.admob.internal.events.EventBus
import com.company.admob.internal.init.Readiness
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.port.AdsSdk
import com.company.admob.internal.port.BannerListener
import com.company.admob.internal.port.RequestExtras
import com.company.admob.internal.port.SdkBannerView
import com.company.admob.internal.reliability.RetryScheduler
import com.company.admob.internal.threading.postToMain
import com.company.admob.internal.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * One banner (architecture §16). The host gets [view], a library [FrameLayout]; the SDK banner view is created and
 * added inside it at the first load. Loads happen only while ads are Ready, once the container's width is known;
 * [readiness] is followed by a single collector, which also hides and destroys the ad when ads are blocked and
 * reloads when they're allowed again. Refresh belongs to the SDK: the library retries only failed first loads, and
 * only while the container is attached and shown.
 *
 * Main thread only. After [destroy] every method is a no-op and the library holds no reference to the handle.
 */
internal class BannerAdHandleImpl(
    activity: Activity,
    placement: EffectivePlacement,
    private var adUnitId: String,
    private val options: BannerOptions,
    private val config: LiveConfig,
    private val adsSdk: AdsSdk,
    private val readiness: Readiness,
    private val retry: RetryScheduler,
    private val events: EventBus,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val log: AdLog,
) : BannerAdHandle {
    private val container = BannerContainer(activity, this)
    private val mutableState = MutableStateFlow<BannerState>(BannerState.Loading)
    private val bannerPlacement = placement.placement
    private val retryPolicy = placement.retry
    private val key = "BANNER:${bannerPlacement.key}@${System.identityHashCode(this)}"
    private val placementKey get() = bannerPlacement.key

    private var sdkView: SdkBannerView? = null
    private var loadedSpec: BannerSizeSpec? = null
    private var generation = 0
    private var loadStartedAt = 0L
    private var lastSource: AdSourceInfo? = null
    private var failedAttempts = 0
    private var lastError: AdError? = null
    private var retryPending = false
    private var waitingForLayout = false
    private var collector: Job? = null
    private var boundOwner: LifecycleOwner? = null
    private var ownerObserver: LifecycleEventObserver? = null
    private var destroyed = false

    override val view: View get() = container

    override val state: StateFlow<BannerState> = mutableState.asStateFlow()

    override var expectedHeightPx: Int = 0
        private set

    /** Starts following readiness. Called once, right after construction, on the main thread. */
    fun start() {
        if (destroyed || collector != null) return
        collector = scope.launch {
            combine(readiness.state, config.flow, ::Pair).collect { (state, current) -> onChange(state, current) }
        }
    }

    override fun bindTo(owner: LifecycleOwner) {
        if (destroyed) return log.d { "Banner $placementKey: bindTo() after destroy() ignored" }
        unbind()
        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return destroy()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) destroy() }
        boundOwner = owner
        ownerObserver = observer
        owner.lifecycle.addObserver(observer)
    }

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        collector?.cancel()
        collector = null
        unbind()
        tearDownAd()
        (container.parent as? ViewGroup)?.removeView(container)
        container.handle = null
        mutableState.value = BannerState.Hidden(null)
        log.d { "Banner $placementKey destroyed" }
    }

    private fun unbind() {
        val owner = boundOwner ?: return
        ownerObserver?.let { owner.lifecycle.removeObserver(it) }
        boundOwner = null
        ownerObserver = null
    }

    /**
     * Readiness or the effective configuration changed. A placement disabled remotely hides the banner
     * (`Hidden(DISABLED_BY_REMOTE)`) and destroys its ad; a new ad unit replaces the ad at the next load.
     */
    private fun onChange(state: AdsState, current: EffectiveConfig) {
        val p = current.placement(bannerPlacement)
        val disabledReason = if (p == null) UnavailableReason.DISABLED_BY_REMOTE else p.disabledReason
        if (p == null || disabledReason != null) {
            if (sdkView != null) log.i { "Banner $placementKey hidden: $disabledReason" }
            tearDownAd()
            mutableState.value = BannerState.Hidden(disabledReason)
            return
        }
        val unit = current.bannerAdUnitId(p, options.size)
        if (unit != adUnitId) {
            log.i { "Banner $placementKey: ad unit changed to ${current.displayAdUnitId(unit)}" }
            tearDownAd()
            adUnitId = unit
        }
        if (mutableState.value is BannerState.Hidden && state !is AdsState.Unavailable) mutableState.value = BannerState.Loading
        onReadiness(state)
    }

    private fun onReadiness(state: AdsState) {
        when (state) {
            AdsState.Ready -> if (sdkView == null) requestLoad()
            is AdsState.Unavailable -> {
                if (sdkView != null) log.i { "Banner $placementKey hidden: ${state.reason}" }
                tearDownAd()
                mutableState.value = BannerState.Hidden(state.reason)
            }
            else -> Unit
        }
    }

    /** Loads at the container's width, or waits for its first layout. */
    private fun requestLoad() {
        if (destroyed || !readiness.canLoad()) return
        if (!container.isLaidOut) {
            waitingForLayout = true
            return
        }
        waitingForLayout = false
        val activity = container.context as Activity
        val spec = BannerSizeResolver.resolve(
            options.size,
            container.width,
            container.resources.displayMetrics.density,
            BannerSizeResolver.windowWidthPx(activity),
        )
        val bannerView = sdkView ?: adsSdk.createBannerView(activity).also { created ->
            sdkView = created
            container.addView(
                created.view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL,
                ),
            )
        }
        expectedHeightPx = bannerView.expectedHeightPx(spec.size, spec.widthDp)
        if (options.reserveSpace) container.minimumHeight = expectedHeightPx
        load(bannerView, spec)
    }

    private fun load(bannerView: SdkBannerView, spec: BannerSizeSpec) {
        loadedSpec = spec
        val loadGeneration = ++generation
        loadStartedAt = clock.elapsedRealtimeMillis()
        if (mutableState.value !is BannerState.Loaded) mutableState.value = BannerState.Loading
        emit(AdEvent.LoadRequested(placementKey, AdFormat.BANNER, adUnitId, now()))
        log.d { "Loading banner $placementKey (${logId()}) at ${spec.widthDp} dp, ${spec.size}" }
        @OptIn(GoogleSdkEscapeHatch::class)
        val extras = RequestExtras(bannerPlacement, config.current.source.advanced.googleRequestCustomizer)
        bannerView.load(adUnitId, spec.size, spec.widthDp, extras, Listener(loadGeneration))
    }

    /** Destroys the SDK view, if any, and stops retrying. The container and handle stay usable. */
    private fun tearDownAd() {
        generation++
        retry.cancel(key)
        retryPending = false
        failedAttempts = 0
        waitingForLayout = false
        loadedSpec = null
        val bannerView = sdkView ?: return
        sdkView = null
        container.removeView(bannerView.view)
        bannerView.destroy()
        container.minimumHeight = 0
        emit(AdEvent.Destroyed(placementKey, AdFormat.BANNER, adUnitId, now()))
    }

    private inner class Listener(private val loadGeneration: Int) : BannerListener {
        private val current get() = !destroyed && loadGeneration == generation

        override fun onLoaded(source: AdSourceInfo?, isRefresh: Boolean) {
            if (!current) return
            failedAttempts = 0
            retry.cancel(key)
            retryPending = false
            lastSource = source
            mutableState.value = BannerState.Loaded
            val latency = if (isRefresh) 0L else clock.elapsedRealtimeMillis() - loadStartedAt
            emit(AdEvent.Loaded(placementKey, AdFormat.BANNER, adUnitId, now(), latency, source))
            log.i { "Banner $placementKey ${if (isRefresh) "refreshed" else "loaded in $latency ms"} (${logId()})" }
        }

        override fun onFailedToLoad(error: AdError, retryable: Boolean, isRefresh: Boolean) {
            if (!current) return
            if (isRefresh) {
                // The SDK keeps the previous ad and refreshes again on its own schedule.
                emit(AdEvent.LoadFailed(placementKey, AdFormat.BANNER, adUnitId, now(), error, 0, willRetry = false))
                log.w { "Banner $placementKey refresh failed: ${error.code}; the SDK keeps refreshing" }
                return
            }
            val attempt = ++failedAttempts
            lastError = error
            mutableState.value = BannerState.Failed(error)
            val willRetry = retryable && attempt <= retryPolicy.maxAttempts
            if (willRetry) scheduleRetry(error)
            emit(AdEvent.LoadFailed(placementKey, AdFormat.BANNER, adUnitId, now(), error, attempt, willRetry))
            log.w { "Banner $placementKey failed to load: ${error.code}, attempt $attempt, will retry: $willRetry" }
        }

        override fun onImpression() {
            if (current) emit(AdEvent.Impression(placementKey, AdFormat.BANNER, adUnitId, now()))
        }

        override fun onClicked() {
            if (current) emit(AdEvent.Clicked(placementKey, AdFormat.BANNER, adUnitId, now()))
        }

        override fun onPaid(revenue: AdRevenue) {
            if (current) emit(AdEvent.Paid(placementKey, AdFormat.BANNER, adUnitId, now(), revenue, lastSource))
        }
    }

    /** Retries only while the container is attached and shown; otherwise the retry waits for [onAttachedOrShown]. */
    private fun scheduleRetry(error: AdError) {
        if (!container.isAttachedAndShown) {
            retryPending = true
            log.d { "Banner $placementKey: retry paused while not attached and shown" }
            return
        }
        retry.schedule(key, failedAttempts, retryPolicy, error, retryable = true) {
            val bannerView = sdkView
            val spec = loadedSpec
            when {
                destroyed || bannerView == null || spec == null || !readiness.canLoad() -> Unit
                container.isAttachedAndShown -> load(bannerView, spec)
                else -> retryPending = true
            }
        }
    }

    internal fun onAttachedOrShown() {
        if (destroyed || !retryPending || !container.isAttachedAndShown) return
        retryPending = false
        lastError?.let(::scheduleRetry)
    }

    internal fun onDetached() {
        if (destroyed || !retry.isScheduled(key)) return
        retry.cancel(key)
        retryPending = true
    }

    /** Posted: during onLayout the container isn't marked laid out yet, and adding the ad view there would re-layout. */
    internal fun onLaidOut() {
        if (!waitingForLayout) return
        postToMain { if (waitingForLayout) requestLoad() }
    }

    /** A width change (layout or configuration change): an adaptive banner reloads when it moved by 8 dp or more. */
    internal fun onWidthChanged(widthPx: Int) {
        val spec = loadedSpec ?: return
        val newWidthDp = (widthPx / container.resources.displayMetrics.density).toInt()
        if (!BannerSizeResolver.needsReload(spec, newWidthDp)) return
        log.d { "Banner $placementKey: width ${spec.widthDp} → $newWidthDp dp; reloading" }
        // Not during the layout pass that reported it.
        postToMain {
            if (destroyed || loadedSpec !== spec) return@postToMain
            tearDownAd()
            requestLoad()
        }
    }

    private fun now(): Long = clock.currentTimeMillis()

    private fun emit(event: AdEvent) = events.emit(event)

    private fun logId(): String = config.current.displayAdUnitId(adUnitId)
}

/** The host-facing container: reports layout, size and attach changes to its handle until the handle is destroyed. */
@SuppressLint("ViewConstructor")
internal class BannerContainer(activity: Activity, var handle: BannerAdHandleImpl?) : FrameLayout(activity) {
    init {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    val isAttachedAndShown: Boolean get() = isAttachedToWindow && isShown

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        handle?.onLaidOut()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw && oldw > 0) handle?.onWidthChanged(w)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handle?.onAttachedOrShown()
    }

    override fun onDetachedFromWindow() {
        handle?.onDetached()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) handle?.onAttachedOrShown()
    }
}
