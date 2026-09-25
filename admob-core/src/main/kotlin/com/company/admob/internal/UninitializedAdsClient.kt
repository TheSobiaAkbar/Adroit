package com.company.admob.internal

import android.app.Activity
import com.company.admob.AdError
import com.company.admob.AdPlacement
import com.company.admob.AdsClient
import com.company.admob.AdsState
import com.company.admob.AppOpenApi
import com.company.admob.BannerApi
import com.company.admob.CompanyAdMobVersion
import com.company.admob.ConsentState
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
import com.company.admob.RewardedPlacement
import com.company.admob.RewardedShowOptions
import com.company.admob.events.AdEvent
import com.company.admob.internal.callbacks.OnceCallback
import com.company.admob.internal.logging.AdLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val inert = InertAdApis(AdError.NotInitialized, ::warn)

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

    override val interstitial: InterstitialApi get() = inert.interstitial

    override val rewarded: RewardedApi get() = inert.rewarded

    override val rewardedInterstitial: RewardedInterstitialApi get() = inert.rewardedInterstitial

    override val appOpen: AppOpenApi get() = inert.appOpen

    override val banner: BannerApi get() = inert.banner

    override val native: NativeApi get() = inert.native

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
            activeOverrides = "none",
        )

        override fun openAdInspector(activity: Activity, onClosed: (AdError?) -> Unit) {
            warn("diagnostics.openAdInspector()")
            deliver(onClosed, AdError.NotInitialized)
        }

        override val recentEvents: List<AdEvent> get() = emptyList()
    }
}
