package io.github.thesobiaakbar.adroit.billing.internal.config

import org.json.JSONArray
import org.json.JSONObject
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Lenient typed reads of one JSON object, as in the ad profiles: a missing field (or JSON null) is null, a wrong one
 * is reported through [issue] and read as null, so the next layer's value applies. [path] prefixes field names in
 * messages, for example `"close."`.
 */
internal class JsonFields(private val json: JSONObject, private val path: String, private val issue: (String) -> Unit) {
    private fun raw(name: String): Any? = json.opt(name)?.takeUnless { it == JSONObject.NULL }

    fun <T> wrong(name: String, expected: String): T? {
        issue("\"$path$name\" must be $expected; ignored")
        return null
    }

    fun boolean(name: String): Boolean? = when (val value = raw(name)) {
        null -> null
        is Boolean -> value
        else -> wrong(name, "true or false")
    }

    /** A whole number in [range]. */
    fun int(name: String, range: IntRange): Int? {
        val expected = "a whole number from ${range.first}" + if (range.last == Int.MAX_VALUE) "" else " to ${range.last}"
        val value = raw(name) ?: return null
        val d = (value as? Number)?.toDouble() ?: return wrong(name, expected)
        return if (d != Math.floor(d) || d < range.first || d > range.last) wrong(name, expected) else d.toInt()
    }

    fun string(name: String): String? = when (val value = raw(name)) {
        null -> null
        is String -> value
        else -> wrong(name, "text")
    }

    /** Text of at most [MAX_TEXT] characters, without HTML. */
    fun text(name: String): String? = string(name)?.let { checkText(it) ?: wrong(name, TEXT_RULE) }

    /** A list of strings that each pass [check]; other entries are reported and dropped. */
    fun strings(name: String, check: (String) -> Boolean = { it.isNotBlank() }): List<String>? = when (val value = raw(name)) {
        null -> null
        is JSONArray -> (0 until value.length()).mapNotNull { i ->
            (value.opt(i) as? String)?.takeIf(check) ?: null.also { issue("\"$path$name\" item ${i + 1} is invalid; ignored") }
        }
        else -> wrong(name, "a list of strings")
    }

    fun obj(name: String): JsonFields? = when (val value = raw(name)) {
        null -> null
        is JSONObject -> JsonFields(value, "$path$name.", issue)
        else -> wrong(name, "an object")
    }

    fun array(name: String): JSONArray? = when (val value = raw(name)) {
        null -> null
        is JSONArray -> value
        else -> wrong(name, "a list")
    }

    /** One of [values], by name, ignoring case. */
    fun <E : Enum<E>> enum(name: String, values: Array<E>): E? {
        val text = string(name) ?: return null
        return values.firstOrNull { it.name.equals(text.trim(), ignoreCase = true) }
            ?: wrong(name, values.joinToString("|", "one of ") { it.name })
    }

    /** `"#RRGGBB"` or `"#AARRGGBB"` as an ARGB color. */
    fun color(name: String): Int? {
        val text = string(name) ?: return null
        return parseColor(text) ?: wrong(name, "a color, \"#RRGGBB\" or \"#AARRGGBB\"")
    }

    /** An ISO-8601 instant with a zone, for example `"2026-10-01T00:00:00Z"`, as epoch milliseconds. */
    fun instant(name: String): Long? {
        val text = string(name) ?: return null
        return parseInstant(text) ?: wrong(name, "an ISO-8601 time with a zone, like \"2026-10-01T00:00:00Z\"")
    }

    /** A version: a number or text. */
    fun version(name: String): String? = when (val value = raw(name)) {
        null -> null
        is String -> value.takeIf { it.isNotBlank() } ?: wrong(name, "a number or text")
        is Number -> value.toDouble().let { if (it == Math.floor(it)) it.toLong().toString() else value.toString() }
        else -> wrong(name, "a number or text")
    }

    companion object {
        const val MAX_TEXT = 300
        const val TEXT_RULE = "text of up to $MAX_TEXT characters, without HTML"
        private val HTML = Regex("""<\s*[a-zA-Z/!]|&[a-zA-Z#][a-zA-Z0-9]*;""")
        private val HEX = Regex("#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})")
        private val PATTERNS = listOf("yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

        fun checkText(text: String): String? = text.takeIf { it.length <= MAX_TEXT && !HTML.containsMatchIn(it) }

        fun parseColor(text: String): Int? {
            if (!HEX.matches(text)) return null
            val value = text.substring(1).toLong(16)
            return (if (text.length == 7) value or 0xFF000000 else value).toInt()
        }

        fun formatColor(color: Int): String =
            if (color ushr 24 == 0xFF) "#%06X".format(color and 0xFFFFFF) else "#%08X".format(color)

        // ponytail: SimpleDateFormat because the library has no core library desugaring (java.time needs API 26).
        fun parseInstant(text: String): Long? = PATTERNS.firstNotNullOfOrNull { pattern ->
            val position = ParsePosition(0)
            val date = utc(pattern).parse(text, position)
            date?.time?.takeIf { position.index == text.length }
        }

        fun formatInstant(millis: Long): String =
            utc(if (millis % 1000 == 0L) "yyyy-MM-dd'T'HH:mm:ss'Z'" else "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(millis)

        private fun utc(pattern: String) = SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }
    }
}
