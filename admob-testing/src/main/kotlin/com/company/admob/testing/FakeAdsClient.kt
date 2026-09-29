package com.company.admob.testing

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import com.company.admob.AdAvailability
import com.company.admob.AdError
import com.company.admob.AdPlacement
import com.company.admob.AdsClient
import com.company.admob.AdsState
import com.company.admob.AppOpenApi
import com.company.admob.AppOpenPlacement
import com.company.admob.BannerAdHandle
import com.company.admob.BannerApi
import com.company.admob.BannerOptions
import com.company.admob.BannerPlacement
import com.company.admob.BannerState
import com.company.admob.CompanyAdMobVersion
import com.company.admob.ConsentState
import com.company.admob.DiagnosticsApi
import com.company.admob.DiagnosticsReport
import com.company.admob.FullScreenResult
import com.company.admob.InterstitialApi
import com.company.admob.InterstitialPlacement
import com.company.admob.NativeAdAssets
import com.company.admob.NativeAdHandle
import com.company.admob.NativeAdLayout
import com.company.admob.NativeAdRenderer
import com.company.admob.NativeAdResult
import com.company.admob.NativeApi
import com.company.admob.NativePlacement
import com.company.admob.NativeStyle
import com.company.admob.PrivacyApi
import com.company.admob.RewardResult
import com.company.admob.RewardedApi
import com.company.admob.RewardedInterstitialApi
import com.company.admob.RewardedInterstitialPlacement
import com.company.admob.RewardedPlacement
import com.company.admob.RewardedShowOptions
import com.company.admob.UnavailableReason
import com.company.admob.events.AdEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import kotlin.time.Duration

/**
 * An [AdsClient] for host tests: no Google SDK, no device, and no Android main looper for the non-View APIs, so
 * ViewModel and repository tests run on the plain JVM. Inject it where the app injects `AdsClient`; for code that
 * calls the `CompanyAdMob` object directly, use [CompanyAdMobTestRule].
 *
 * Script it with [setState], the `enqueue…Result` functions, [setAvailability] and [emit], then assert on
 * [recordedCalls]. Show calls take the next queued result for their placement; with an empty queue they report
 * `NotShown(NotReady)` or `RewardResult.Failed(NotReady)`, as the library does when no ad is cached. `native.load`
 * delivers queued results, or else a [FakeNativeAdHandle] with [nativeAssets].
 *
 * **Differs from production:** callbacks run synchronously, inside the call, on the calling thread, unless you pass a
 * [callbackExecutor] (for example `dispatcher.asExecutor()` or a main-thread executor). The real library always
 * delivers them later, on the main thread. Nothing here enforces frequency limits, consent, or readiness: the fake
 * answers exactly what the test scripted.
 *
 * Thread-safe for scripting and recording. `banner.create` and `native.render` create Views, so they need an Android
 * runtime (a device or Robolectric).
 */
public class FakeAdsClient @JvmOverloads constructor(
    initialState: AdsState = AdsState.Ready,
    private val callbackExecutor: Executor = Executor(Runnable::run),
) : AdsClient {
    private val lock = Any()
    private val calls = mutableListOf<FakeCall>()
    private val mutableState = MutableStateFlow(initialState)
    private val mutableEvents = MutableSharedFlow<AdEvent>(
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val emitted = mutableListOf<AdEvent>()
    private val interstitialResults = mutableMapOf<String, ArrayDeque<FullScreenResult>>()
    private val rewardedResults = mutableMapOf<String, ArrayDeque<RewardResult>>()
    private val rewardedInterstitialResults = mutableMapOf<String, ArrayDeque<RewardResult>>()
    private val appOpenResults = ArrayDeque<FullScreenResult>()
    private val nativeResults = mutableMapOf<String, ArrayDeque<NativeAdResult>>()
    private val availabilities = mutableMapOf<AdPlacement, MutableStateFlow<AdAvailability>>()
    private val appOpenAvailability = MutableStateFlow<AdAvailability>(AdAvailability.Unavailable)
    private val banners = mutableListOf<FakeBannerAdHandle>()
    private val nativeHandles = mutableListOf<FakeNativeAdHandle>()
    private val consent = MutableStateFlow(ConsentState.Unknown)
    private val privacyOptionsRequired = MutableStateFlow(false)
    private val unclaimedRewarded = Channel<RewardResult.Earned>(Channel.UNLIMITED)
    private val unclaimedRewardedInterstitial = Channel<RewardResult.Earned>(Channel.UNLIMITED)

    /** Every call received so far, oldest first. */
    public val recordedCalls: List<FakeCall> get() = synchronized(lock) { calls.toList() }

    /** Every event passed to [emit], oldest first. Also what `diagnostics.recentEvents` returns. */
    public val emittedEvents: List<AdEvent> get() = synchronized(lock) { emitted.toList() }

    /** The banners made by `banner.create`, oldest first. */
    public val createdBanners: List<FakeBannerAdHandle> get() = synchronized(lock) { banners.toList() }

    /** The native handles delivered by `native.load` (default ones only), oldest first. */
    public val loadedNativeAds: List<FakeNativeAdHandle> get() = synchronized(lock) { nativeHandles.toList() }

    /** The last value passed to `setAdFree`. */
    @Volatile
    public var isAdFree: Boolean = false
        private set

    /** The state a new banner starts in. */
    @Volatile
    public var initialBannerState: BannerState = BannerState.Loaded

    /** The assets of the native ads `native.load` delivers when nothing is queued. */
    @Volatile
    public var nativeAssets: NativeAdAssets = DEFAULT_NATIVE_ASSETS

    /** Sets [state]. */
    public fun setState(state: AdsState) {
        mutableState.value = state
    }

    /** Queues the result of the next interstitial show for [placement]. */
    public fun enqueueInterstitialResult(placement: InterstitialPlacement, result: FullScreenResult) {
        synchronized(lock) { interstitialResults.getOrPut(placement.key) { ArrayDeque() }.addLast(result) }
    }

    /** Queues the result of the next rewarded show for [placement]. */
    public fun enqueueRewardResult(placement: RewardedPlacement, result: RewardResult) {
        synchronized(lock) { rewardedResults.getOrPut(placement.key) { ArrayDeque() }.addLast(result) }
    }

    /** Queues the result of the next rewarded interstitial show for [placement]. */
    public fun enqueueRewardedInterstitialResult(placement: RewardedInterstitialPlacement, result: RewardResult) {
        synchronized(lock) { rewardedInterstitialResults.getOrPut(placement.key) { ArrayDeque() }.addLast(result) }
    }

    /** Queues the result of the next `appOpen.showOnColdStart`. */
    public fun enqueueAppOpenResult(result: FullScreenResult) {
        synchronized(lock) { appOpenResults.addLast(result) }
    }

    /** Queues the result of the next `native.load` for [placement]. */
    public fun enqueueNativeResult(placement: NativePlacement, result: NativeAdResult) {
        synchronized(lock) { nativeResults.getOrPut(placement.key) { ArrayDeque() }.addLast(result) }
    }

    /**
     * Sets what `availability(placement)` reports (`appOpen.availability` for an app open placement). Every
     * placement starts [AdAvailability.Unavailable]. A format API's `isReady` is true only for
     * [AdAvailability.Ready].
     */
    public fun setAvailability(placement: AdPlacement, availability: AdAvailability) {
        availabilityFlow(placement).value = availability
    }

    /** Emits [event] to [events] and records it in [emittedEvents]. */
    public fun emit(event: AdEvent) {
        synchronized(lock) { emitted += event }
        mutableEvents.tryEmit(event)
    }

    /** Sends [reward] to `rewarded.unclaimedResults`, as the library does when a `showAndAwait` caller was cancelled. */
    public fun emitUnclaimedReward(reward: RewardResult.Earned) {
        unclaimedRewarded.trySend(reward)
    }

    /** Sends [reward] to `rewardedInterstitial.unclaimedResults`. */
    public fun emitUnclaimedRewardedInterstitialReward(reward: RewardResult.Earned) {
        unclaimedRewardedInterstitial.trySend(reward)
    }

    /** Forgets [recordedCalls] and [emittedEvents]. Queued results and availability stay. */
    public fun clearRecordedCalls() {
        synchronized(lock) {
            calls.clear()
            emitted.clear()
        }
    }

    // --- AdsClient ---

    override val state: StateFlow<AdsState> = mutableState.asStateFlow()

    override val isReady: Boolean get() = mutableState.value == AdsState.Ready

    override val events: SharedFlow<AdEvent> = mutableEvents.asSharedFlow()

    override fun start(activity: Activity, onComplete: ((AdsState) -> Unit)?) {
        record(FakeCall.Start)
        onComplete?.let { callback -> deliver { callback(mutableState.value) } }
    }

    /** Waits for `Ready` or a settled `Unavailable`/`Failed` state set with [setState]. */
    override suspend fun awaitReady(timeout: Duration): Boolean = withTimeoutOrNull(timeout) {
        mutableState.first { it == AdsState.Ready || it is AdsState.Unavailable || it is AdsState.Failed }
    } == AdsState.Ready

    /** Records the call. `true` moves a `Ready` state to `Unavailable(AD_FREE_USER)`, and `false` moves it back. */
    override fun setAdFree(adFree: Boolean) {
        record(FakeCall.SetAdFree(adFree))
        isAdFree = adFree
        val adFreeState = AdsState.Unavailable(UnavailableReason.AD_FREE_USER)
        if (adFree && mutableState.value == AdsState.Ready) mutableState.value = adFreeState
        if (!adFree && mutableState.value == adFreeState) mutableState.value = AdsState.Ready
    }

    override fun showInterstitial(activity: Activity, placement: InterstitialPlacement, onResult: (FullScreenResult) -> Unit): Unit =
        interstitial.show(activity, placement, onResult)

    override fun showRewarded(activity: Activity, placement: RewardedPlacement, onResult: (RewardResult) -> Unit): Unit =
        rewarded.show(activity, placement, RewardedShowOptions(), onResult)

    override fun preload(vararg placements: AdPlacement) {
        record(FakeCall.Preload(placements.map { it.key }))
    }

    override val privacy: PrivacyApi = object : PrivacyApi {
        override val consentState: StateFlow<ConsentState> = consent.asStateFlow()

        override val isPrivacyOptionsRequired: StateFlow<Boolean> = privacyOptionsRequired.asStateFlow()

        override fun showPrivacyOptions(activity: Activity, onDismissed: (AdError?) -> Unit) {
            record(FakeCall.ShowPrivacyOptions)
            deliver { onDismissed(null) }
        }

        override fun resetConsentForTesting() {
            record(FakeCall.ResetConsent)
        }
    }

    override val interstitial: InterstitialApi = object : InterstitialApi, FullScreenFake<InterstitialPlacement>() {
        override fun show(activity: Activity, placement: InterstitialPlacement, onResult: (FullScreenResult) -> Unit) {
            val result = nextInterstitial(placement)
            deliver { onResult(result) }
        }

        override suspend fun showAndAwait(activity: Activity, placement: InterstitialPlacement): FullScreenResult =
            nextInterstitial(placement)

        override fun showOnSplash(activity: Activity, placement: InterstitialPlacement, onResult: (FullScreenResult) -> Unit) {
            val result = nextInterstitial(placement, FakeCall.ShowInterstitialOnSplash(placement.key))
            deliver { onResult(result) }
        }
    }

    override val rewarded: RewardedApi = object : RewardedApi, FullScreenFake<RewardedPlacement>() {
        override fun show(
            activity: Activity,
            placement: RewardedPlacement,
            options: RewardedShowOptions,
            onResult: (RewardResult) -> Unit,
        ) {
            val result = nextReward(rewardedResults, placement, FakeCall.ShowRewarded(placement.key, options.serverSideVerificationCustomData))
            deliver { onResult(result) }
        }

        override suspend fun showAndAwait(activity: Activity, placement: RewardedPlacement, options: RewardedShowOptions): RewardResult =
            nextReward(rewardedResults, placement, FakeCall.ShowRewarded(placement.key, options.serverSideVerificationCustomData))

        override val unclaimedResults: Flow<RewardResult.Earned> = unclaimedRewarded.receiveAsFlow()
    }

    override val rewardedInterstitial: RewardedInterstitialApi =
        object : RewardedInterstitialApi, FullScreenFake<RewardedInterstitialPlacement>() {
            override fun show(
                activity: Activity,
                placement: RewardedInterstitialPlacement,
                options: RewardedShowOptions,
                onResult: (RewardResult) -> Unit,
            ) {
                val call = FakeCall.ShowRewardedInterstitial(placement.key, options.serverSideVerificationCustomData)
                val result = nextReward(rewardedInterstitialResults, placement, call)
                deliver { onResult(result) }
            }

            override suspend fun showAndAwait(
                activity: Activity,
                placement: RewardedInterstitialPlacement,
                options: RewardedShowOptions,
            ): RewardResult {
                val call = FakeCall.ShowRewardedInterstitial(placement.key, options.serverSideVerificationCustomData)
                return nextReward(rewardedInterstitialResults, placement, call)
            }

            override val unclaimedResults: Flow<RewardResult.Earned> = unclaimedRewardedInterstitial.receiveAsFlow()
        }

    override val appOpen: AppOpenApi = object : AppOpenApi {
        override fun showOnColdStart(activity: Activity, maxWait: Duration, onResult: (FullScreenResult) -> Unit) {
            record(FakeCall.ShowAppOpenOnColdStart)
            val result = synchronized(lock) { appOpenResults.removeFirstOrNull() } ?: NOT_READY
            deliver { onResult(result) }
        }

        override fun skipNextForeground() = record(FakeCall.SkipNextAppOpen)

        override fun suppressWhile(owner: LifecycleOwner) = record(FakeCall.SuppressAppOpen)

        override fun setEnabled(enabled: Boolean) = record(FakeCall.SetAppOpenEnabled(enabled))

        override val availability: StateFlow<AdAvailability> = appOpenAvailability.asStateFlow()
    }

    override val banner: BannerApi = object : BannerApi {
        override fun create(activity: Activity, placement: BannerPlacement, options: BannerOptions): BannerAdHandle {
            record(FakeCall.CreateBanner(placement.key))
            val handle = FakeBannerAdHandle(activity, placement, options, initialBannerState)
            synchronized(lock) { banners += handle }
            return handle
        }
    }

    override val native: NativeApi = object : NativeApi {
        override fun preload(placement: NativePlacement) = record(FakeCall.Preload(listOf(placement.key)))

        override fun load(placement: NativePlacement, onResult: (NativeAdResult) -> Unit) {
            val result = nextNative(placement)
            deliver { onResult(result) }
        }

        override suspend fun load(placement: NativePlacement): NativeAdResult = nextNative(placement)

        override fun style(placement: NativePlacement): NativeStyle =
            synchronized(lock) { nativeStyles[placement.key] } ?: NativeStyle.LARGE_BUTTON_BOTTOM

        /**
         * Inflates [layout] into [container] (replacing its children) and fills the asset views, hiding those the ad
         * has no asset for, so templates can be screenshot-tested. Throws like the real `render`.
         */
        override fun render(
            handle: NativeAdHandle,
            container: ViewGroup,
            layout: NativeAdLayout,
            renderer: NativeAdRenderer?,
        ): View {
            record(FakeCall.RenderNative(handle.placement.key))
            check(!handle.isDestroyed) { "render(): the native ad for ${handle.placement.key} was destroyed or blocked" }
            val root = LayoutInflater.from(container.context).inflate(layout.layoutRes, container, false)
            val headline = requireNotNull(root.findViewById<TextView>(layout.headlineId)) {
                "render(): the layout has no headline view (NativeAdLayout.headlineId)"
            }
            val assets = handle.assets
            headline.text = assets.headline
            root.text(layout.bodyId, assets.body)
            root.text(layout.callToActionId, assets.callToAction)
            root.text(layout.advertiserId, assets.advertiser)
            root.text(layout.priceId, assets.price)
            root.text(layout.storeId, assets.store)
            root.find<RatingBar>(layout.starRatingId)?.let { stars ->
                val rating = assets.starRating
                stars.visibility = if (rating == null) View.GONE else View.VISIBLE
                if (rating != null) stars.rating = rating.toFloat()
            }
            root.find<ImageView>(layout.iconId)?.let { icon ->
                icon.setImageDrawable(assets.icon)
                icon.visibility = if (assets.icon == null) View.GONE else View.VISIBLE
            }
            renderer?.onBind(root, assets)
            container.removeAllViews()
            container.addView(root)
            return root
        }
    }

    override val diagnostics: DiagnosticsApi = object : DiagnosticsApi {
        override fun snapshot(): DiagnosticsReport = DiagnosticsReport(
            libraryVersion = CompanyAdMobVersion.NAME,
            googleSdkVersion = null,
            environment = null,
            state = mutableState.value,
            consent = consent.value,
            testMode = null,
            placements = emptyList(),
            activeFullScreen = null,
            currentActivity = null,
            lastError = null,
            activeOverrides = "none",
        )

        override fun openAdInspector(activity: Activity, onClosed: (AdError?) -> Unit) {
            record(FakeCall.OpenAdInspector)
            deliver { onClosed(null) }
        }

        override val recentEvents: List<AdEvent> get() = emittedEvents
    }

    // --- Consent scripting ---

    /** Sets what `native.style(placement)` reports. Every placement starts [NativeStyle.LARGE_BUTTON_BOTTOM]. */
    public fun setNativeStyle(placement: NativePlacement, style: NativeStyle) {
        synchronized(lock) { nativeStyles[placement.key] = style }
    }

    /** Sets `privacy.consentState` (and `isPrivacyOptionsRequired` from it). */
    public fun setConsentState(state: ConsentState) {
        consent.value = state
        privacyOptionsRequired.value = state.privacyOptionsRequired
    }

    // --- Internals ---

    private val nativeStyles = mutableMapOf<String, NativeStyle>()

    /** The parts shared by the three full-screen fakes. */
    private abstract inner class FullScreenFake<P : AdPlacement> {
        fun preload(placement: P) = record(FakeCall.Preload(listOf(placement.key)))

        fun availability(placement: P): StateFlow<AdAvailability> = availabilityFlow(placement).asStateFlow()

        fun isReady(placement: P): Boolean = availabilityFlow(placement).value == AdAvailability.Ready

        fun clear(placement: P) = record(FakeCall.Clear(placement.key))
    }

    private fun availabilityFlow(placement: AdPlacement): MutableStateFlow<AdAvailability> =
        if (placement is AppOpenPlacement) {
            appOpenAvailability
        } else {
            synchronized(lock) { availabilities.getOrPut(placement) { MutableStateFlow(AdAvailability.Unavailable) } }
        }

    private fun nextInterstitial(
        placement: InterstitialPlacement,
        call: FakeCall = FakeCall.ShowInterstitial(placement.key),
    ): FullScreenResult {
        record(call)
        return synchronized(lock) { interstitialResults[placement.key]?.removeFirstOrNull() } ?: NOT_READY
    }

    private fun nextReward(queues: MutableMap<String, ArrayDeque<RewardResult>>, placement: AdPlacement, call: FakeCall): RewardResult {
        record(call)
        return synchronized(lock) { queues[placement.key]?.removeFirstOrNull() } ?: RewardResult.Failed(AdError.NotReady)
    }

    private fun nextNative(placement: NativePlacement): NativeAdResult {
        record(FakeCall.LoadNative(placement.key))
        synchronized(lock) { nativeResults[placement.key]?.removeFirstOrNull() }?.let { return it }
        val handle = FakeNativeAdHandle(placement, nativeAssets)
        synchronized(lock) { nativeHandles += handle }
        return NativeAdResult.Loaded(handle)
    }

    private fun record(call: FakeCall) {
        synchronized(lock) { calls += call }
    }

    private fun deliver(block: () -> Unit) {
        callbackExecutor.execute(block)
    }

    private inline fun <reified V : View> View.find(id: Int): V? = if (id == 0) null else findViewById(id)

    private fun View.text(id: Int, value: String?) {
        val view = find<TextView>(id) ?: return
        view.text = value
        view.visibility = if (value == null) View.GONE else View.VISIBLE
    }

    /** Shared defaults for fakes. */
    public companion object {
        /** Assets of the default native ads: a headline, body, call to action and rating. No icon or media. */
        @JvmField
        public val DEFAULT_NATIVE_ASSETS: NativeAdAssets = NativeAdAssets(
            headline = "Test ad headline",
            body = "Test ad body text",
            callToAction = "Install",
            advertiser = "Test advertiser",
            starRating = 4.5,
            price = null,
            store = null,
            icon = null,
            hasVideo = false,
            mediaAspectRatio = null,
        )

        private const val EVENT_BUFFER = 64
        private val NOT_READY = FullScreenResult.NotShown(AdError.NotReady)
    }
}
