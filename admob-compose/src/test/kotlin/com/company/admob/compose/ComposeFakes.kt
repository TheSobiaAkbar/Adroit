package com.company.admob.compose

import com.company.admob.NativeStyle
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import com.company.admob.AdAvailability
import com.company.admob.AdError
import com.company.admob.AdsClient
import com.company.admob.BannerAdHandle
import com.company.admob.BannerApi
import com.company.admob.BannerOptions
import com.company.admob.BannerPlacement
import com.company.admob.BannerState
import com.company.admob.CompanyAdMob
import com.company.admob.FullScreenResult
import com.company.admob.InterstitialApi
import com.company.admob.InterstitialPlacement
import com.company.admob.NativeAdAssets
import com.company.admob.NativeAdHandle
import com.company.admob.NativeAdHandleState
import com.company.admob.NativeAdLayout
import com.company.admob.NativeAdRenderer
import com.company.admob.NativeAdResult
import com.company.admob.NativeApi
import com.company.admob.NativePlacement
import com.company.admob.RewardResult
import com.company.admob.RewardedApi
import com.company.admob.RewardedPlacement
import com.company.admob.RewardedShowOptions
import com.company.admob.UnavailableReason
import com.company.admob.compose.internal.ComposeRuntime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

internal val HOME = BannerPlacement("home")
internal val OTHER_BANNER = BannerPlacement("other")
internal val FEED = NativePlacement("feed")
internal val LEVEL_END = InterstitialPlacement("level_end")
internal val BONUS = RewardedPlacement("bonus")

/**
 * The public [AdsClient] with fake banner, native, interstitial and rewarded APIs; everything else is the
 * uninitialized facade. [install] makes the composables use it, and [detached] records native views dropped
 * through the ADR-012 hook.
 */
internal class TestAdsClient(
    override val banner: FakeBannerApi = FakeBannerApi(),
    override val native: FakeNativeApi = FakeNativeApi(),
    override val interstitial: FakeInterstitialApi = FakeInterstitialApi(),
    override val rewarded: FakeRewardedApi = FakeRewardedApi(),
) : AdsClient by CompanyAdMob.client {
    val detached = mutableListOf<NativeAdHandle>()

    fun install() = apply {
        ComposeRuntime.clientOverride = this
        ComposeRuntime.detachRenderedView = { detached += it }
    }

    companion object {
        private val defaultDetach = ComposeRuntime.detachRenderedView

        fun uninstall() {
            ComposeRuntime.clientOverride = null
            ComposeRuntime.detachRenderedView = defaultDetach
        }
    }
}

internal class FakeBannerApi : BannerApi {
    class Created(val activity: Activity, val placement: BannerPlacement, val options: BannerOptions, val handle: FakeBannerHandle)

    val created = mutableListOf<Created>()

    val live: List<FakeBannerHandle> get() = created.map { it.handle }.filterNot { it.isDestroyed }

    override fun create(activity: Activity, placement: BannerPlacement, options: BannerOptions): BannerAdHandle =
        FakeBannerHandle(activity).also { created += Created(activity, placement, options, it) }
}

/** A banner handle that follows core's contract: destroy is idempotent and removes the view from its parent. */
internal class FakeBannerHandle(activity: Activity) : BannerAdHandle {
    override val view: View = FrameLayout(activity)
    override val state = MutableStateFlow<BannerState>(BannerState.Loading)
    override val expectedHeightPx: Int = 100
    var destroyCount = 0
    val isDestroyed: Boolean get() = destroyCount > 0

    override fun bindTo(owner: LifecycleOwner) = error("the composables own the banner; bindTo isn't used")

    override fun destroy() {
        if (destroyCount++ > 0) return
        (view.parent as? ViewGroup)?.removeView(view)
        state.value = BannerState.Hidden(null)
    }
}

internal class FakeNativeHandle(override val placement: NativePlacement) : NativeAdHandle {
    override val assets = NativeAdAssets(
        headline = "Fake headline", body = "Fake body", callToAction = "Install", advertiser = null, starRating = 4.0,
        price = null, store = null, icon = null, hasVideo = false, mediaAspectRatio = null,
    )
    private val mutableState = MutableStateFlow<NativeAdHandleState>(NativeAdHandleState.Active)
    override val state: StateFlow<NativeAdHandleState> = mutableState
    override val isDestroyed: Boolean get() = mutableState.value != NativeAdHandleState.Active
    var destroyCount = 0

    override fun destroy() {
        destroyCount++
        mutableState.value = NativeAdHandleState.Destroyed
    }

    fun block() {
        mutableState.value = NativeAdHandleState.Blocked(UnavailableReason.AD_FREE_USER)
    }
}

/**
 * Loads [FakeNativeHandle]s (or [failWith]), delivering results posted to the main thread like core, and renders
 * by inflating the layout and setting the headline, keeping no reference to the views.
 */
internal class FakeNativeApi : NativeApi {
    override fun style(placement: NativePlacement): NativeStyle = style

    class Render(val handle: NativeAdHandle, val layout: NativeAdLayout, val container: ViewGroup)

    var failWith: AdError? = null

    /** What `style()` reports: the template the helpers pick when the code passes no layout. */
    var style: NativeStyle = NativeStyle.LARGE_BUTTON_BOTTOM
    val handles = mutableListOf<FakeNativeHandle>()
    val renders = mutableListOf<Render>()
    var loadCount = 0
        private set

    val live: List<FakeNativeHandle> get() = handles.filterNot { it.isDestroyed }

    private val main = Handler(Looper.getMainLooper())

    override fun preload(placement: NativePlacement) = Unit

    override fun load(placement: NativePlacement, onResult: (NativeAdResult) -> Unit) {
        loadCount++
        val result = failWith?.let { NativeAdResult.Failed(it) }
            ?: NativeAdResult.Loaded(FakeNativeHandle(placement).also { handles += it })
        if (deliverImmediately) main.post { onResult(result) } else pending += { onResult(result) }
    }

    /** False keeps results until [deliverPending], to observe the loading state. */
    var deliverImmediately = true
    private val pending = mutableListOf<() -> Unit>()

    fun deliverPending() {
        val due = pending.toList()
        pending.clear()
        due.forEach { it() }
    }

    override suspend fun load(placement: NativePlacement): NativeAdResult = error("not used by admob-compose")

    override fun render(handle: NativeAdHandle, container: ViewGroup, layout: NativeAdLayout, renderer: NativeAdRenderer?): View {
        check(!handle.isDestroyed) { "render(): destroyed" }
        val root = FrameLayout(container.context)
        LayoutInflater.from(container.context).inflate(layout.layoutRes, root, true)
        val headline = requireNotNull(root.findViewById<TextView>(layout.headlineId)) { "no headline view" }
        headline.text = handle.assets.headline
        renderer?.onBind(root, handle.assets)
        container.removeAllViews()
        container.addView(root)
        renders += Render(handle, layout, container)
        return root
    }
}

/** Records the Activity of each show and answers with [result], posted to the main thread like core. */
internal class FakeInterstitialApi : InterstitialApi {
    val shownOn = mutableListOf<Activity>()
    var result: FullScreenResult = FullScreenResult.Dismissed(clicked = false)
    val availabilityFlow = MutableStateFlow<AdAvailability>(AdAvailability.Unavailable)
    private val main = Handler(Looper.getMainLooper())

    override fun preload(placement: InterstitialPlacement) = Unit
    override fun availability(placement: InterstitialPlacement): StateFlow<AdAvailability> = availabilityFlow
    override fun isReady(placement: InterstitialPlacement): Boolean = availabilityFlow.value == AdAvailability.Ready
    override fun clear(placement: InterstitialPlacement) = Unit

    override fun show(activity: Activity, placement: InterstitialPlacement, onResult: (FullScreenResult) -> Unit) {
        shownOn += activity
        val result = result
        main.post { onResult(result) }
    }

    override suspend fun showAndAwait(activity: Activity, placement: InterstitialPlacement): FullScreenResult =
        error("not used by admob-compose")

    override fun showOnSplash(activity: Activity, placement: InterstitialPlacement, onResult: (FullScreenResult) -> Unit) =
        error("not used by admob-compose")
}

internal class FakeRewardedApi : RewardedApi {
    val shownOn = mutableListOf<Activity>()
    private val main = Handler(Looper.getMainLooper())

    override fun preload(placement: RewardedPlacement) = Unit
    override fun availability(placement: RewardedPlacement): StateFlow<AdAvailability> =
        MutableStateFlow(AdAvailability.Unavailable)
    override fun isReady(placement: RewardedPlacement): Boolean = false
    override fun clear(placement: RewardedPlacement) = Unit

    override fun show(
        activity: Activity,
        placement: RewardedPlacement,
        options: RewardedShowOptions,
        onResult: (RewardResult) -> Unit,
    ) {
        shownOn += activity
        main.post { onResult(RewardResult.NotEarned) }
    }

    override suspend fun showAndAwait(
        activity: Activity,
        placement: RewardedPlacement,
        options: RewardedShowOptions,
    ): RewardResult = error("not used by admob-compose")

    override val unclaimedResults: Flow<RewardResult.Earned> = emptyFlow()
}
