package io.github.thesobiaakbar.adroit.fakes

import android.app.Activity
import android.content.Context
import android.view.View
import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.AdFormat
import io.github.thesobiaakbar.adroit.BannerSize
import io.github.thesobiaakbar.adroit.NativeAdAssets
import io.github.thesobiaakbar.adroit.Reward
import io.github.thesobiaakbar.adroit.events.AdRevenue
import io.github.thesobiaakbar.adroit.events.AdSourceInfo
import io.github.thesobiaakbar.adroit.events.RevenuePrecision
import io.github.thesobiaakbar.adroit.internal.port.AdsSdk
import io.github.thesobiaakbar.adroit.internal.port.BannerListener
import io.github.thesobiaakbar.adroit.internal.port.FullScreenAdListener
import io.github.thesobiaakbar.adroit.internal.port.LoadedFullScreenAd
import io.github.thesobiaakbar.adroit.internal.port.LoadedNativeAd
import io.github.thesobiaakbar.adroit.internal.port.NativeAdListener
import io.github.thesobiaakbar.adroit.internal.port.PreloadedBanner
import io.github.thesobiaakbar.adroit.internal.port.RequestExtras
import io.github.thesobiaakbar.adroit.internal.port.SdkBannerView
import io.github.thesobiaakbar.adroit.internal.port.SdkInitResult
import io.github.thesobiaakbar.adroit.internal.port.SdkLoadResult
import io.github.thesobiaakbar.adroit.internal.port.SdkRequestConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Scriptable [AdsSdk] for engine tests. Results are delivered through [scope] (use the test's main dispatcher), so
 * they are never synchronous and delays follow virtual time.
 *
 * Loads are scripted per ad unit with [enqueueLoad]; each load takes the next script for its ad unit, or
 * [defaultLoad] when none is queued. Every call is recorded.
 */
internal class FakeAdsSdk(private val scope: CoroutineScope) : AdsSdk {
    /** What a scripted load does. */
    sealed interface ScriptedLoad {
        /** Succeeds with a new fake ad for the requested format. */
        data class Success(val source: AdSourceInfo? = null) : ScriptedLoad

        data class Failure(val error: AdError, val retryable: Boolean) : ScriptedLoad

        /** Never completes, for timeout tests. */
        data object Pending : ScriptedLoad
    }

    data class InitializeCall(
        val appId: String,
        val request: SdkRequestConfig,
        val disableCrashReporting: Boolean,
    )

    data class LoadCall(val format: AdFormat, val adUnitId: String, val extras: RequestExtras)

    override var sdkVersion: String? = "fake-1.0.0"

    var initResult: SdkInitResult = SdkInitResult.Success
    var initDelayMs: Long = 0
    var defaultLoad: ScriptedLoad = ScriptedLoad.Success()
    var adInspectorResult: AdError? = null

    /** Called for every load request, before it's scripted. Tests use it to assert when loads may happen. */
    var onLoad: ((LoadCall) -> Unit)? = null

    /** Called at the start of every [initialize] call. Tests use it to check what's in place before the SDK starts. */
    var onInitialize: ((Context) -> Unit)? = null

    val initializeCalls = mutableListOf<InitializeCall>()

    /** The thread each [initialize] call was made on. The port is called on main; the adapter moves off it. */
    val initializeThreads = mutableListOf<Thread>()
    val requestConfigUpdates = mutableListOf<SdkRequestConfig>()
    val loadCalls = mutableListOf<LoadCall>()
    val fullScreenAds = mutableListOf<FakeLoadedFullScreenAd>()
    val nativeAds = mutableListOf<FakeLoadedNativeAd>()
    val bannerViews = mutableListOf<FakeSdkBannerView>()
    var adInspectorOpenCount: Int = 0
        private set

    private val scripts = mutableMapOf<String, ArrayDeque<Pair<ScriptedLoad, Long>>>()

    fun enqueueLoad(adUnitId: String, result: ScriptedLoad, delayMs: Long = 0) {
        scripts.getOrPut(adUnitId) { ArrayDeque() }.addLast(result to delayMs)
    }

    fun loadCount(adUnitId: String): Int = loadCalls.count { it.adUnitId == adUnitId }

    override fun initialize(
        appContext: Context,
        appId: String,
        request: SdkRequestConfig,
        disableCrashReporting: Boolean,
        beforeInit: () -> Unit,
        onComplete: (SdkInitResult) -> Unit,
    ) {
        beforeInit()
        onInitialize?.invoke(appContext)
        initializeCalls += InitializeCall(appId, request, disableCrashReporting)
        initializeThreads += Thread.currentThread()
        val result = initResult
        scope.launch {
            delay(initDelayMs)
            onComplete(result)
        }
    }

    override fun updateRequestConfig(request: SdkRequestConfig) {
        requestConfigUpdates += request
    }

    override fun loadFullScreen(
        format: AdFormat,
        adUnitId: String,
        extras: RequestExtras,
        onResult: (SdkLoadResult<LoadedFullScreenAd>) -> Unit,
    ) = load(format, adUnitId, extras, onResult) { source ->
        FakeLoadedFullScreenAd(format, source).also { fullScreenAds += it }
    }

    override fun loadNative(adUnitId: String, extras: RequestExtras, onResult: (SdkLoadResult<LoadedNativeAd>) -> Unit) =
        load(AdFormat.NATIVE, adUnitId, extras, onResult) { source -> FakeLoadedNativeAd(source).also { nativeAds += it } }

    private fun <T> load(
        format: AdFormat,
        adUnitId: String,
        extras: RequestExtras,
        onResult: (SdkLoadResult<T>) -> Unit,
        newAd: (AdSourceInfo?) -> T,
    ) {
        val call = LoadCall(format, adUnitId, extras)
        onLoad?.invoke(call)
        loadCalls += call
        val (script, delayMs) = scripts[adUnitId]?.removeFirstOrNull() ?: (defaultLoad to 0L)
        if (script == ScriptedLoad.Pending) return
        scope.launch {
            delay(delayMs)
            onResult(
                when (script) {
                    is ScriptedLoad.Success -> SdkLoadResult.Success(newAd(script.source), script.source)
                    is ScriptedLoad.Failure -> SdkLoadResult.Failure(script.error, script.retryable)
                    ScriptedLoad.Pending -> error("unreachable")
                },
            )
        }
    }

    override fun createBannerView(activity: Activity): SdkBannerView =
        FakeSdkBannerView(View(activity)).also { bannerViews += it }

    /** Banner loads without a view ([preloadBanner]), completed by the test with [FakeBannerPreload.succeed] or [FakeBannerPreload.fail]. */
    val bannerPreloads = mutableListOf<FakeBannerPreload>()

    override fun preloadBanner(
        context: Context,
        adUnitId: String,
        size: BannerSize,
        widthDp: Int,
        extras: RequestExtras,
        onResult: (SdkLoadResult<PreloadedBanner>) -> Unit,
    ) {
        bannerPreloads += FakeBannerPreload(adUnitId, size, widthDp, onResult)
    }

    override fun openAdInspector(onClosed: (AdError?) -> Unit) {
        adInspectorOpenCount++
        val result = adInspectorResult
        scope.launch { onClosed(result) }
    }
}

/** A loaded full-screen ad whose SDK events are triggered by the test. */
internal class FakeLoadedFullScreenAd(
    override val format: AdFormat,
    override val source: AdSourceInfo? = null,
) : LoadedFullScreenAd {
    var serverSideVerificationData: String? = null
        private set
    val shownIn = mutableListOf<Activity>()

    /** The server-side verification data at the moment [show] was called. */
    var serverSideVerificationDataAtShow: String? = null
        private set
    var listener: FullScreenAdListener? = null
        private set
    var released: Boolean = false
        private set

    val showCount: Int get() = shownIn.size

    override fun setServerSideVerification(customData: String?) {
        serverSideVerificationData = customData
    }

    override fun show(activity: Activity, listener: FullScreenAdListener) {
        serverSideVerificationDataAtShow = serverSideVerificationData
        shownIn += activity
        this.listener = listener
    }

    override fun release() {
        released = true
        listener = null
    }

    fun simulateShown() = requireListener().onShown()

    fun simulateImpression() = requireListener().onImpression()

    fun simulateClicked() = requireListener().onClicked()

    fun simulatePaid(revenue: AdRevenue = AdRevenue(1_000, "USD", RevenuePrecision.PRECISE)) = requireListener().onPaid(revenue)

    fun simulateReward(reward: Reward = Reward("coins", 10)) = requireListener().onRewardEarned(reward)

    fun simulateDismissed() = requireListener().onDismissed()

    fun simulateFailedToShow(error: AdError = AdError.ShowFailed()) = requireListener().onFailedToShow(error)

    /** Shown, impression, then dismissed: the common happy path. */
    fun simulateFullShow() {
        simulateShown()
        simulateImpression()
        simulateDismissed()
    }

    private fun requireListener() = checkNotNull(listener) { "show() was not called, or the ad was released" }
}

/** A loaded native ad whose SDK events are triggered by the test. */
internal class FakeLoadedNativeAd(
    override val source: AdSourceInfo? = null,
    override val assets: NativeAdAssets = defaultAssets(),
) : LoadedNativeAd {
    var listener: NativeAdListener? = null
        private set
    var destroyCount: Int = 0
        private set
    val isDestroyed: Boolean get() = destroyCount > 0

    override fun setListener(listener: NativeAdListener?) {
        this.listener = listener
    }

    override fun destroy() {
        destroyCount++
        listener = null
    }

    override fun bindInternal(): Any = this

    fun simulateImpression() = listener?.onImpression()

    fun simulateClicked() = listener?.onClicked()

    fun simulatePaid(revenue: AdRevenue = AdRevenue(1_000, "USD", RevenuePrecision.PRECISE)) = listener?.onPaid(revenue)

    companion object {
        fun defaultAssets() = NativeAdAssets(
            headline = "Test headline",
            body = "Test body",
            callToAction = "Install",
            advertiser = "Test advertiser",
            starRating = 4.5,
            price = null,
            store = null,
            icon = null,
            hasVideo = false,
            mediaAspectRatio = null,
        )
    }
}

/** A banner view whose loads are completed by the test. */
internal class FakeSdkBannerView(override val view: View) : SdkBannerView {
    data class LoadCall(val adUnitId: String, val size: BannerSize, val widthDp: Int, val extras: RequestExtras)

    var heightPx: Int = 150
    val loadCalls = mutableListOf<LoadCall>()
    var listener: BannerListener? = null
        private set
    var destroyCount: Int = 0
        private set
    val destroyed: Boolean get() = destroyCount > 0

    /** The parent container's minimum height when each load started, to check space is reserved first. */
    val parentMinHeightAtLoad = mutableListOf<Int>()

    override fun load(adUnitId: String, size: BannerSize, widthDp: Int, extras: RequestExtras, listener: BannerListener) {
        loadCalls += LoadCall(adUnitId, size, widthDp, extras)
        parentMinHeightAtLoad += (view.parent as? View)?.minimumHeight ?: -1
        this.listener = listener
    }

    override fun expectedHeightPx(size: BannerSize, widthDp: Int): Int = heightPx

    override fun destroy() {
        destroyCount++
        listener = null
    }

    /** Whether an ad loaded in (or was attached to) this view. */
    private var hasAd = false

    /** The ad [detach] handed back, to show elsewhere. */
    var detachedAs: FakePreloadedBanner? = null
        private set

    override fun detach(): PreloadedBanner? {
        val ad = if (hasAd) FakePreloadedBanner().also { detachedAs = it } else null
        destroy()
        return ad
    }

    fun simulateLoaded(source: AdSourceInfo? = null, isRefresh: Boolean = false) {
        if (listener != null) hasAd = true
        listener?.onLoaded(source, isRefresh)
    }

    /** A preloaded banner's view: loaded already, its events going to [listener]. */
    fun adopt(listener: BannerListener) {
        this.listener = listener
        hasAd = true
    }

    fun simulateFailedToLoad(error: AdError = AdError.NoFill(), retryable: Boolean = true, isRefresh: Boolean = false) =
        listener?.onFailedToLoad(error, retryable, isRefresh)

    fun simulateImpression() = listener?.onImpression()

    fun simulateClicked() = listener?.onClicked()

    fun simulatePaid(revenue: AdRevenue = AdRevenue(1_000, "USD", RevenuePrecision.PRECISE)) = listener?.onPaid(revenue)
}

/** A banner load without a view, pending until the test completes it. */
internal class FakeBannerPreload(
    val adUnitId: String,
    val size: BannerSize,
    val widthDp: Int,
    private val onResult: (SdkLoadResult<PreloadedBanner>) -> Unit,
) {
    val banner = FakePreloadedBanner()

    fun succeed(source: AdSourceInfo? = null) = onResult(SdkLoadResult.Success(banner, source))

    fun fail(error: AdError = AdError.NoFill()) = onResult(SdkLoadResult.Failure(error, retryable = true))
}

/** A preloaded banner: records where it's attached, and whether it was destroyed unshown. */
internal class FakePreloadedBanner : PreloadedBanner {
    var attachedView: FakeSdkBannerView? = null
        private set
    var destroyedUnshown = false
        private set

    override fun attach(activity: Activity, listener: BannerListener): SdkBannerView {
        check(attachedView == null) { "attached twice" }
        return FakeSdkBannerView(View(activity)).also {
            it.adopt(listener)
            attachedView = it
        }
    }

    override fun destroy() {
        destroyedUnshown = true
    }
}
