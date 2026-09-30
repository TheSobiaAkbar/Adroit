package io.github.thesobiaakbar.adroit.internal

import android.app.Activity
import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.AdPlacement
import io.github.thesobiaakbar.adroit.AdsClient
import io.github.thesobiaakbar.adroit.AdsState
import io.github.thesobiaakbar.adroit.AppOpenApi
import io.github.thesobiaakbar.adroit.BannerApi
import io.github.thesobiaakbar.adroit.AdroitVersion
import io.github.thesobiaakbar.adroit.ConsentState
import io.github.thesobiaakbar.adroit.DiagnosticsApi
import io.github.thesobiaakbar.adroit.DiagnosticsReport
import io.github.thesobiaakbar.adroit.FullScreenResult
import io.github.thesobiaakbar.adroit.InterstitialApi
import io.github.thesobiaakbar.adroit.InterstitialPlacement
import io.github.thesobiaakbar.adroit.NativeApi
import io.github.thesobiaakbar.adroit.PrivacyApi
import io.github.thesobiaakbar.adroit.RewardResult
import io.github.thesobiaakbar.adroit.RewardedApi
import io.github.thesobiaakbar.adroit.RewardedInterstitialApi
import io.github.thesobiaakbar.adroit.RewardedPlacement
import io.github.thesobiaakbar.adroit.RewardedShowOptions
import io.github.thesobiaakbar.adroit.events.AdEvent
import io.github.thesobiaakbar.adroit.internal.callbacks.OnceCallback
import io.github.thesobiaakbar.adroit.internal.logging.AdLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration

/**
 * The [AdsClient] in effect before `Adroit.initialize()`. Nothing loads; every show returns
 * [AdError.NotInitialized] exactly once, on the main thread, never synchronously; everything else is a logged no-op.
 */
internal class UninitializedAdsClient(
    override val state: StateFlow<AdsState>,
    override val events: SharedFlow<AdEvent>,
    private val log: AdLog,
) : AdsClient {

    private fun warn(call: String) {
        log.w { "$call called before Adroit.initialize(); ignoring. Call initialize() in Application.onCreate." }
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
            libraryVersion = AdroitVersion.NAME,
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
