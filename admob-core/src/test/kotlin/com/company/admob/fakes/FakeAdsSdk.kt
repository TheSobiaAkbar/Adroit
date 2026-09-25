package com.company.admob.fakes

import android.app.Activity
import android.content.Context
import android.view.View
import com.company.admob.AdError
import com.company.admob.AdFormat
import com.company.admob.BannerSize
import com.company.admob.NativeAdAssets
import com.company.admob.Reward
import com.company.admob.events.AdRevenue
import com.company.admob.events.AdSourceInfo
import com.company.admob.events.RevenuePrecision
import com.company.admob.internal.port.AdsSdk
import com.company.admob.internal.port.BannerListener
import com.company.admob.internal.port.FullScreenAdListener
import com.company.admob.internal.port.LoadedFullScreenAd
import com.company.admob.internal.port.LoadedNativeAd
import com.company.admob.internal.port.NativeAdListener
import com.company.admob.internal.port.RequestExtras
import com.company.admob.internal.port.SdkBannerView
import com.company.admob.internal.port.SdkInitResult
import com.company.admob.internal.port.SdkLoadResult
import com.company.admob.internal.port.SdkRequestConfig
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
        onComplete: (SdkInitResult) -> Unit,
    ) {
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
    var listener: FullScreenAdListener? = null
        private set
    var released: Boolean = false
        private set

    val showCount: Int get() = shownIn.size

    override fun setServerSideVerification(customData: String?) {
        serverSideVerificationData = customData
    }

    override fun show(activity: Activity, listener: FullScreenAdListener) {
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
    var destroyed: Boolean = false
        private set

    override fun load(adUnitId: String, size: BannerSize, widthDp: Int, extras: RequestExtras, listener: BannerListener) {
        loadCalls += LoadCall(adUnitId, size, widthDp, extras)
        this.listener = listener
    }

    override fun expectedHeightPx(size: BannerSize, widthDp: Int): Int = heightPx

    override fun destroy() {
        destroyed = true
        listener = null
    }

    fun simulateLoaded(source: AdSourceInfo? = null, isRefresh: Boolean = false) = listener?.onLoaded(source, isRefresh)

    fun simulateFailedToLoad(error: AdError = AdError.NoFill(), retryable: Boolean = true, isRefresh: Boolean = false) =
        listener?.onFailedToLoad(error, retryable, isRefresh)

    fun simulateImpression() = listener?.onImpression()
}
