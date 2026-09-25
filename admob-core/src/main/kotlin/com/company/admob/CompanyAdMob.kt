package com.company.admob

import android.app.Activity
import com.company.admob.events.AdEvent
import com.company.admob.internal.UninitializedAdsClient
import com.company.admob.internal.events.EventBus
import com.company.admob.internal.logging.AdLog
import com.company.admob.logging.AndroidAdLogger
import com.company.admob.logging.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration

/**
 * The process-wide entry point to CompanyAdMob.
 *
 * Call `initialize()` in `Application.onCreate()`, then [start] from your first Activity. Everything else is
 * described on [AdsClient]. For dependency injection, bind [AdsClient] to [client].
 *
 * [state] and [events] are the same instances before and after initialization, so they can be collected at any
 * time. Other objects obtained before initialization (for example availability flows) stay inert, so initialize
 * before using the rest of the API.
 */
public object CompanyAdMob : AdsClient {
    private val bootstrapLog = AdLog(AndroidAdLogger(), LogLevel.WARN)

    internal val sharedState: MutableStateFlow<AdsState> = MutableStateFlow(AdsState.NotInitialized)

    internal val eventBus: EventBus = EventBus(bootstrapLog)

    /** The implementation behind the facade. Replaced once by `initialize()`. */
    @Volatile
    internal var delegate: AdsClient =
        UninitializedAdsClient(sharedState.asStateFlow(), eventBus.events, bootstrapLog)

    /** The last value passed to [setAdFree], so an early call is applied at initialization. */
    @Volatile
    internal var requestedAdFree: Boolean? = null

    // TODO(PROMPT-06): public fun initialize(application: Application, config: AdMobConfig), once PROMPT 03
    //  provides AdMobConfig.

    /** This facade as an [AdsClient], for dependency injection. Stable across initialization. */
    @JvmStatic
    public val client: AdsClient
        get() = this

    override val state: StateFlow<AdsState> = sharedState.asStateFlow()

    override val isReady: Boolean get() = state.value == AdsState.Ready

    override fun start(activity: Activity, onComplete: ((AdsState) -> Unit)?): Unit =
        delegate.start(activity, onComplete)

    override suspend fun awaitReady(timeout: Duration): Boolean = delegate.awaitReady(timeout)

    override val privacy: PrivacyApi get() = delegate.privacy

    override val interstitial: InterstitialApi get() = delegate.interstitial

    override val rewarded: RewardedApi get() = delegate.rewarded

    override val rewardedInterstitial: RewardedInterstitialApi get() = delegate.rewardedInterstitial

    override val appOpen: AppOpenApi get() = delegate.appOpen

    override val banner: BannerApi get() = delegate.banner

    override val native: NativeApi get() = delegate.native

    override val diagnostics: DiagnosticsApi get() = delegate.diagnostics

    override val events: SharedFlow<AdEvent> = eventBus.events

    override fun setAdFree(adFree: Boolean) {
        requestedAdFree = adFree
        delegate.setAdFree(adFree)
    }

    override fun showInterstitial(
        activity: Activity,
        placement: InterstitialPlacement,
        onResult: (FullScreenResult) -> Unit,
    ): Unit = delegate.showInterstitial(activity, placement, onResult)

    override fun showRewarded(
        activity: Activity,
        placement: RewardedPlacement,
        onResult: (RewardResult) -> Unit,
    ): Unit = delegate.showRewarded(activity, placement, onResult)

    override fun preload(vararg placements: AdPlacement): Unit = delegate.preload(*placements)
}
