package com.company.admob.internal

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import com.company.admob.AdAvailability
import com.company.admob.AdError
import com.company.admob.AdPlacement
import com.company.admob.AdsClient
import com.company.admob.AdsState
import com.company.admob.AppOpenApi
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
import com.company.admob.NativeAdHandle
import com.company.admob.NativeAdLayout
import com.company.admob.NativeAdRenderer
import com.company.admob.NativeAdResult
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
import com.company.admob.internal.callbacks.OnceCallback
import com.company.admob.internal.logging.AdLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.time.Duration

/**
 * The [AdsClient] in effect before `CompanyAdMob.initialize()`. Nothing loads; every show returns
 * [AdError.NotInitialized] exactly once, on the main thread, never synchronously; everything else is a logged no-op.
 */
internal class UninitializedAdsClient(
    override val state: StateFlow<AdsState>,
    override val events: SharedFlow<AdEvent>,
    private val log: AdLog,
) : AdsClient {

    private fun warn(call: String) {
        log.w { "$call called before CompanyAdMob.initialize(); ignoring. Call initialize() in Application.onCreate." }
    }

    private fun <T> deliver(callback: (T) -> Unit, value: T) {
        OnceCallback(callback).invoke(value)
    }

    override val isReady: Boolean get() = false

    override fun start(activity: Activity, onComplete: ((AdsState) -> Unit)?) {
        warn("start()")
        onComplete?.let { deliver(it, AdsState.NotInitialized) }
    }

    override suspend fun awaitReady(timeout: Duration): Boolean {
        warn("awaitReady()")
        return false
    }

    override fun setAdFree(adFree: Boolean) {
        // Remembered by the facade and applied at initialize(); nothing to do here.
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
        warn("preload()")
    }

    override val privacy: PrivacyApi = object : PrivacyApi {
        override val consentState: StateFlow<ConsentState> = MutableStateFlow(ConsentState.Unknown).asStateFlow()
        override val isPrivacyOptionsRequired: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

        override fun showPrivacyOptions(activity: Activity, onDismissed: (AdError?) -> Unit) {
            warn("privacy.showPrivacyOptions()")
            deliver(onDismissed, AdError.NotInitialized)
        }

        override fun resetConsentForTesting() = warn("privacy.resetConsentForTesting()")
    }

    private val unavailable: StateFlow<AdAvailability> =
        MutableStateFlow<AdAvailability>(AdAvailability.Unavailable).asStateFlow()

    override val interstitial: InterstitialApi = object : InterstitialApi {
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
            deliver(onResult, FullScreenResult.NotShown(AdError.NotInitialized))
        }

        override suspend fun showAndAwait(activity: Activity, placement: InterstitialPlacement): FullScreenResult {
            warn("interstitial.showAndAwait()")
            return FullScreenResult.NotShown(AdError.NotInitialized)
        }
    }

    override val rewarded: RewardedApi = object : RewardedApi {
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
            deliver(onResult, RewardResult.Failed(AdError.NotInitialized))
        }

        override suspend fun showAndAwait(
            activity: Activity,
            placement: RewardedPlacement,
            options: RewardedShowOptions,
        ): RewardResult {
            warn("rewarded.showAndAwait()")
            return RewardResult.Failed(AdError.NotInitialized)
        }
    }

    override val rewardedInterstitial: RewardedInterstitialApi = object : RewardedInterstitialApi {
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
            deliver(onResult, RewardResult.Failed(AdError.NotInitialized))
        }

        override suspend fun showAndAwait(
            activity: Activity,
            placement: RewardedInterstitialPlacement,
            options: RewardedShowOptions,
        ): RewardResult {
            warn("rewardedInterstitial.showAndAwait()")
            return RewardResult.Failed(AdError.NotInitialized)
        }
    }

    override val appOpen: AppOpenApi = object : AppOpenApi {
        override val availability: StateFlow<AdAvailability> = unavailable

        override fun showOnColdStart(
            activity: Activity,
            maxWait: Duration,
            onComplete: (FullScreenResult) -> Unit,
        ) {
            warn("appOpen.showOnColdStart()")
            deliver(onComplete, FullScreenResult.NotShown(AdError.NotInitialized))
        }

        override fun skipNextForeground() = Unit
        override fun suppressWhile(owner: LifecycleOwner) = Unit
        override fun setEnabled(enabled: Boolean) = Unit
    }

    override val banner: BannerApi = object : BannerApi {
        override fun create(activity: Activity, placement: BannerPlacement, options: BannerOptions): BannerAdHandle {
            warn("banner.create()")
            return InertBannerAdHandle(FrameLayout(activity).apply { visibility = View.GONE })
        }
    }

    override val native: NativeApi = object : NativeApi {
        override fun preload(placement: NativePlacement) = warn("native.preload()")

        override fun load(placement: NativePlacement, onResult: (NativeAdResult) -> Unit) {
            warn("native.load()")
            deliver(onResult, NativeAdResult.Failed(AdError.NotInitialized))
        }

        override suspend fun load(placement: NativePlacement): NativeAdResult {
            warn("native.load()")
            return NativeAdResult.Failed(AdError.NotInitialized)
        }

        override fun render(
            handle: NativeAdHandle,
            container: ViewGroup,
            layout: NativeAdLayout,
            renderer: NativeAdRenderer?,
        ): View = throw IllegalStateException("No native ad can exist before CompanyAdMob.initialize()")
    }

    override val diagnostics: DiagnosticsApi = object : DiagnosticsApi {
        override fun snapshot(): DiagnosticsReport = DiagnosticsReport(
            libraryVersion = CompanyAdMobVersion.NAME,
            googleSdkVersion = null,
            environment = null,
            state = state.value,
            consent = ConsentState.Unknown,
            testMode = null,
            placements = emptyList(),
            activeFullScreen = null,
            currentActivity = null,
            lastError = AdError.NotInitialized,
        )

        override fun openAdInspector(activity: Activity, onClosed: (AdError?) -> Unit) {
            warn("diagnostics.openAdInspector()")
            deliver(onClosed, AdError.NotInitialized)
        }

        override val recentEvents: List<AdEvent> get() = emptyList()
    }
}

/** A banner handle that never shows an ad. */
private class InertBannerAdHandle(override val view: View) : BannerAdHandle {
    override val state: StateFlow<BannerState> =
        MutableStateFlow<BannerState>(BannerState.Failed(AdError.NotInitialized)).asStateFlow()
    override val expectedHeightPx: Int get() = 0
    override fun bindTo(owner: LifecycleOwner) = Unit
    override fun destroy() {
        (view.parent as? ViewGroup)?.removeView(view)
    }
}
