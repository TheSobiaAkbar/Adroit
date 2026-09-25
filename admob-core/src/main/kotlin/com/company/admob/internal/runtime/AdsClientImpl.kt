package com.company.admob.internal.runtime

import android.app.Activity
import com.company.admob.AdPlacement
import com.company.admob.AdsClient
import com.company.admob.AdsState
import com.company.admob.AppOpenApi
import com.company.admob.AppOpenPlacement
import com.company.admob.BannerApi
import com.company.admob.DiagnosticsApi
import com.company.admob.FullScreenResult
import com.company.admob.InterstitialApi
import com.company.admob.InterstitialPlacement
import com.company.admob.NativeApi
import com.company.admob.NativePlacement
import com.company.admob.PrivacyApi
import com.company.admob.RewardResult
import com.company.admob.RewardedApi
import com.company.admob.RewardedInterstitialApi
import com.company.admob.RewardedInterstitialPlacement
import com.company.admob.RewardedPlacement
import com.company.admob.RewardedShowOptions
import com.company.admob.events.AdEvent
import com.company.admob.internal.events.EventBus
import com.company.admob.internal.init.InitCoordinator
import com.company.admob.internal.threading.postToMain
import com.company.admob.internal.threading.runOnMain
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/**
 * The [AdsClient] behind the facade once `initialize()` has run. Callable from any thread: work is moved to the main
 * thread, and callbacks arrive there.
 */
internal class AdsClientImpl(
    override val state: StateFlow<AdsState>,
    private val eventBus: EventBus,
    private val init: InitCoordinator,
    override val privacy: PrivacyApi,
    override val interstitial: InterstitialApi,
    override val rewarded: RewardedApi,
    override val rewardedInterstitial: RewardedInterstitialApi,
    override val appOpen: AppOpenApi,
    override val banner: BannerApi,
    override val native: NativeApi,
    override val diagnostics: DiagnosticsApi,
    private val runtime: AdsRuntime,
) : AdsClient {
    private val log get() = runtime.log

    override val events: SharedFlow<AdEvent> get() = eventBus.events

    override val isReady: Boolean get() = state.value == AdsState.Ready

    // Always posted, so a start() made right after an off-main initialize() runs after its state change.
    override fun start(activity: Activity, onComplete: ((AdsState) -> Unit)?) {
        postToMain { init.start(activity, onComplete) }
    }

    override suspend fun awaitReady(timeout: Duration): Boolean = init.awaitSettled(timeout)

    override fun setAdFree(adFree: Boolean) {
        runOnMain { init.setAdFree(adFree) }
    }

    override fun showInterstitial(
        activity: Activity,
        placement: InterstitialPlacement,
        onResult: (FullScreenResult) -> Unit,
    ): Unit = interstitial.show(activity, placement, onResult)

    override fun showRewarded(
        activity: Activity,
        placement: RewardedPlacement,
        onResult: (RewardResult) -> Unit,
    ): Unit = rewarded.show(activity, placement, RewardedShowOptions(), onResult)

    override fun preload(vararg placements: AdPlacement) {
        for (placement in placements) {
            when (placement) {
                is InterstitialPlacement -> interstitial.preload(placement)
                is RewardedPlacement -> rewarded.preload(placement)
                is RewardedInterstitialPlacement -> rewardedInterstitial.preload(placement)
                is AppOpenPlacement -> runOnMain { runtime.appOpenEngine.preload(placement) }
                is NativePlacement -> native.preload(placement)
                else -> log.d { "preload(): ${placement.format} placements are ignored here" }
            }
        }
    }
}
