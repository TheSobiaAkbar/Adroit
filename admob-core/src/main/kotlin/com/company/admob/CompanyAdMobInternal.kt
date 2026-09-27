package com.company.admob

import com.company.admob.config.remote.AdOverrides
import com.company.admob.internal.config.AdProfiles
import com.company.admob.internal.native.NativeAdHandleImpl
import com.company.admob.logging.LogLevel

/** Hooks for CompanyAdMob's own modules only (ADR-012). See [InternalCompanyAdMobApi]. */
@InternalCompanyAdMobApi
public object CompanyAdMobInternal {
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
     * Makes the [CompanyAdMob] facade behave as [client] (its state, events and every API) until the returned
     * function is called, which restores the previous facade. For admob-testing's `CompanyAdMobTestRule`, so hosts
     * that call `CompanyAdMob` directly can still test with a fake. Test code only: never call it in an app.
     */
    @JvmStatic
    public fun installForTesting(client: AdsClient): () -> Unit = CompanyAdMob.installForTesting(client)

    /**
     * Logs [message] through the host's configured logger and level (`logging { }`), so the views and Compose
     * modules honour them too. Before `initialize()`, only warnings and errors are logged, to Logcat. Any thread.
     */
    @JvmStatic
    public fun log(level: LogLevel, message: String) {
        CompanyAdMob.log(level, message)
    }

    /**
     * Each configured placement's format, by placement key: the ad keys whose remote values admob-firebase reads.
     * Empty before `initialize()`. Any thread.
     */
    @JvmStatic
    public fun configuredPlacementFormats(): Map<String, AdFormat> = CompanyAdMob.configuredPlacementFormats()

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
