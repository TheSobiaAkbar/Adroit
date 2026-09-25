package com.company.admob.compose

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.company.admob.AdError
import com.company.admob.FullScreenResult
import com.company.admob.InterstitialPlacement
import com.company.admob.RewardResult
import com.company.admob.RewardedInterstitialPlacement
import com.company.admob.RewardedPlacement
import com.company.admob.compose.internal.ComposeRuntime
import com.company.admob.compose.internal.findActivity
import java.lang.ref.WeakReference

/**
 * Shows full-screen ads from Compose event handlers, over the Activity hosting the composition. Get one with
 * [rememberFullScreenAdLauncher].
 *
 * Each call finds the Activity when it's made. If there's none (the composition was disposed, or its context has no
 * Activity), the ad isn't shown and the result is `NotShown(ActivityUnavailable)` or `Failed(ActivityUnavailable)`.
 * Callable from any thread; every `onResult` is called exactly once, on the main thread, and never synchronously.
 */
public interface FullScreenAdLauncher {
    /** Shows an interstitial for [placement] if one is ready. See `InterstitialApi.show`. */
    public fun showInterstitial(placement: InterstitialPlacement, onResult: (FullScreenResult) -> Unit = {})

    /**
     * Shows a rewarded ad for [placement]. See `RewardedApi.show`: grant the reward in a ViewModel or repository,
     * idempotently by `rewardId`, not in the composable.
     */
    public fun showRewarded(placement: RewardedPlacement, onResult: (RewardResult) -> Unit)

    /**
     * Shows a rewarded interstitial for [placement]. Show your intro screen with an opt-out first; see
     * `RewardedInterstitialApi.show`.
     */
    public fun showRewardedInterstitial(placement: RewardedInterstitialPlacement, onResult: (RewardResult) -> Unit)
}

/**
 * A [FullScreenAdLauncher] for this composition's Activity, stable across recompositions. It keeps no Activity: it
 * holds the composition's context only weakly, and lets go of it when the composition is disposed.
 */
@Composable
public fun rememberFullScreenAdLauncher(): FullScreenAdLauncher {
    val context = LocalContext.current
    val launcher = remember { ContextFullScreenAdLauncher() }
    DisposableEffect(launcher, context) {
        launcher.context = WeakReference(context)
        onDispose { launcher.context = null }
    }
    return launcher
}

private class ContextFullScreenAdLauncher : FullScreenAdLauncher {
    @Volatile
    var context: WeakReference<Context>? = null

    private val main = Handler(Looper.getMainLooper())

    private fun activity(what: String): Activity? {
        val activity = context?.get()?.findActivity()
        if (activity == null) ComposeRuntime.error("FullScreenAdLauncher.$what: no Activity (composition disposed?)")
        return activity
    }

    override fun showInterstitial(placement: InterstitialPlacement, onResult: (FullScreenResult) -> Unit) {
        val activity = activity("showInterstitial(${placement.key})")
            ?: return notShown { onResult(FullScreenResult.NotShown(AdError.ActivityUnavailable)) }
        ComposeRuntime.client.interstitial.show(activity, placement, onResult)
    }

    override fun showRewarded(placement: RewardedPlacement, onResult: (RewardResult) -> Unit) {
        val activity = activity("showRewarded(${placement.key})")
            ?: return notShown { onResult(RewardResult.Failed(AdError.ActivityUnavailable)) }
        ComposeRuntime.client.rewarded.show(activity, placement, onResult = onResult)
    }

    override fun showRewardedInterstitial(placement: RewardedInterstitialPlacement, onResult: (RewardResult) -> Unit) {
        val activity = activity("showRewardedInterstitial(${placement.key})")
            ?: return notShown { onResult(RewardResult.Failed(AdError.ActivityUnavailable)) }
        ComposeRuntime.client.rewardedInterstitial.show(activity, placement, onResult = onResult)
    }

    /** Results are never delivered synchronously, as with the core APIs. */
    private fun notShown(deliver: () -> Unit) {
        main.post(deliver)
    }
}
