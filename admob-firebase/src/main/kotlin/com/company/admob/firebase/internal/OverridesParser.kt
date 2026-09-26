package com.company.admob.firebase.internal

import com.company.admob.AdFormat
import com.company.admob.config.PreloadStrategy
import com.company.admob.config.remote.AdOverrides
import com.company.admob.config.remote.PlacementOverride
import com.company.admob.logging.LogLevel
import org.json.JSONException
import org.json.JSONObject
import kotlin.time.Duration.Companion.seconds

/**
 * Maps the documented Remote Config keys to [AdOverrides]. A key that's unset or empty leaves the local
 * configuration alone; a key with an invalid value is logged and ignored, and the other keys still apply. Values
 * that parse but are out of range (a negative interval) are left to CompanyAdMob's own override validation.
 */
internal object OverridesParser {
    const val KEY_ENABLED = "company_admob_enabled"
    const val KEY_DISABLED_FORMATS = "company_admob_disabled_formats"
    const val KEY_MIN_FULLSCREEN_INTERVAL = "company_admob_min_fullscreen_interval_seconds"
    const val KEY_PLACEMENTS = "company_admob_placements_json"

    fun parse(source: RemoteConfigSource): AdOverrides = AdOverrides(
        adsEnabled = source.value(KEY_ENABLED)?.let(::parseBoolean),
        disabledFormats = source.value(KEY_DISABLED_FORMATS)?.let(::parseFormats).orEmpty(),
        placements = source.value(KEY_PLACEMENTS)?.let(::parsePlacements).orEmpty(),
        minIntervalBetweenFullScreenAds = source.value(KEY_MIN_FULLSCREEN_INTERVAL)
            ?.let { parseLong(KEY_MIN_FULLSCREEN_INTERVAL, it) }?.seconds,
    )

    private fun RemoteConfigSource.value(key: String): String? = string(key)?.trim()?.takeIf(String::isNotEmpty)

    private fun parseBoolean(value: String): Boolean? = when (value.lowercase()) {
        "true", "1" -> true
        "false", "0" -> false
        else -> null.also { invalid(KEY_ENABLED, "'$value' isn't true or false") }
    }

    private fun parseLong(key: String, value: String): Long? =
        value.toLongOrNull() ?: null.also { invalid(key, "'$value' isn't a whole number") }

    private fun parseFormats(value: String): Set<AdFormat> =
        value.split(',').map(String::trim).filter(String::isNotEmpty).mapNotNull { name ->
            AdFormat.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: null.also { invalid(KEY_DISABLED_FORMATS, "unknown format '$name'") }
        }.toSet()

    /** `{"level_complete": {"enabled": false, "minIntervalSeconds": 90, ...}, ...}`. Invalid JSON: all ignored. */
    private fun parsePlacements(value: String): Map<String, PlacementOverride>? {
        val root = try {
            JSONObject(value)
        } catch (e: JSONException) {
            invalid(KEY_PLACEMENTS, "invalid JSON (${e.message})")
            return null
        }
        return try {
            root.keys().asSequence().associateWith { key -> root.getJSONObject(key).toPlacementOverride(key) }
        } catch (e: JSONException) {
            invalid(KEY_PLACEMENTS, "each placement must be a JSON object (${e.message})")
            null
        }
    }

    private fun JSONObject.toPlacementOverride(placementKey: String): PlacementOverride {
        fun <T : Any> field(name: String, read: (String) -> T): T? = if (!has(name) || isNull(name)) {
            null
        } else {
            try {
                read(name)
            } catch (e: JSONException) {
                invalid(KEY_PLACEMENTS, "$placementKey.$name ignored (${e.message})")
                null
            }
        }
        return PlacementOverride(
            enabled = field("enabled", ::getBoolean),
            minInterval = field("minIntervalSeconds", ::getLong)?.seconds,
            maxPerSession = field("maxPerSession", ::getInt),
            preload = field("preload", ::getString)?.let { name ->
                PreloadStrategy.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ?: null.also { invalid(KEY_PLACEMENTS, "$placementKey.preload: unknown strategy '$name'") }
            },
            adUnitId = field("adUnitId", ::getString),
        )
    }

    private fun invalid(key: String, problem: String) {
        log(LogLevel.WARN, "Firebase Remote Config $key ignored: $problem")
    }
}
