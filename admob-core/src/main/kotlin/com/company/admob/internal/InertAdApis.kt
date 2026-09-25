package com.company.admob.internal

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import com.company.admob.AdAvailability
import com.company.admob.AdError
import com.company.admob.AppOpenApi
import com.company.admob.BannerAdHandle
import com.company.admob.BannerApi
import com.company.admob.BannerOptions
import com.company.admob.BannerPlacement
import com.company.admob.BannerState
import com.company.admob.FullScreenResult
import com.company.admob.InterstitialApi
import com.company.admob.InterstitialPlacement
import com.company.admob.NativeAdHandle
import com.company.admob.NativeAdLayout
import com.company.admob.NativeAdRenderer
import com.company.admob.NativeAdResult
import com.company.admob.NativeApi
import com.company.admob.NativePlacement
import com.company.admob.RewardResult
import com.company.admob.RewardedApi
import com.company.admob.RewardedInterstitialApi
import com.company.admob.RewardedInterstitialPlacement
import com.company.admob.RewardedPlacement
import com.company.admob.RewardedShowOptions
import com.company.admob.internal.callbacks.OnceCallback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.time.Duration

/**
 * Format APIs that never load or show anything: every show or load reports [error] exactly once, on the main thread,
 * never synchronously; everything else is a no-op reported through [warn]. Used before `initialize()`, and for
 * formats whose engine isn't built yet.
 */
internal class InertAdApis(private val error: AdError, private val warn: (String) -> Unit) {
    private fun <T> deliver(callback: (T) -> Unit, value: T) {
        OnceCallback(callback).invoke(value)
    }

    private val unavailable: StateFlow<AdAvailability> =
        MutableStateFlow<AdAvailability>(AdAvailability.Unavailable).asStateFlow()

    val interstitial: InterstitialApi = object : InterstitialApi {
        override fun preload(placement: InterstitialPlacement) = warn("interstitial.preload()")
        override fun availability(placement: InterstitialPlacement): StateFlow<AdAvailability> = unavailable
        override fun isReady(placement: InterstitialPlacement): Boolean = false
        override fun clear(placement: InterstitialPlacement) = Unit

        override fun show(
            activity: Activity,
            placement: InterstitialPlacement,
            onResult: (FullScreenResult) -> Unit,
        ) {
            warn("interstitial.show()")
            deliver(onResult, FullScreenResult.NotShown(error))
        }

        override suspend fun showAndAwait(activity: Activity, placement: InterstitialPlacement): FullScreenResult {
            warn("interstitial.showAndAwait()")
            return FullScreenResult.NotShown(error)
        }
    }

    val rewarded: RewardedApi = object : RewardedApi {
        override fun preload(placement: RewardedPlacement) = warn("rewarded.preload()")
        override fun availability(placement: RewardedPlacement): StateFlow<AdAvailability> = unavailable
        override fun isReady(placement: RewardedPlacement): Boolean = false
        override fun clear(placement: RewardedPlacement) = Unit
        override val unclaimedResults: Flow<RewardResult.Earned> = emptyFlow()

        override fun show(
            activity: Activity,
            placement: RewardedPlacement,
            options: RewardedShowOptions,
            onResult: (RewardResult) -> Unit,
        ) {
            warn("rewarded.show()")
            deliver(onResult, RewardResult.Failed(error))
        }

        override suspend fun showAndAwait(
            activity: Activity,
            placement: RewardedPlacement,
            options: RewardedShowOptions,
        ): RewardResult {
            warn("rewarded.showAndAwait()")
            return RewardResult.Failed(error)
        }
    }

    val rewardedInterstitial: RewardedInterstitialApi = object : RewardedInterstitialApi {
        override fun preload(placement: RewardedInterstitialPlacement) = warn("rewardedInterstitial.preload()")
        override fun availability(placement: RewardedInterstitialPlacement): StateFlow<AdAvailability> = unavailable
        override fun isReady(placement: RewardedInterstitialPlacement): Boolean = false
        override fun clear(placement: RewardedInterstitialPlacement) = Unit
        override val unclaimedResults: Flow<RewardResult.Earned> = emptyFlow()

        override fun show(
            activity: Activity,
            placement: RewardedInterstitialPlacement,
            options: RewardedShowOptions,
            onResult: (RewardResult) -> Unit,
        ) {
            warn("rewardedInterstitial.show()")
            deliver(onResult, RewardResult.Failed(error))
        }

        override suspend fun showAndAwait(
            activity: Activity,
            placement: RewardedInterstitialPlacement,
            options: RewardedShowOptions,
        ): RewardResult {
            warn("rewardedInterstitial.showAndAwait()")
            return RewardResult.Failed(error)
        }
    }

    val appOpen: AppOpenApi = object : AppOpenApi {
        override val availability: StateFlow<AdAvailability> = unavailable

        override fun showOnColdStart(
            activity: Activity,
            maxWait: Duration,
            onComplete: (FullScreenResult) -> Unit,
        ) {
            warn("appOpen.showOnColdStart()")
            deliver(onComplete, FullScreenResult.NotShown(error))
        }

        override fun skipNextForeground() = Unit
        override fun suppressWhile(owner: LifecycleOwner) = Unit
        override fun setEnabled(enabled: Boolean) = Unit
    }

    val banner: BannerApi = object : BannerApi {
        override fun create(activity: Activity, placement: BannerPlacement, options: BannerOptions): BannerAdHandle {
            warn("banner.create()")
            return InertBannerAdHandle(FrameLayout(activity).apply { visibility = View.GONE }, error)
        }
    }

    val native: NativeApi = object : NativeApi {
        override fun preload(placement: NativePlacement) = warn("native.preload()")

        override fun load(placement: NativePlacement, onResult: (NativeAdResult) -> Unit) {
            warn("native.load()")
            deliver(onResult, NativeAdResult.Failed(error))
        }

        override suspend fun load(placement: NativePlacement): NativeAdResult {
            warn("native.load()")
            return NativeAdResult.Failed(error)
        }

        override fun render(
            handle: NativeAdHandle,
            container: ViewGroup,
            layout: NativeAdLayout,
            renderer: NativeAdRenderer?,
        ): View = throw IllegalStateException("No native ad can exist here (${error.message})")
    }
}

/** A banner handle that never shows an ad. */
private class InertBannerAdHandle(override val view: View, error: AdError) : BannerAdHandle {
    override val state: StateFlow<BannerState> =
        MutableStateFlow<BannerState>(BannerState.Failed(error)).asStateFlow()
    override val expectedHeightPx: Int get() = 0
    override fun bindTo(owner: LifecycleOwner) = Unit
    override fun destroy() {
        (view.parent as? ViewGroup)?.removeView(view)
    }
}
