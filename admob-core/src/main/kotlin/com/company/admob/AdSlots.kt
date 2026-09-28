package com.company.admob

import android.app.Activity
import com.company.admob.config.AdDefinition
import com.company.admob.internal.threading.postToMain
import com.company.admob.internal.threading.runOnMain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Ads shown by key, in whatever format they have now (docs/AD_PROFILES.md, "Swapping formats"). A Firebase value can
 * swap an interstitial for an app open ad and back, or a banner for a native ad and back; code that goes through
 * `AdSlots` (or `AdSlotView`, or the `AdSlot` composable) follows the swap without a release.
 *
 * Rewarded formats never swap: show them with [AdsClient.rewarded] and [AdsClient.rewardedInterstitial].
 */
public object AdSlots {
    /**
     * The format [key] is served in now: its configured format, or the one a remote value swapped it to. Null before
     * `initialize()` or when [key] isn't configured. Any thread.
     */
    @JvmStatic
    public fun currentFormat(key: String): AdFormat? =
        CompanyAdMob.liveRuntime?.liveConfig?.current?.active(key)?.format

    /** [currentFormat] of [definition]. */
    @JvmStatic
    public fun currentFormat(definition: AdDefinition): AdFormat? = currentFormat(definition.name)

    /**
     * [currentFormat] of [key], then again each time a remote value swaps it. Emits null once before `initialize()`
     * (collect again after it) or when [key] isn't configured.
     */
    @JvmStatic
    public fun formatChanges(key: String): Flow<AdFormat?> {
        val live = CompanyAdMob.liveRuntime?.liveConfig ?: return flowOf(null)
        return live.flow.map { it.active(key)?.format }.distinctUntilChanged()
    }

    /**
     * Shows the full-screen ad [definition] as an interstitial or an app open ad, whichever it is now, with its
     * counter, timer and caps (they belong to the ad, whatever its format). [onResult] is called exactly once, on the
     * main thread, after the ad is dismissed or at once with `NotShown` (for example `CounterNotReached`). Continue
     * your flow in both cases. Any thread.
     */
    @JvmStatic
    public fun showFullScreen(activity: Activity, definition: AdDefinition, onResult: (FullScreenResult) -> Unit) {
        showFullScreen(activity, definition.name, onResult)
    }

    /** [showFullScreen] by key, for ads not declared as an [AdDefinition]. */
    @JvmStatic
    public fun showFullScreen(activity: Activity, key: String, onResult: (FullScreenResult) -> Unit) {
        show(activity, key, splash = false, onResult)
    }

    /**
     * Shows the splash ad [definition], as `InterstitialApi.showOnSplash` does, in its current format: a splash
     * interstitial swapped to app open waits for and shows an app open ad instead. [onResult] is called exactly once,
     * on the main thread. Any thread.
     */
    @JvmStatic
    public fun showOnSplash(activity: Activity, definition: AdDefinition, onResult: (FullScreenResult) -> Unit) {
        show(activity, definition.name, splash = true, onResult)
    }

    private fun show(activity: Activity, key: String, splash: Boolean, onResult: (FullScreenResult) -> Unit) {
        val runtime = CompanyAdMob.liveRuntime
        if (runtime == null) {
            // Not initialized, or a test client stands in: the public interstitial API reports it.
            val placement = InterstitialPlacement(key)
            if (splash) {
                CompanyAdMob.interstitial.showOnSplash(activity, placement, onResult)
            } else {
                CompanyAdMob.interstitial.show(activity, placement, onResult)
            }
            return
        }
        val p = runtime.liveConfig.current.active(key)
        val engine = when (p?.format) {
            AdFormat.INTERSTITIAL -> runtime.interstitialEngine
            AdFormat.APP_OPEN -> runtime.appOpenEngine
            else -> null
        }
        if (p == null || engine == null) {
            val reason = if (p == null) "no ad \"$key\" is configured" else "\"$key\" is ${p.format}, not a full-screen ad AdSlots can show"
            runtime.log.w { "AdSlots: $reason" }
            postToMain { onResult(FullScreenResult.NotShown(AdError.InvalidConfiguration(reason))) }
            return
        }
        runOnMain {
            if (splash) engine.showOnSplash(activity, p.placement, onResult) else engine.show(activity, p.placement, onResult = onResult)
        }
    }
}
