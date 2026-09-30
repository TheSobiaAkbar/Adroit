package io.github.thesobiaakbar.adroit.internal.runtime

import android.app.Activity
import io.github.thesobiaakbar.adroit.AdPlacement
import io.github.thesobiaakbar.adroit.AdsClient
import io.github.thesobiaakbar.adroit.AdsState
import io.github.thesobiaakbar.adroit.AppOpenApi
import io.github.thesobiaakbar.adroit.AppOpenPlacement
import io.github.thesobiaakbar.adroit.BannerApi
import io.github.thesobiaakbar.adroit.DiagnosticsApi
import io.github.thesobiaakbar.adroit.FullScreenResult
import io.github.thesobiaakbar.adroit.InterstitialApi
import io.github.thesobiaakbar.adroit.InterstitialPlacement
import io.github.thesobiaakbar.adroit.NativeApi
import io.github.thesobiaakbar.adroit.NativePlacement
import io.github.thesobiaakbar.adroit.PrivacyApi
import io.github.thesobiaakbar.adroit.RewardResult
import io.github.thesobiaakbar.adroit.RewardedApi
import io.github.thesobiaakbar.adroit.RewardedInterstitialApi
import io.github.thesobiaakbar.adroit.RewardedInterstitialPlacement
import io.github.thesobiaakbar.adroit.RewardedPlacement
import io.github.thesobiaakbar.adroit.RewardedShowOptions
import io.github.thesobiaakbar.adroit.events.AdEvent
import io.github.thesobiaakbar.adroit.internal.events.EventBus
import io.github.thesobiaakbar.adroit.internal.init.InitCoordinator
import io.github.thesobiaakbar.adroit.internal.threading.postToMain
import io.github.thesobiaakbar.adroit.internal.threading.runOnMain
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
