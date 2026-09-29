package com.company.admob.config

import com.company.admob.NativeStyle

/** The names of native styles and registered app layouts, as `"style"` holds them. */
internal object NativeLayoutNames {
    private val VALID = Regex("[a-z0-9_]{1,64}")

    /** [name] in lower case, trimmed; rejects a name that isn't 1 to 64 letters, digits or underscores. */
    fun normalize(name: String): String {
        val normalized = name.trim().lowercase()
        require(VALID.matches(normalized)) {
            "A native layout name must be 1 to 64 letters, digits or underscores: \"$name\""
        }
        return normalized
    }

    /** A style's name in the JSON: its enum name in lower case. */
    fun of(style: NativeStyle): String = style.name.lowercase()

    /** The library style named [name], or null. */
    fun styleNamed(name: String): NativeStyle? = NativeStyle.entries.firstOrNull { of(it) == name }

    /** Whether [name] can name an app layout. */
    fun isValid(name: String): Boolean = VALID.matches(name)

    /** Every library style's name, for messages. */
    val styleNames: String get() = NativeStyle.entries.joinToString(", ") { of(it) }
}
