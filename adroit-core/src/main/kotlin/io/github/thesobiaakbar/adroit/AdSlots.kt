package io.github.thesobiaakbar.adroit

import android.app.Activity
import io.github.thesobiaakbar.adroit.config.AdDefinition
import io.github.thesobiaakbar.adroit.internal.threading.postToMain
import io.github.thesobiaakbar.adroit.internal.threading.runOnMain
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
        Adroit.liveRuntime?.liveConfig?.current?.active(key)?.format

    /** [currentFormat] of [definition]. */
    @JvmStatic
    public fun currentFormat(definition: AdDefinition): AdFormat? = currentFormat(definition.name)

    /**
     * [currentFormat] of [key], then again each time a remote value swaps it. Emits null once before `initialize()`
     * (collect again after it) or when [key] isn't configured.
     */
    @JvmStatic
    public fun formatChanges(key: String): Flow<AdFormat?> {
        val live = Adroit.liveRuntime?.liveConfig ?: return flowOf(null)
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

    /**
     * Loads the ad [definition] now, in its current format, unless it's loaded or loading already. Ad profiles load
     * only when asked (docs/AD_PROFILES.md#loading): call this when the screen that may show the ad opens (for example
     * in `onResume`), so it's ready by the time it's shown. A failed load isn't retried; the next call tries again.
     * After a show, the ad loads again by itself only with `loadAgain`. Nothing happens before `initialize()`. Any
     * thread.
     *
     * A banner or native ad loaded this way waits for its view: the next `AdSlotView`, `AdBannerView`,
     * `AdNativeView`, `attachBanner`, `attachNativeAd`, `NativeAdHolder` or composable created for it shows it at once,
     * and one created while it's still loading shows it when it arrives, without a second request. A banner is loaded
     * at the window's width, for a full-width banner; a banner view of another width loads its own.
     */
    @JvmStatic
    public fun load(definition: AdDefinition) {
        load(definition.name)
    }

    /** [load] by key, for ads not declared as an [AdDefinition]. */
    @JvmStatic
    public fun load(key: String) {
        val runtime = Adroit.liveRuntime ?: return
        runOnMain {
            val p = runtime.liveConfig.current.active(key)
            if (p == null) {
                runtime.log.w { "AdSlots.load: no ad \"$key\" is configured" }
                return@runOnMain
            }
            when (p.format) {
                AdFormat.INTERSTITIAL -> runtime.interstitialEngine.preload(p.placement)
                AdFormat.APP_OPEN -> runtime.appOpenEngine.preload(p.placement)
                AdFormat.REWARDED -> runtime.rewardedEngine.preload(p.placement)
                AdFormat.REWARDED_INTERSTITIAL -> runtime.rewardedInterstitialEngine.preload(p.placement)
                AdFormat.NATIVE -> runtime.nativeEngine.preload(p.placement as NativePlacement)
                AdFormat.BANNER -> runtime.bannerPreloader.preload(p)
            }
        }
    }

    private fun show(activity: Activity, key: String, splash: Boolean, onResult: (FullScreenResult) -> Unit) {
        val runtime = Adroit.liveRuntime
        if (runtime == null) {
            // Not initialized, or a test client stands in: the public interstitial API reports it.
            val placement = InterstitialPlacement(key)
            if (splash) {
                Adroit.interstitial.showOnSplash(activity, placement, onResult)
            } else {
                Adroit.interstitial.show(activity, placement, onResult)
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
