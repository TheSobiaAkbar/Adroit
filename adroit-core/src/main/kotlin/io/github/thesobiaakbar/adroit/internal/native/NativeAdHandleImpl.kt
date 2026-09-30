package io.github.thesobiaakbar.adroit.internal.native

import android.view.ViewGroup
import io.github.thesobiaakbar.adroit.AdFormat
import io.github.thesobiaakbar.adroit.NativeAdAssets
import io.github.thesobiaakbar.adroit.NativeAdHandle
import io.github.thesobiaakbar.adroit.NativeAdHandleState
import io.github.thesobiaakbar.adroit.NativePlacement
import io.github.thesobiaakbar.adroit.UnavailableReason
import io.github.thesobiaakbar.adroit.events.AdEvent
import io.github.thesobiaakbar.adroit.events.AdRevenue
import io.github.thesobiaakbar.adroit.internal.events.EventBus
import io.github.thesobiaakbar.adroit.internal.logging.AdLog
import io.github.thesobiaakbar.adroit.internal.port.LoadedNativeAd
import io.github.thesobiaakbar.adroit.internal.port.NativeAdListener
import io.github.thesobiaakbar.adroit.internal.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A native ad handed out to a caller, who owns it (architecture §17). Wraps the [LoadedNativeAd], reports its
 * impression, click and paid events for [placement], and remembers the view it was last rendered into, so a second
 * render (for example after rotation) detaches the first and [destroy] leaves no reference to any view or Context.
 *
 * The SDK ad is destroyed exactly once: by [destroy], or by [block] when ads are blocked. Main thread only.
 */
internal class NativeAdHandleImpl(
    override val placement: NativePlacement,
    val ad: LoadedNativeAd,
    private val adUnitId: String,
    private val events: EventBus,
    private val clock: Clock,
    private val log: AdLog,
    private val onReleased: (NativeAdHandleImpl) -> Unit,
) : NativeAdHandle {
    private val mutableState = MutableStateFlow<NativeAdHandleState>(NativeAdHandleState.Active)
    private var boundView: ViewGroup? = null
    private var releaseBoundView: ((ViewGroup) -> Unit)? = null

    override val assets: NativeAdAssets get() = ad.assets

    override val state: StateFlow<NativeAdHandleState> = mutableState.asStateFlow()

    override val isDestroyed: Boolean get() = mutableState.value != NativeAdHandleState.Active

    init {
        ad.setListener(
            object : NativeAdListener {
                override fun onImpression() = emit { AdEvent.Impression(placement.key, AdFormat.NATIVE, adUnitId, it) }

                override fun onClicked() = emit { AdEvent.Clicked(placement.key, AdFormat.NATIVE, adUnitId, it) }

                override fun onPaid(revenue: AdRevenue) =
                    emit { AdEvent.Paid(placement.key, AdFormat.NATIVE, adUnitId, it, revenue, ad.source) }
            },
        )
    }

    /** Records the view the ad is now rendered into, detaching it from the previous one first. */
    fun attachView(adView: ViewGroup, release: (ViewGroup) -> Unit) {
        detachView()
        boundView = adView
        releaseBoundView = release
    }

    /** Removes the rendered view from its parent and releases it. */
    fun detachView() {
        val view = boundView ?: return
        val release = releaseBoundView
        boundView = null
        releaseBoundView = null
        (view.parent as? ViewGroup)?.removeView(view)
        release?.invoke(view)
    }

    override fun destroy() {
        when (mutableState.value) {
            NativeAdHandleState.Active -> {
                release()
                mutableState.value = NativeAdHandleState.Destroyed
                events.emit(AdEvent.Destroyed(placement.key, AdFormat.NATIVE, adUnitId, clock.currentTimeMillis()))
                log.d { "Native ad ${placement.key} destroyed" }
            }
            // The ad was already released when ads were blocked; only the state changes.
            is NativeAdHandleState.Blocked -> mutableState.value = NativeAdHandleState.Destroyed
            NativeAdHandleState.Destroyed -> Unit
        }
    }

    /** Ads were blocked: release the ad and its view, and tell observers to hide the slot. */
    fun block(reason: UnavailableReason) {
        if (mutableState.value != NativeAdHandleState.Active) return
        release()
        mutableState.value = NativeAdHandleState.Blocked(reason)
        events.emit(AdEvent.Destroyed(placement.key, AdFormat.NATIVE, adUnitId, clock.currentTimeMillis()))
    }

    private fun release() {
        detachView()
        ad.setListener(null)
        ad.destroy()
        onReleased(this)
    }

    private inline fun emit(event: (Long) -> AdEvent) {
        if (mutableState.value == NativeAdHandleState.Active) events.emit(event(clock.currentTimeMillis()))
    }

    override fun toString(): String = "NativeAdHandle(${placement.key}, ${mutableState.value})"
}
