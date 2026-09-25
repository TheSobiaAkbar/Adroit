package com.company.admob.internal.runtime

import android.app.Activity
import com.company.admob.AdError
import com.company.admob.AdPlacement
import com.company.admob.AdsClient
import com.company.admob.AdsState
import com.company.admob.AppOpenApi
import com.company.admob.AppOpenPlacement
import com.company.admob.BannerApi
import com.company.admob.CompanyAdMobVersion
import com.company.admob.DiagnosticsApi
import com.company.admob.DiagnosticsReport
import com.company.admob.FullScreenResult
import com.company.admob.InterstitialApi
import com.company.admob.InterstitialPlacement
import com.company.admob.NativeApi
import com.company.admob.PrivacyApi
import com.company.admob.RewardResult
import com.company.admob.RewardedApi
import com.company.admob.RewardedInterstitialApi
import com.company.admob.RewardedInterstitialPlacement
import com.company.admob.RewardedPlacement
import com.company.admob.RewardedShowOptions
import com.company.admob.TestModeInfo
import com.company.admob.events.AdEvent
import com.company.admob.internal.InertAdApis
import com.company.admob.internal.callbacks.OnceCallback
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
    private val runtime: AdsRuntime,
) : AdsClient {
    private val log get() = runtime.log

    /** Stand-ins for format engines that don't exist yet. Each one reports `Internal("not implemented")`. */
    private val notYetAvailable = InertAdApis(AdError.Internal("not implemented")) { call ->
        log.w { "$call is not implemented yet" }
    }

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

    // TODO(PROMPT-12): the native loader.
    override val native: NativeApi get() = notYetAvailable.native

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
                // TODO(PROMPT-12): native placements.
                else -> log.d { "preload(): ${placement.format} placements are ignored here" }
            }
        }
    }

    // TODO(PROMPT-15): full diagnostics (placements, active full-screen ad, Ad Inspector).
    override val diagnostics: DiagnosticsApi = object : DiagnosticsApi {
        override fun snapshot(): DiagnosticsReport {
            val effective = runtime.effectiveConfig
            val consent = privacy.consentState.value
            return DiagnosticsReport(
                libraryVersion = CompanyAdMobVersion.NAME,
                googleSdkVersion = runtime.deps.adsSdk.sdkVersion,
                environment = effective.environment,
                state = state.value,
                consent = consent,
                testMode = TestModeInfo(effective.usesDemoAdUnits, effective.testDeviceIds.size),
                placements = emptyList(),
                activeFullScreen = null,
                currentActivity = null,
                lastError = (state.value as? AdsState.Failed)?.error ?: consent.lastError,
            )
        }

        override fun openAdInspector(activity: Activity, onClosed: (AdError?) -> Unit) {
            log.w { "diagnostics.openAdInspector() is not implemented yet" }
            OnceCallback(onClosed).invoke(AdError.Internal("not implemented"))
        }

        override val recentEvents: List<AdEvent> get() = eventBus.recent()
    }
}
