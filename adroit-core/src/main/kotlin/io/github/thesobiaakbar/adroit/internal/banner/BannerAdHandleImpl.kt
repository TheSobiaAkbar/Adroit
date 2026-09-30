package io.github.thesobiaakbar.adroit.internal.banner

import android.annotation.SuppressLint
import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.AdFormat
import io.github.thesobiaakbar.adroit.AdsState
import io.github.thesobiaakbar.adroit.BannerAdHandle
import io.github.thesobiaakbar.adroit.BannerOptions
import io.github.thesobiaakbar.adroit.BannerSize
import io.github.thesobiaakbar.adroit.BannerState
import io.github.thesobiaakbar.adroit.GoogleSdkEscapeHatch
import io.github.thesobiaakbar.adroit.UnavailableReason
import io.github.thesobiaakbar.adroit.events.AdEvent
import io.github.thesobiaakbar.adroit.events.AdRevenue
import io.github.thesobiaakbar.adroit.events.AdSourceInfo
import io.github.thesobiaakbar.adroit.internal.config.EffectiveConfig
import io.github.thesobiaakbar.adroit.internal.config.EffectivePlacement
import io.github.thesobiaakbar.adroit.internal.config.LiveConfig
import io.github.thesobiaakbar.adroit.internal.config.bannerAdUnitIds
import io.github.thesobiaakbar.adroit.internal.events.EventBus
import io.github.thesobiaakbar.adroit.internal.init.Readiness
import io.github.thesobiaakbar.adroit.internal.logging.AdLog
import io.github.thesobiaakbar.adroit.internal.port.AdsSdk
import io.github.thesobiaakbar.adroit.internal.port.BannerListener
import io.github.thesobiaakbar.adroit.internal.port.PreloadedBanner
import io.github.thesobiaakbar.adroit.internal.port.RequestExtras
import io.github.thesobiaakbar.adroit.internal.port.SdkBannerView
import io.github.thesobiaakbar.adroit.internal.reliability.AdUnitRotation
import io.github.thesobiaakbar.adroit.internal.reliability.RetryScheduler
import io.github.thesobiaakbar.adroit.internal.threading.postToMain
import io.github.thesobiaakbar.adroit.internal.time.Clock
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
 * [size] is fixed at creation (the code's size, else the placement's). Each new load takes the next ad unit from
 * [rotation]; a collapsible banner asks for the collapsible format only on the first load of each SDK view.
 *
 * Compose keeps a banner for its whole screen: [park] takes it off screen with its loaded ad but without a view or
 * Activity, and [showIn] shows that ad again in a new container, with no new request.
 *
 * Main thread only. After [destroy] every method is a no-op and the library holds no reference to the handle.
 */
internal class BannerAdHandleImpl(
    activity: Activity,
    placement: EffectivePlacement,
    private var adUnitIds: List<String>,
    private val size: BannerSize,
    private val options: BannerOptions,
    private val config: LiveConfig,
    private val adsSdk: AdsSdk,
    private val readiness: Readiness,
    private val retry: RetryScheduler,
    private val events: EventBus,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val log: AdLog,
    private val rotation: AdUnitRotation,
    /** Banners loaded ahead of their view (`AdSlots.load`): the first load takes one when it fits. */
    private val preloader: BannerPreloader? = null,
) : BannerAdHandle {
    /** The host-facing view; null while off screen ([park]). */
    private var container: BannerContainer? = BannerContainer(activity, this)

    /** The loaded ad while the banner is off screen ([park]), with the size it was loaded at. */
    private var parked: PreloadedBanner? = null
    private var parkedSpec: BannerSizeSpec? = null
    private val mutableState = MutableStateFlow<BannerState>(BannerState.Loading)
    private val bannerPlacement = placement.placement
    private val retryPolicy = placement.retry
    private val key = "BANNER:${bannerPlacement.key}@${System.identityHashCode(this)}"
    private val placementKey get() = bannerPlacement.key

    private var sdkView: SdkBannerView? = null

    /** The ad unit of the latest load request. */
    private var adUnitId: String = adUnitIds.first()

    /** Loads made on the current [sdkView]; only the first may be collapsible. */
    private var loadsOnView = 0
    private var loadedSpec: BannerSizeSpec? = null
    private var generation = 0
    private var loadStartedAt = 0L
    private var lastSource: AdSourceInfo? = null
    private var failedAttempts = 0
    private var lastError: AdError? = null
    private var retryPending = false
    private var waitingForLayout = false

    /** Waiting for this placement's banner loading ahead of its view, instead of requesting another. */
    private var waitingForPreload = false
    private var collector: Job? = null
    private var boundOwner: LifecycleOwner? = null
    private var ownerObserver: LifecycleEventObserver? = null
    private var destroyed = false

    override val view: View get() = checkNotNull(container) { "Banner $placementKey is off screen: showIn() first" }

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
        // The container stays readable ([view]) after destroy, only emptied and detached.
        container?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.handle = null
        }
        mutableState.value = BannerState.Hidden(null)
        log.d { "Banner $placementKey destroyed" }
    }

    /**
     * Takes the banner off screen while its screen lives on (a composable left the composition): a loaded ad is kept
     * without its view, so nothing here keeps the Activity. A banner still loading, or failed, drops its request.
     */
    fun park() {
        if (destroyed || container == null) return
        val bannerView = sdkView
        val spec = loadedSpec
        val ad = if (bannerView != null && spec != null && mutableState.value == BannerState.Loaded) {
            sdkView = null // detached, not destroyed: tearDownAd leaves it
            bannerView.detach()
        } else {
            null
        }
        tearDownAd()
        parked = ad
        parkedSpec = spec.takeIf { ad != null }
        dropContainer()
        log.d { "Banner $placementKey off screen${if (ad != null) ", its ad kept" else ""}" }
    }

    /**
     * Puts the banner on screen in [activity], in a new container ([view]): its parked ad without a new request, else a
     * new load. A banner shown in another Activity (a configuration change) moves here the same way.
     */
    fun showIn(activity: Activity) {
        if (destroyed) return
        container?.let { if (it.context === activity) return else park() }
        container = BannerContainer(activity, this)
        val ad = parked
        val spec = parkedSpec
        parked = null
        parkedSpec = null
        if (ad != null && spec != null) {
            show(ad, adUnitId, spec, activity, "shown again, same ad")
        } else if (mutableState.value !is BannerState.Hidden) {
            requestLoad()
        }
    }

    private fun dropContainer() {
        val c = container ?: return
        (c.parent as? ViewGroup)?.removeView(c)
        c.handle = null
        container = null
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
        val units = current.bannerAdUnitIds(p, size)
        if (units != adUnitIds) {
            log.i { "Banner $placementKey: ad units changed to ${units.joinToString { current.displayAdUnitId(it) }}" }
            tearDownAd()
            adUnitIds = units
        }
        if (mutableState.value is BannerState.Hidden && state !is AdsState.Unavailable) mutableState.value = BannerState.Loading
        onReadiness(state)
    }

    private fun onReadiness(state: AdsState) {
        when (state) {
            AdsState.Ready -> if (sdkView == null && parked == null) requestLoad()
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
        val container = container ?: return // off screen: loads in showIn()
        if (!container.isLaidOut) {
            waitingForLayout = true
            return
        }
        waitingForLayout = false
        if (waitingForPreload) return
        val activity = container.context as Activity
        val spec = BannerSizeResolver.resolve(
            size,
            container.width,
            container.resources.displayMetrics.density,
            BannerSizeResolver.windowWidthPx(activity),
        )
        if (sdkView == null && preloader != null) {
            val token = ++generation
            val claimed = preloader.claim(placementKey, spec, adUnitIds) { preloaded ->
                if (destroyed || token != generation || sdkView != null) return@claim false
                waitingForPreload = false
                if (preloaded == null) {
                    requestLoad()
                } else {
                    show(preloaded.banner, preloaded.adUnitId, spec, activity, "shown from its load ahead of the view")
                }
                true
            }
            if (claimed) {
                // Shown now if it was loaded; otherwise when its load completes.
                if (sdkView == null) waitingForPreload = true
                return
            }
        }
        val bannerView = sdkView ?: adsSdk.createBannerView(activity).also { created ->
            sdkView = created
            loadsOnView = 0
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

    /** Shows a loaded ad (loaded ahead of this view, or parked): attached as this handle's SDK view. */
    private fun show(banner: PreloadedBanner, fromAdUnitId: String, spec: BannerSizeSpec, activity: Activity, how: String) {
        val container = checkNotNull(container)
        val loadGeneration = ++generation
        val bannerView = banner.attach(activity, Listener(loadGeneration))
        sdkView = bannerView
        loadsOnView = 1
        loadedSpec = spec
        adUnitId = fromAdUnitId
        container.addView(
            bannerView.view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL,
            ),
        )
        expectedHeightPx = bannerView.expectedHeightPx(spec.size, spec.widthDp)
        if (options.reserveSpace) container.minimumHeight = expectedHeightPx
        failedAttempts = 0
        mutableState.value = BannerState.Loaded
        log.i { "Banner $placementKey $how (${logId()})" }
    }

    private fun load(bannerView: SdkBannerView, spec: BannerSizeSpec) {
        loadedSpec = spec
        val loadGeneration = ++generation
        loadStartedAt = clock.elapsedRealtimeMillis()
        if (mutableState.value !is BannerState.Loaded) mutableState.value = BannerState.Loading
        val pick = rotation.next(AdFormat.BANNER, adUnitIds)
        adUnitId = pick.adUnitId
        emit(AdEvent.LoadRequested(placementKey, AdFormat.BANNER, adUnitId, now()))
        log.d { "Loading banner $placementKey (${logId()})${pick.describe} at ${spec.widthDp} dp, ${spec.size}" }
        val collapsible = (spec.size as? BannerSize.Collapsible)?.position?.takeIf { loadsOnView == 0 }
        loadsOnView++
        @OptIn(GoogleSdkEscapeHatch::class)
        val extras = RequestExtras(bannerPlacement, config.current.source.advanced.googleRequestCustomizer, collapsible)
        bannerView.load(adUnitId, spec.size, spec.widthDp, extras, Listener(loadGeneration))
    }

    /** Destroys the SDK view, if any, and stops retrying. The container and handle stay usable. */
    private fun tearDownAd() {
        generation++
        retry.cancel(key)
        retryPending = false
        failedAttempts = 0
        waitingForLayout = false
        waitingForPreload = false
        loadedSpec = null
        parked?.destroy()
        parked = null
        parkedSpec = null
        val bannerView = sdkView ?: return
        sdkView = null
        container?.removeView(bannerView.view)
        bannerView.destroy()
        container?.minimumHeight = 0
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
        if (container?.isAttachedAndShown != true) {
            retryPending = true
            log.d { "Banner $placementKey: retry paused while not attached and shown" }
            return
        }
        retry.schedule(key, failedAttempts, retryPolicy, error, retryable = true) {
            val bannerView = sdkView
            val spec = loadedSpec
            when {
                destroyed || bannerView == null || spec == null || !readiness.canLoad() -> Unit
                container?.isAttachedAndShown == true -> load(bannerView, spec)
                else -> retryPending = true
            }
        }
    }

    internal fun onAttachedOrShown() {
        if (destroyed || !retryPending || container?.isAttachedAndShown != true) return
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
        val newWidthDp = (widthPx / (container ?: return).resources.displayMetrics.density).toInt()
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
