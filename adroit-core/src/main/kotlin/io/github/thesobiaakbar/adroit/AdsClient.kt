package io.github.thesobiaakbar.adroit

import android.app.Activity
import io.github.thesobiaakbar.adroit.events.AdEvent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The Adroit API. Use [Adroit] directly, or inject `AdsClient` (bound to [Adroit.client]) so
 * tests can substitute a fake from `adroit-testing`.
 *
 * Threading: every function may be called from any thread. Every callback and flow emission arrives on the main
 * thread. No function throws because of an ad failure; failures are reported through results and [events].
 *
 * Stability: this interface, its format APIs ([interstitial], [banner] and the others) and the ad handles are
 * implemented by the library. Members may be added to them in minor versions, always with a default implementation,
 * so an existing implementation (such as a hand-written test fake) keeps compiling and linking; prefer the fakes in
 * `adroit-testing`, which implement new members for you.
 */
public interface AdsClient {
    /** Library-wide readiness. Ads load only in [AdsState.Ready]. */
    public val state: StateFlow<AdsState>

    /** True when [state] is [AdsState.Ready]. */
    public val isReady: Boolean

    /**
     * Gathers consent (showing Google's consent form if required) and then initializes the Google Mobile Ads SDK.
     * Call it from your first Activity on every launch. Safe to call again, for example after a configuration
     * change: repeated calls join the work in progress. [onComplete] is called once, on the main thread, with the
     * settled state.
     */
    public fun start(activity: Activity, onComplete: ((AdsState) -> Unit)? = null)

    /** Suspends until the library is ready (true), reaches a terminal state, or [timeout] passes (false). */
    public suspend fun awaitReady(timeout: Duration = 10.seconds): Boolean

    /** Consent and privacy options. */
    public val privacy: PrivacyApi

    /** Interstitial ads. */
    public val interstitial: InterstitialApi

    /** Rewarded ads. */
    public val rewarded: RewardedApi

    /** Rewarded interstitial ads. */
    public val rewardedInterstitial: RewardedInterstitialApi

    /** App open ads. */
    public val appOpen: AppOpenApi

    /** Banner ads. */
    public val banner: BannerApi

    /** Native ads. */
    public val native: NativeApi

    /** Debugging aids. */
    public val diagnostics: DiagnosticsApi

    /** Every library event. Buffered; a slow collector drops the oldest events rather than slowing ads down. */
    public val events: SharedFlow<AdEvent>

    /**
     * Marks the user as ad-free (for example, after a purchase), or not. When true, cached ads are released,
     * banners and native ads hide, and nothing loads. Not persisted: apply it again on every launch.
     */
    public fun setAdFree(adFree: Boolean)

    /** Shortcut for [InterstitialApi.show]. */
    public fun showInterstitial(
        activity: Activity,
        placement: InterstitialPlacement,
        onResult: (FullScreenResult) -> Unit = {},
    )

    /** Shortcut for [RewardedApi.show] with default options. */
    public fun showRewarded(activity: Activity, placement: RewardedPlacement, onResult: (RewardResult) -> Unit)

    /** Preloads ads for full-screen and native [placements]. Banner and app open placements are ignored. */
    public fun preload(vararg placements: AdPlacement)
}
