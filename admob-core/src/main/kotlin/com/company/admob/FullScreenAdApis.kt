package com.company.admob

import android.app.Activity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Operations shared by the full-screen formats (interstitial, rewarded, rewarded interstitial).
 *
 * Every function may be called from any thread. Loading happens only when the library is [AdsState.Ready];
 * earlier calls are remembered as intent and executed once it's ready.
 */
public interface FullScreenAdApi<P : AdPlacement> {
    /** Starts loading an ad for [placement] if none is cached or loading. Never blocks. */
    public fun preload(placement: P)

    /** Observable availability of an ad for [placement]. Emits on the main thread. */
    public fun availability(placement: P): StateFlow<AdAvailability>

    /** True if a fresh ad for [placement] is cached and could be shown now. */
    public fun isReady(placement: P): Boolean

    /** Drops any cached ad for [placement] and cancels pending retries. */
    public fun clear(placement: P)
}

/** Interstitial ads. Obtain it from [AdsClient.interstitial]. */
public interface InterstitialApi : FullScreenAdApi<InterstitialPlacement> {
    /**
     * Shows an interstitial for [placement] if one is ready, otherwise doesn't show one.
     *
     * [onResult] is called exactly once, on the main thread, and never synchronously inside this call.
     * Continue the app flow in both branches; if no ad is ready, a load starts for next time.
     */
    public fun show(
        activity: Activity,
        placement: InterstitialPlacement,
        onResult: (FullScreenResult) -> Unit = {},
    )

    /**
     * Suspending form of [show]. Returns after the ad is dismissed, or immediately if it can't be shown.
     * Cancelling the caller does not cancel an ad that is already showing.
     */
    public suspend fun showAndAwait(activity: Activity, placement: InterstitialPlacement): FullScreenResult

    /**
     * Shows an interstitial from a splash screen: waits up to the placement's `splashTimeout` for the library to be
     * ready (after consent) and for an ad to load, then shows it. [onResult] is called exactly once, on the main
     * thread: after the ad is dismissed, or with [FullScreenResult.NotShown] if none could be shown in time
     * (`Timeout`), so continue to your main screen in both cases.
     *
     * Use it for a placement configured with `splash = true`: it isn't counted in
     * `FrequencyOptions.maxInterstitialsPerSession` and ignores the counter. If [activity] is paused or destroyed
     * during the wait, the result is `NotShown(ActivityUnavailable)`. A call made while another is still waiting
     * completes at once with `NotShown(AlreadyShowing)`.
     */
    public fun showOnSplash(
        activity: Activity,
        placement: InterstitialPlacement,
        onResult: (FullScreenResult) -> Unit,
    )
}

/**
 * Rewarded ads. Obtain it from [AdsClient.rewarded].
 *
 * Grant rewards in a ViewModel or repository, not in the Activity, and use [RewardResult.Earned.rewardId] to make
 * the grant idempotent. For valuable rewards, prefer server-side verification
 * ([RewardedShowOptions.serverSideVerificationCustomData]). If the process dies while the ad is showing, the
 * client-side reward is lost; server-side verification covers that case.
 */
public interface RewardedApi : FullScreenAdApi<RewardedPlacement> {
    /**
     * Shows a rewarded ad for [placement]. [onResult] is called exactly once, on the main thread, after the ad is
     * dismissed, or without showing an ad if none can be shown. A reward is never delivered twice.
     *
     * Grant the reward in a ViewModel or repository, not in the Activity, and use [RewardResult.Earned.rewardId] to
     * make the grant idempotent. For valuable rewards, prefer server-side verification
     * ([RewardedShowOptions.serverSideVerificationCustomData]). If the process dies while the ad is showing, the
     * client-side reward is lost; server-side verification covers that case.
     */
    public fun show(
        activity: Activity,
        placement: RewardedPlacement,
        options: RewardedShowOptions = RewardedShowOptions(),
        onResult: (RewardResult) -> Unit,
    )

    /**
     * Suspending form of [show]. If the calling coroutine is cancelled while the ad is showing (for example, by a
     * configuration change), the ad continues, and an earned reward is delivered to [unclaimedResults] instead.
     */
    public suspend fun showAndAwait(
        activity: Activity,
        placement: RewardedPlacement,
        options: RewardedShowOptions = RewardedShowOptions(),
    ): RewardResult

    /**
     * Earned rewards whose [showAndAwait] caller was cancelled before the ad finished. Each reward is delivered
     * to exactly one collector: collect it from a single long-lived scope, such as a ViewModel.
     */
    public val unclaimedResults: Flow<RewardResult.Earned>
}

/**
 * Rewarded interstitial ads. Obtain it from [AdsClient.rewardedInterstitial].
 *
 * Google requires an intro screen that announces the reward and lets the user opt out **before** you call [show]
 * (https://developers.google.com/admob/android/next-gen/rewarded-interstitial). The same reward guarantees as
 * [RewardedApi] apply.
 */
public interface RewardedInterstitialApi : FullScreenAdApi<RewardedInterstitialPlacement> {
    /**
     * You must show an intro screen announcing the reward with an option to opt out before calling this
     * (https://developers.google.com/admob/android/next-gen/rewarded-interstitial). Otherwise the same as
     * [RewardedApi.show]: [onResult] is called exactly once, on the main thread, and a reward is never delivered twice.
     */
    public fun show(
        activity: Activity,
        placement: RewardedInterstitialPlacement,
        options: RewardedShowOptions = RewardedShowOptions(),
        onResult: (RewardResult) -> Unit,
    )

    /** See [RewardedApi.showAndAwait]. */
    public suspend fun showAndAwait(
        activity: Activity,
        placement: RewardedInterstitialPlacement,
        options: RewardedShowOptions = RewardedShowOptions(),
    ): RewardResult

    /** See [RewardedApi.unclaimedResults]. */
    public val unclaimedResults: Flow<RewardResult.Earned>
}

/**
 * Options for showing a rewarded or rewarded interstitial ad.
 *
 * @property serverSideVerificationCustomData passed to your server-side verification (SSV) callback as
 * `custom_data`. Don't put personal data here.
 */
public class RewardedShowOptions(
    public val serverSideVerificationCustomData: String? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is RewardedShowOptions && other.serverSideVerificationCustomData == serverSideVerificationCustomData

    override fun hashCode(): Int = serverSideVerificationCustomData.hashCode()

    override fun toString(): String =
        "RewardedShowOptions(serverSideVerificationCustomData=${serverSideVerificationCustomData?.let { "<set>" }})"
}
