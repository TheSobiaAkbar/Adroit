package io.github.thesobiaakbar.adroit.views

import io.github.thesobiaakbar.adroit.NativeStyle
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.AdsClient
import io.github.thesobiaakbar.adroit.BannerAdHandle
import io.github.thesobiaakbar.adroit.BannerApi
import io.github.thesobiaakbar.adroit.BannerOptions
import io.github.thesobiaakbar.adroit.BannerPlacement
import io.github.thesobiaakbar.adroit.BannerState
import io.github.thesobiaakbar.adroit.Adroit
import io.github.thesobiaakbar.adroit.NativeAdAssets
import io.github.thesobiaakbar.adroit.NativeAdHandle
import io.github.thesobiaakbar.adroit.NativeAdHandleState
import io.github.thesobiaakbar.adroit.NativeAdLayout
import io.github.thesobiaakbar.adroit.NativeAdRenderer
import io.github.thesobiaakbar.adroit.NativeAdResult
import io.github.thesobiaakbar.adroit.NativeApi
import io.github.thesobiaakbar.adroit.NativePlacement
import io.github.thesobiaakbar.adroit.UnavailableReason
import io.github.thesobiaakbar.adroit.views.internal.ViewsRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal val HOME = BannerPlacement("home")
internal val FEED = NativePlacement("feed")

/** The public [AdsClient] with fake banner and native APIs; everything else is the uninitialized facade. */
internal class TestAdsClient(
    override val banner: FakeBannerApi = FakeBannerApi(),
    override val native: FakeNativeApi = FakeNativeApi(),
) : AdsClient by Adroit.client {
    fun install() = apply { ViewsRuntime.clientOverride = this }

    companion object {
        fun uninstall() {
            ViewsRuntime.clientOverride = null
        }
    }
}

/** Creates [FakeBannerHandle]s; placements not in [known] get `Failed(InvalidConfiguration)`, like core. */
internal class FakeBannerApi(private val known: Set<BannerPlacement> = setOf(HOME)) : BannerApi {
    class Created(val placement: BannerPlacement, val options: BannerOptions, val handle: FakeBannerHandle)

    val created = mutableListOf<Created>()

    val live: List<FakeBannerHandle> get() = created.map { it.handle }.filterNot { it.isDestroyed }

    override fun create(activity: Activity, placement: BannerPlacement, options: BannerOptions): BannerAdHandle {
        val initial = if (placement in known) {
            BannerState.Loading
        } else {
            BannerState.Failed(AdError.InvalidConfiguration("${placement.key} isn't a configured banner placement"))
        }
        return FakeBannerHandle(activity, initial).also { created += Created(placement, options, it) }
    }
}

/** A banner handle that follows core's contract: `bindTo` destroys on ON_DESTROY, and destroy is idempotent. */
internal class FakeBannerHandle(activity: Activity, initial: BannerState) : BannerAdHandle {
    override val view: View = FrameLayout(activity)
    override val state = MutableStateFlow(initial)
    override val expectedHeightPx: Int = 0
    var boundOwner: LifecycleOwner? = null
    var destroyCount = 0
    val isDestroyed: Boolean get() = destroyCount > 0

    override fun bindTo(owner: LifecycleOwner) {
        boundOwner = owner
        owner.lifecycle.addObserver(LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) destroy() })
    }

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

    class Render(val handle: NativeAdHandle, val layout: NativeAdLayout)

    var failWith: AdError? = null

    /** What `style()` reports: the template the helpers pick when the code passes no layout. */
    var style: NativeStyle = NativeStyle.LARGE_BUTTON_BOTTOM
    val handles = mutableListOf<FakeNativeHandle>()
    val renders = mutableListOf<Render>()
    var loadCount = 0
        private set

    /** When false, results are held in [pending] until [deliverPending]. */
    var deliverImmediately = true
    private val pending = mutableListOf<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    override fun preload(placement: NativePlacement) = Unit

    override fun load(placement: NativePlacement, onResult: (NativeAdResult) -> Unit) {
        loadCount++
        val result = failWith?.let { NativeAdResult.Failed(it) }
            ?: NativeAdResult.Loaded(FakeNativeHandle(placement).also { handles += it })
        val deliver = { onResult(result) }
        if (deliverImmediately) main.post(deliver) else pending += deliver
    }

    fun deliverPending() {
        pending.forEach { main.post(it) }
        pending.clear()
    }

    override suspend fun load(placement: NativePlacement): NativeAdResult = error("not used by adroit-views")

    override fun render(handle: NativeAdHandle, container: ViewGroup, layout: NativeAdLayout, renderer: NativeAdRenderer?): View {
        check(!handle.isDestroyed) { "render(): destroyed" }
        val root = FrameLayout(container.context)
        LayoutInflater.from(container.context).inflate(layout.layoutRes, root, true)
        val headline = requireNotNull(root.findViewById<TextView>(layout.headlineId)) { "no headline view" }
        headline.text = handle.assets.headline
        renderer?.onBind(root, handle.assets)
        container.removeAllViews()
        container.addView(root)
        renders += Render(handle, layout)
        return root
    }
}
