package io.github.thesobiaakbar.adroit

import android.app.Activity
import io.github.thesobiaakbar.adroit.config.remote.AdOverrides
import io.github.thesobiaakbar.adroit.internal.banner.BannerAdHandleImpl
import io.github.thesobiaakbar.adroit.internal.config.AdProfiles
import io.github.thesobiaakbar.adroit.internal.native.NativeAdHandleImpl
import io.github.thesobiaakbar.adroit.logging.LogLevel

/** Hooks for Adroit's own modules only (ADR-012). See [InternalAdroitApi]. */
@InternalAdroitApi
public object AdroitInternal {
    /**
     * Detaches [handle] from the view it was last rendered into, without destroying the ad, so a holder that keeps
     * the handle across configuration changes (`NativeAdHolder`) retains no View or Context between renders. Main
     * thread only. Does nothing for a handle that isn't from `native.load()`.
     */
    @JvmStatic
    public fun detachRenderedView(handle: NativeAdHandle) {
        (handle as? NativeAdHandleImpl)?.detachView()
    }

    /**
     * Takes a banner off screen while its screen lives on (a composable left the composition): its loaded ad is kept
     * without its view, so the banner retains no View or Activity; [showBannerIn] shows the same ad again, with no new
     * request. A banner not loaded yet drops its request, and loads again in [showBannerIn]. Main thread only. Does
     * nothing for a handle that isn't from `banner.create()`.
     */
    @JvmStatic
    public fun parkBanner(handle: BannerAdHandle) {
        (handle as? BannerAdHandleImpl)?.park()
    }

    /**
     * Puts a banner back on screen in [activity] (its `view` is then a new view of that Activity): a parked ad without
     * a new request, else a new load. Moves a banner shown in another Activity (after a configuration change). Does
     * nothing for a banner already in [activity]. Main thread only.
     */
    @JvmStatic
    public fun showBannerIn(handle: BannerAdHandle, activity: Activity) {
        (handle as? BannerAdHandleImpl)?.showIn(activity)
    }

    /**
     * Makes the [Adroit] facade behave as [client] (its state, events and every API) until the returned
     * function is called, which restores the previous facade. For adroit-testing's `AdroitTestRule`, so hosts
     * that call `Adroit` directly can still test with a fake. Test code only: never call it in an app.
     */
    @JvmStatic
    public fun installForTesting(client: AdsClient): () -> Unit = Adroit.installForTesting(client)

    /**
     * Logs [message] through the host's configured logger and level (`logging { }`), so the views and Compose
     * modules honour them too. Before `initialize()`, only warnings and errors are logged, to Logcat. Any thread.
     */
    @JvmStatic
    public fun log(level: LogLevel, message: String) {
        Adroit.log(level, message)
    }

    /**
     * Whether the ad [key] (in its current format) shows a loading placeholder while it loads: a shimmer for a banner
     * or native ad (PROMPT 29C). True only for ad profiles with `showLoading`; false before `initialize()`, for
     * placements declared in `placements { }`, and while a test client stands in. Any thread.
     */
    @JvmStatic
    public fun showsLoading(key: String): Boolean =
        Adroit.liveRuntime?.liveConfig?.current?.active(key)?.behavior?.showLoading == true

    /**
     * The registered app layout a native ad [key] shows now (its ad profile's, or its Firebase `"style"`), or null
     * for a library template (`NativeApi.style`). For adroit-views and adroit-compose; any thread.
     */
    @JvmStatic
    public fun nativeLayoutName(key: String): String? =
        Adroit.liveRuntime?.liveConfig?.current?.active(key)?.nativeLayoutName

    /**
     * Each configured placement's format, by placement key: the ad keys whose remote values adroit-firebase reads.
     * Empty before `initialize()`. Any thread.
     */
    @JvmStatic
    public fun configuredPlacementFormats(): Map<String, AdFormat> = Adroit.configuredPlacementFormats()

    /**
     * Turns remote ad profile values (05_AD_PROFILES.md) into overrides: [values] maps a placement key to its raw JSON
     * value, [formats] gives each key's format, and [settings] is the raw settings value or null. Problems are logged
     * as warnings, and what's valid still applies. Pure apart from logging; any thread.
     */
    @JvmStatic
    public fun parseRemoteProfiles(formats: Map<String, AdFormat>, values: Map<String, String>, settings: String?): AdOverrides {
        val parsed = AdProfiles.parseRemote(formats, values, settings)
        parsed.issues.forEach { log(LogLevel.WARN, it) }
        return parsed.overrides
    }
}
