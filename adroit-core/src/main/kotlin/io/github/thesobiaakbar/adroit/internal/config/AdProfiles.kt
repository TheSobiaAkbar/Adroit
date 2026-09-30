package io.github.thesobiaakbar.adroit.internal.config

import android.graphics.Color
import io.github.thesobiaakbar.adroit.AdFormat
import io.github.thesobiaakbar.adroit.AppOpenPlacement
import io.github.thesobiaakbar.adroit.BannerPlacement
import io.github.thesobiaakbar.adroit.BannerSize
import io.github.thesobiaakbar.adroit.CollapsibleBannerPosition
import io.github.thesobiaakbar.adroit.InterstitialPlacement
import io.github.thesobiaakbar.adroit.NativePlacement
import io.github.thesobiaakbar.adroit.NativeStyle
import io.github.thesobiaakbar.adroit.RewardedInterstitialPlacement
import io.github.thesobiaakbar.adroit.RewardedPlacement
import io.github.thesobiaakbar.adroit.config.FrequencyRule
import io.github.thesobiaakbar.adroit.config.NativeLayoutNames
import io.github.thesobiaakbar.adroit.config.PlacementConfig
import io.github.thesobiaakbar.adroit.config.PlacementsBuilder
import io.github.thesobiaakbar.adroit.config.PreloadStrategy
import io.github.thesobiaakbar.adroit.config.SplashTimings
import io.github.thesobiaakbar.adroit.config.remote.AdOverrides
import io.github.thesobiaakbar.adroit.config.remote.PlacementOverride
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Ad profiles (architecture 05_AD_PROFILES.md): the ads file (`res/raw/ads_config.json`) and the Firebase value of
 * each ad. The same JSON describes an ad in both places. Pure and thread-safe; never throws. Every problem becomes a
 * message in the result's `issues`: in the file a bad ad is an error (validation reports it), in a Firebase value a
 * bad field is dropped and the rest applies.
 */
internal object AdProfiles {
    /** The settings in the file. Null means the file doesn't set it. */
    class FileSettings(
        val adsEnabled: Boolean,
        val maxInterstitialsPerSession: Int?,
        val minIntervalBetweenFullScreenAds: Duration,
        val splash: SplashTimings = SplashTimings(),
        val behavior: AdBehavior? = null,
    )

    /** [testIdKeys]: ads without ad unit IDs, given Google's test ad unit. [loadAgainKeys]: ads with `"loadAgain": true`. */
    class ParsedFile(
        val placements: List<PlacementConfig>,
        val settings: FileSettings,
        val issues: List<String>,
        val testIdKeys: Set<String> = emptySet(),
        val loadAgainKeys: Set<String> = emptySet(),
        /** Each ad's own loading behaviour, when it sets any. */
        val behaviors: Map<String, AdBehavior> = emptyMap(),
    )

    class ParsedRemote(val overrides: AdOverrides, val issues: List<String>)

    /** The parameter name of the settings object in Firebase. */
    const val DEFAULT_SETTINGS_KEY: String = "AdSettings"

    /** Default max height of an `inline_adaptive` banner, as in adroit-views' `adroit_banner_inline_max_height`. */
    const val INLINE_ADAPTIVE_MAX_HEIGHT_DP: Int = 250

    fun parseFile(text: String): ParsedFile {
        val issues = mutableListOf<String>()
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            issues += "Ads file: not valid JSON (${e.message})"
            return ParsedFile(emptyList(), defaultSettings(), issues)
        }
        val settings = root.optJSONObject("settings")?.let { parseFileSettings(it, issues) } ?: defaultSettings()
        val ads = root.optJSONObject("ads")
        if (ads == null) {
            issues += "Ads file: no \"ads\" object"
            return ParsedFile(emptyList(), settings, issues)
        }
        val builder = PlacementsBuilder()
        val testIdKeys = linkedSetOf<String>()
        val loadAgainKeys = linkedSetOf<String>()
        val behaviors = LinkedHashMap<String, AdBehavior>()
        for (key in ads.keys()) {
            val ad = ads.optJSONObject(key)
            if (ad == null) {
                issues += "Ads file: \"$key\" isn't a JSON object"
                continue
            }
            val issue: (String) -> Unit = { issues += "Ads file, \"$key\": $it" }
            if (addPlacement(builder, key, ad, issue)) testIdKeys += key
            val added = builder.configs.lastOrNull()?.takeIf { it.placement.key == key } ?: continue
            if (loadsAgain(ad, issue)) loadAgainKeys += key
            behaviorOf(ad, added.format, issue)?.let { behaviors[key] = it }
        }
        return ParsedFile(builder.configs.toList(), settings, issues, testIdKeys, loadAgainKeys, behaviors)
    }

    /**
     * Turns Firebase values into overrides. [values] maps a placement key to its raw JSON value; [formats] gives each
     * configured key's format (a value for any other key is ignored). [settings] is the raw settings value, or null.
     */
    fun parseRemote(formats: Map<String, AdFormat>, values: Map<String, String>, settings: String?): ParsedRemote {
        val issues = mutableListOf<String>()
        val placements = LinkedHashMap<String, PlacementOverride>()
        for ((key, raw) in values) {
            if (raw.isBlank()) continue
            val format = formats[key] ?: continue
            val json = parseObject(raw) { issues += "Firebase \"$key\" ignored: $it" } ?: continue
            parseOverride(key, format, json) { issues += "Firebase \"$key\": $it" }?.let { placements[key] = it }
        }
        var adsEnabled: Boolean? = null
        var maxInterstitials: Int? = null
        var minInterval: Duration? = null
        var splash: SplashTimings? = null
        var behavior: AdBehavior? = null
        if (!settings.isNullOrBlank()) {
            parseObject(settings) { issues += "Firebase settings ignored: $it" }?.let { json ->
                val fields = Fields(json) { issues += "Firebase settings: $it" }
                adsEnabled = fields.boolean("adsEnabled")
                maxInterstitials = fields.count("maxInterstitialsPerSession")?.let(::zeroMeansNoLimit)
                minInterval = fields.count("minSecondsBetweenFullScreenAds")?.seconds
                splash = fields.splashTimings()
                behavior = behaviorOf(json, format = null) { issues += "Firebase settings: $it" }
            }
        }
        return ParsedRemote(
            AdOverrides(
                splash = splash,
                adsEnabled = adsEnabled,
                placements = placements,
                minIntervalBetweenFullScreenAds = minInterval,
                maxInterstitialsPerSession = maxInterstitials,
            ).also { it.behavior = behavior },
            issues,
        )
    }

    private fun defaultSettings() = FileSettings(adsEnabled = true, maxInterstitialsPerSession = null, Duration.ZERO)

    private fun parseFileSettings(json: JSONObject, issues: MutableList<String>): FileSettings {
        val fields = Fields(json) { issues += "Ads file settings: $it" }
        return FileSettings(
            adsEnabled = fields.boolean("adsEnabled") ?: true,
            maxInterstitialsPerSession = fields.count("maxInterstitialsPerSession")?.let(::zeroMeansNoLimit),
            minIntervalBetweenFullScreenAds = (fields.count("minSecondsBetweenFullScreenAds") ?: 0).seconds,
            splash = fields.splashTimings() ?: SplashTimings(),
            behavior = behaviorOf(json, format = null) { issues += "Ads file settings: $it" },
        )
    }

    /** Adds [ad] as a placement. Returns true when it has no ad unit IDs and was given Google's test ad unit. */
    private fun addPlacement(builder: PlacementsBuilder, key: String, ad: JSONObject, issue: (String) -> Unit): Boolean {
        val fields = Fields(ad, issue)
        val formatName = fields.string("format")
        val format = formatName?.let(::formatOf)
        if (format == null) {
            issue(if (formatName == null) "\"format\" is missing" else "unknown format \"$formatName\"")
            return false
        }
        val placementKeyValid = KEY_PATTERN.matches(key)
        if (!placementKeyValid) {
            issue("the key must match ${KEY_PATTERN.pattern}")
            return false
        }
        val bannerSize = if (format == AdFormat.BANNER) fields.bannerSize() else null
        // No IDs (missing, or an empty list): Google's test ad unit for the format, in every environment.
        val listedIds = fields.adUnitIds().orEmpty()
        val usesTestIds = listedIds.isEmpty()
        val ids = if (usesTestIds) listOf(GoogleDemoAdUnits.forFormat(format, bannerSize)) else listedIds
        val enabled = fields.boolean("enabled") ?: true
        val maxPerSession = fields.count("maxPerSession")?.let(::zeroMeansNoLimit) ?: Int.MAX_VALUE
        val timer = (fields.count("timerSeconds") ?: 0).seconds
        val counter = fields.count("counter") ?: 0
        // Every ad loads only when the app asks (AdSlots.load, or a native view); "loadAgain" reloads it after a show.
        val preload = PreloadStrategy.NONE
        when (format) {
            AdFormat.INTERSTITIAL -> builder.interstitial(InterstitialPlacement(key), ids) {
                this.enabled = enabled
                frequency = FrequencyRule(timer, maxPerSession, counter)
                splash = fields.boolean("splash") ?: false
                fields.count("splashTimeoutSeconds")?.let { splashTimeout = it.seconds }
                this.preload = preload
            }
            AdFormat.REWARDED_INTERSTITIAL -> builder.rewardedInterstitial(RewardedInterstitialPlacement(key), ids) {
                this.enabled = enabled
                frequency = FrequencyRule(timer, maxPerSession, counter)
                this.preload = preload
            }
            AdFormat.REWARDED -> builder.rewarded(RewardedPlacement(key), ids) {
                this.enabled = enabled
                frequency = FrequencyRule(maxPerSession = maxPerSession)
                this.preload = preload
            }
            AdFormat.APP_OPEN -> builder.appOpen(AppOpenPlacement(key), ids) {
                this.enabled = enabled
                frequency = FrequencyRule(maxPerSession = maxPerSession)
                if (ad.has("timerSeconds")) cooldown = timer
                fields.strings("excludedActivities")?.let(::excludeActivityClassNames)
                this.preload = preload
            }
            AdFormat.BANNER -> builder.banner(BannerPlacement(key), ids) {
                this.enabled = enabled
                bannerSize?.let { size = it }
            }
            AdFormat.NATIVE -> builder.native(NativePlacement(key), ids) {
                this.enabled = enabled
                fields.nativeLook()?.let { look ->
                    look.style?.let { style = it }
                    layoutName = look.layoutName
                }
                fields.count("poolSize")?.let { poolSize = it }
                this.preload = preload
            }
        }
        return usesTestIds
    }

    /**
     * The loading and look fields of an ad of [format], or of the settings (null format): `loadTimeoutSeconds` and
     * `loadingSeconds` apply to full-screen ads only, `showLoading` to every ad, and the colors (`ctaColors`,
     * `ctaTextColor`, `attributionColor`, `attributionTextColor`) to native ads, and to a banner that becomes one.
     * Null when none is set.
     */
    private fun behaviorOf(json: JSONObject, format: AdFormat?, issue: (String) -> Unit): AdBehavior? {
        val fields = Fields(json, issue)
        val inline = format == AdFormat.BANNER || format == AdFormat.NATIVE
        if (inline) {
            for (name in listOf("loadTimeoutSeconds", "loadingSeconds")) {
                if (json.has(name)) issue("\"$name\" applies to full-screen ads only; ignored")
            }
        }
        val colored = format == null || inline
        if (!colored) {
            for (name in COLOR_FIELDS) if (json.has(name)) issue("\"$name\" applies to native ads only; ignored")
        }
        return AdBehavior(
            loadTimeout = if (inline) null else fields.count("loadTimeoutSeconds")?.seconds,
            showLoading = fields.boolean("showLoading"),
            loadingTime = if (inline) null else fields.count("loadingSeconds")?.seconds,
            ctaColors = if (colored) fields.colors("ctaColors") else null,
            ctaTextColor = if (colored) fields.color("ctaTextColor") else null,
            attributionColor = if (colored) fields.color("attributionColor") else null,
            attributionTextColor = if (colored) fields.color("attributionTextColor") else null,
        ).takeIfSet()
    }

    /** `"loadAgain"` of a file ad; banners, which load with their view, report it and ignore it. */
    private fun loadsAgain(ad: JSONObject, issue: (String) -> Unit): Boolean {
        val value = Fields(ad, issue).boolean("loadAgain") ?: return false
        if (ad.optString("format").trim().lowercase() == "banner") {
            issue("\"loadAgain\" doesn't apply to banners, which load with their view; ignored")
            return false
        }
        return value
    }

    private fun parseOverride(key: String, format: AdFormat, json: JSONObject, issue: (String) -> Unit): PlacementOverride? {
        val fields = Fields(json, issue)
        val formatName = fields.string("format")
        val remoteFormat = formatName?.let(::formatOf)
        val swapTo = remoteFormat?.takeIf { it != format && it == FormatSwap.partnerOf(format) }
        if (formatName != null && remoteFormat != format && swapTo == null) {
            val allowed = FormatSwap.partnerOf(format)?.let { " (it can only become ${it.name.lowercase()})" }.orEmpty()
            issue("ignored: its format \"$formatName\" differs from the app's ${format.name.lowercase()} \"$key\"$allowed")
            return null
        }
        if (format == AdFormat.BANNER && json.has("loadAgain")) {
            issue("\"loadAgain\" doesn't apply to banners, which load with their view; ignored")
        }
        for (fileOnly in listOf("splash", "excludedActivities", "poolSize")) {
            if (json.has(fileOnly)) issue("\"$fileOnly\" can only be set in the ads file; ignored")
        }
        // A swapped ad keeps its own frequency rules (counter, timer, caps); only its style follows the new format.
        val styleFormat = swapTo ?: format
        val look = if (styleFormat == AdFormat.NATIVE) fields.nativeLook() else null
        val fullScreenFrequency = format == AdFormat.INTERSTITIAL || format == AdFormat.REWARDED_INTERSTITIAL
        return PlacementOverride(
            enabled = fields.boolean("enabled"),
            minInterval = if (fullScreenFrequency || format == AdFormat.APP_OPEN) fields.count("timerSeconds")?.seconds else null,
            maxPerSession = if (format == AdFormat.BANNER || format == AdFormat.NATIVE) null else {
                fields.count("maxPerSession")?.let(::zeroMeansNoLimit)
            },
            adUnitIds = fields.adUnitIds()?.ifEmpty { null },
            counter = if (fullScreenFrequency) fields.count("counter") else null,
            bannerSize = if (styleFormat == AdFormat.BANNER) fields.bannerSize() else null,
            nativeStyle = look?.style,
            splashTimeout = if (format == AdFormat.INTERSTITIAL) fields.count("splashTimeoutSeconds")?.seconds else null,
            format = swapTo,
        ).also {
            if (format != AdFormat.BANNER) it.loadAgain = fields.boolean("loadAgain")
            it.nativeLayoutName = look?.layoutName
            it.behavior = behaviorOf(json, format, issue)
        }
    }

    private fun parseObject(raw: String, issue: (String) -> Unit): JSONObject? = try {
        JSONObject(raw)
    } catch (e: JSONException) {
        issue("not a valid JSON object (${e.message})")
        null
    }

    private fun zeroMeansNoLimit(count: Int): Int = if (count == 0) Int.MAX_VALUE else count

    private fun formatOf(name: String): AdFormat? = when (name.trim().lowercase()) {
        "interstitial" -> AdFormat.INTERSTITIAL
        "rewarded" -> AdFormat.REWARDED
        "rewarded_interstitial" -> AdFormat.REWARDED_INTERSTITIAL
        "app_open" -> AdFormat.APP_OPEN
        "banner" -> AdFormat.BANNER
        "native" -> AdFormat.NATIVE
        else -> null
    }

    private val KEY_PATTERN = Regex("[A-Za-z0-9_]{1,40}")
    private val COLOR_PATTERN = Regex("#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})")
    private val COLOR_FIELDS = listOf("ctaColors", "ctaTextColor", "attributionColor", "attributionTextColor")

    /** Typed reads of one JSON object; a wrong type is reported through [issue] and read as absent. */
    private class Fields(private val json: JSONObject, private val issue: (String) -> Unit) {
        /** The value of [name], with JSON null read as absent. */
        private fun raw(name: String): Any? = json.opt(name)?.takeUnless { it == JSONObject.NULL }

        fun boolean(name: String): Boolean? = when (val value = raw(name)) {
            null -> null
            is Boolean -> value
            else -> wrong(name, "true or false")
        }

        /** A whole number, 0 or more. */
        fun count(name: String): Int? = when (val value = raw(name)) {
            null -> null
            is Number -> {
                val d = value.toDouble()
                if (d < 0 || d != Math.floor(d) || d > Int.MAX_VALUE) wrong(name, "a whole number, 0 or more") else d.toInt()
            }
            else -> wrong(name, "a whole number, 0 or more")
        }

        fun string(name: String): String? = when (val value = raw(name)) {
            null -> null
            is String -> value
            else -> wrong(name, "a string")
        }

        /** A list of non-blank strings; other entries are reported and dropped. */
        fun strings(name: String): List<String>? = when (val value = raw(name)) {
            null -> null
            is JSONArray -> (0 until value.length()).mapNotNull { i ->
                (value.opt(i) as? String)?.trim()?.takeIf { it.isNotEmpty() }
                    ?: null.also { issue("\"$name\" item ${i + 1} isn't text; ignored") }
            }
            else -> wrong(name, "a list of strings")
        }

        /** A color: `"#RRGGBB"` or `"#AARRGGBB"`. */
        fun color(name: String): Int? {
            val text = string(name) ?: return null
            return parseColor(text) ?: wrong(name, "a color, \"#RRGGBB\" or \"#AARRGGBB\"")
        }

        /** One or two colors (a gradient from start to end): a list, or a single color as text. */
        fun colors(name: String): List<Int>? {
            val texts = when (val value = raw(name)) {
                null -> return null
                is String -> listOf(value)
                is JSONArray -> (0 until value.length()).map { value.opt(it) as? String ?: "" }
                else -> return wrong(name, "one or two colors, [\"#RRGGBB\", \"#RRGGBB\"]")
            }
            val parsed = texts.map(::parseColor)
            if (texts.size !in 1..2 || parsed.any { it == null }) {
                return wrong(name, "one or two colors, [\"#RRGGBB\", \"#RRGGBB\"]")
            }
            return parsed.filterNotNull()
        }

        private fun parseColor(text: String): Int? {
            val trimmed = text.trim()
            if (!COLOR_PATTERN.matches(trimmed)) return null
            return try {
                Color.parseColor(trimmed)
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        /** `adUnitIds` as a list; a single string is accepted as a one-item list. Blank entries are dropped. */
        fun adUnitIds(): List<String>? = when (val value = raw("adUnitIds")) {
            null -> null
            is String -> listOf(value.trim()).filter { it.isNotEmpty() }
            is JSONArray -> (0 until value.length()).mapNotNull { i ->
                (value.opt(i) as? String)?.trim()?.takeIf { it.isNotEmpty() }
                    ?: null.also { issue("\"adUnitIds\" item ${i + 1} isn't a text ID; ignored") }
            }
            else -> wrong("adUnitIds", "a list of ad unit IDs")
        }

        /** The splash timings set here (each a positive number of seconds), or null when none is. */
        fun splashTimings(): SplashTimings? {
            fun seconds(name: String): Int? = count(name)?.let { value ->
                if (value > 0) value else null.also { issue("\"$name\" must be at least 1; ignored") }
            }
            val timings = SplashTimings(
                seconds("splashSeconds"),
                seconds("premiumSplashSeconds"),
                seconds("offlineSplashSeconds"),
                seconds("consentTimeoutSeconds"),
            )
            return timings.takeUnless { it == SplashTimings() }
        }

        fun bannerSize(): BannerSize? {
            val style = string("style") ?: return null
            return when (style.trim().lowercase()) {
                "adaptive" -> BannerSize.AnchoredAdaptive
                "collapsible_bottom" -> BannerSize.Collapsible(CollapsibleBannerPosition.BOTTOM)
                "collapsible_top" -> BannerSize.Collapsible(CollapsibleBannerPosition.TOP)
                "rectangle" -> BannerSize.MediumRectangle
                "standard" -> BannerSize.Standard
                "inline_adaptive" -> BannerSize.InlineAdaptive(INLINE_ADAPTIVE_MAX_HEIGHT_DP)
                else -> wrong("style", "adaptive, collapsible_bottom, collapsible_top, rectangle, standard or inline_adaptive")
            }
        }

        /** A library style, or the name of an app layout registered with `NativeTemplates.register`. */
        fun nativeLook(): NativeLook? {
            val raw = string("style") ?: return null
            val name = raw.trim().lowercase()
            NativeLayoutNames.styleNamed(name)?.let { return NativeLook(it, null) }
            if (NativeLayoutNames.isValid(name)) return NativeLook(null, name)
            wrong("style", "${NativeLayoutNames.styleNames}, or a registered layout's name (letters, digits, _)")
            return null
        }

        private fun wrong(name: String, expected: String): Nothing? {
            issue("\"$name\" must be $expected; ignored")
            return null
        }
    }
}

/** A native ad's `"style"`: a library [style], or the [layoutName] of an app layout (exactly one is set). */
internal class NativeLook(val style: NativeStyle?, val layoutName: String?)
