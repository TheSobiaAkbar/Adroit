package io.github.thesobiaakbar.adroit.internal.config

import io.github.thesobiaakbar.adroit.AdCloseButton
import io.github.thesobiaakbar.adroit.BannerSize
import io.github.thesobiaakbar.adroit.CollapsibleBannerPosition
import io.github.thesobiaakbar.adroit.NativeStyle
import io.github.thesobiaakbar.adroit.config.AdDefinition
import io.github.thesobiaakbar.adroit.config.AdProfile
import io.github.thesobiaakbar.adroit.config.AdSettings
import io.github.thesobiaakbar.adroit.config.NativeLayoutNames
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Writes [AdDefinition]s in the ads file's JSON (05_AD_PROFILES.md), so enum defaults go through the same parser and
 * validation as a file, and each entry's JSON is exactly what its Firebase parameter takes.
 */
internal object AdDefinitionJson {
    /** A whole ads file: `{"settings": …, "ads": {name: profile, …}}`, in definition order. */
    fun file(definitions: Iterable<AdDefinition>, settings: AdSettings): JSONObject {
        val ads = JSONObject()
        definitions.forEach { ads.put(it.name, profile(it.defaults, firebase = false)) }
        return JSONObject().put("settings", settings(settings)).put("ads", ads)
    }

    fun settings(settings: AdSettings): JSONObject = JSONObject()
        .put("adsEnabled", settings.adsEnabled)
        .put("maxInterstitialsPerSession", settings.maxInterstitialsPerSession)
        .put("minSecondsBetweenFullScreenAds", settings.minSecondsBetweenFullScreenAds)
        .put("splashSeconds", settings.splashSeconds)
        .put("premiumSplashSeconds", settings.premiumSplashSeconds)
        .put("offlineSplashSeconds", settings.offlineSplashSeconds)
        .put("consentTimeoutSeconds", settings.consentTimeoutSeconds)
        .put("loadTimeoutSeconds", settings.loadTimeoutSeconds)
        .put("showLoading", settings.showLoading)
        .put("loadingSeconds", settings.loadingSeconds)
        // Written only when set, so the JSON of an app without a close button stays as it was.
        .apply { if (settings.closeButton != AdCloseButton.NONE) put("closeButton", settings.closeButton.jsonName) }
        .apply { settings.closeIcon?.let { put("closeIcon", it) } }

    /**
     * [profile] as JSON. With [firebase], the file-only fields (`splash`, `excludedActivities`, `poolSize`) are left
     * out: Firebase ignores them with a warning, and the definition keeps them anyway.
     */
    fun profile(profile: AdProfile, firebase: Boolean = true): JSONObject {
        val json = JSONObject()
            .put("format", formatName(profile))
            .put("enabled", profile.enabled)
            .put("adUnitIds", JSONArray(profile.adUnitIds))
        when (profile) {
            is AdProfile.Interstitial -> json
                .putCounter(
                    profile.counter, profile.counters, profile.counterStart, profile.counterStep,
                    profile.counterResetOnFail, profile.loadAhead,
                )
                .put("timerSeconds", profile.timerSeconds)
                .put("maxPerSession", profile.maxPerSession)
                .apply {
                    if (profile.splash) {
                        if (!firebase) put("splash", true)
                        put("splashTimeoutSeconds", profile.splashTimeoutSeconds)
                    }
                }
            is AdProfile.RewardedInterstitial -> json
                .putCounter(
                    profile.counter, profile.counters, profile.counterStart, profile.counterStep,
                    profile.counterResetOnFail, profile.loadAhead,
                )
                .put("timerSeconds", profile.timerSeconds)
                .put("maxPerSession", profile.maxPerSession)
            is AdProfile.Rewarded -> json.put("maxPerSession", profile.maxPerSession)
            is AdProfile.AppOpen -> json
                .apply { profile.timerSeconds?.let { put("timerSeconds", it) } }
                .put("maxPerSession", profile.maxPerSession)
                .apply {
                    if (!firebase && profile.excludedActivities.isNotEmpty()) {
                        put("excludedActivities", JSONArray(profile.excludedActivities))
                    }
                }
            is AdProfile.Banner -> json.put("style", bannerStyle(profile.style))
                .putClose(profile.closeButton, profile.closeIcon)
            is AdProfile.Native -> json.put("style", profile.layoutName ?: nativeStyle(profile.style))
                .apply { if (!firebase) put("poolSize", profile.poolSize) }
                .putClose(profile.closeButton, profile.closeIcon)
        }
        if (profile !is AdProfile.Banner) json.put("loadAgain", profile.loadAgain)
        // Unset: the ad follows AdSettings.
        profile.loadTimeoutSeconds?.let { json.put("loadTimeoutSeconds", it) }
        profile.showLoading?.let { json.put("showLoading", it) }
        profile.loadingSeconds?.let { json.put("loadingSeconds", it) }
        return json
    }

    /**
     * A Firebase Remote Config template (the console's "Publish from a file"): one JSON parameter per definition with
     * its defaults as the value, plus [settingsKey] with [settings].
     */
    fun firebaseTemplate(definitions: Iterable<AdDefinition>, settings: AdSettings, settingsKey: String): JSONObject {
        val parameters = JSONObject()
        fun add(key: String, value: JSONObject, description: String) {
            parameters.put(
                key,
                JSONObject()
                    .put("defaultValue", JSONObject().put("value", value.toString()))
                    .put("valueType", "JSON")
                    .put("description", description),
            )
        }
        add(settingsKey, settings(settings), "Settings of every ad (docs/AD_PROFILES.md)")
        definitions.forEach { add(it.name, profile(it.defaults), "Ad ${it.name} (docs/AD_PROFILES.md)") }
        return JSONObject().put("parameters", parameters)
    }

    /** `counter` (a number, or the list of max counters) and, for an ad that has a counter, its other counter fields. */
    private fun JSONObject.putCounter(
        counter: Int,
        counters: List<Double>,
        start: Double,
        step: Double,
        resetOnFail: Boolean,
        loadAhead: Int,
    ): JSONObject {
        if (counters.isEmpty()) put("counter", counter) else put("counter", JSONArray(counters.map(::number)))
        if (counter == 0 && counters.isEmpty()) return this
        return put("counterStart", number(start))
            .put("counterStep", number(step))
            .put("counterResetOnFail", resetOnFail)
            .put("loadAhead", loadAhead)
    }

    /** A banner's or native ad's own close button, when it sets one; unset, it follows the settings. */
    private fun JSONObject.putClose(button: AdCloseButton?, icon: String?): JSONObject = apply {
        button?.let { put("closeButton", it.jsonName) }
        icon?.let { put("closeIcon", it) }
    }

    private val AdCloseButton.jsonName: String get() = name.lowercase(Locale.ROOT)

    /** 3.0 as 3, 1.5 as 1.5. */
    private fun number(value: Double): Number = if (value == Math.floor(value)) value.toLong() else value

    private fun formatName(profile: AdProfile): String = when (profile) {
        is AdProfile.Interstitial -> "interstitial"
        is AdProfile.RewardedInterstitial -> "rewarded_interstitial"
        is AdProfile.Rewarded -> "rewarded"
        is AdProfile.AppOpen -> "app_open"
        is AdProfile.Banner -> "banner"
        is AdProfile.Native -> "native"
    }

    private fun bannerStyle(size: BannerSize): String = when (size) {
        BannerSize.AnchoredAdaptive -> "adaptive"
        is BannerSize.Collapsible -> when (size.position) {
            CollapsibleBannerPosition.BOTTOM -> "collapsible_bottom"
            CollapsibleBannerPosition.TOP -> "collapsible_top"
        }
        BannerSize.MediumRectangle -> "rectangle"
        BannerSize.Standard -> "standard"
        is BannerSize.InlineAdaptive -> "inline_adaptive"
    }

    private fun nativeStyle(style: NativeStyle): String = NativeLayoutNames.of(style)
}
