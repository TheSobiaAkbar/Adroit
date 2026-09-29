package com.company.admob.firebase

import com.company.admob.firebase.internal.ActivationSignal
import com.company.admob.firebase.internal.FirebaseRemoteConfigSource
import com.company.admob.firebase.internal.RemoteConfigSource
import com.company.admob.firebase.internal.log
import com.company.admob.logging.LogLevel
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONException
import org.json.JSONObject

/**
 * One of the app's own Firebase Remote Config parameters (anything besides the ads), with its in-app default
 * (docs/REMOTE_CONFIG.md). Declare them once, for example in an `object`, and read [value] anywhere:
 *
 * ```
 * object AppConfig {
 *     val showRateDialog = RemoteValue.boolean("show_rate_dialog", default = false)
 *     val freeLevels = RemoteValue.long("free_levels", default = 5)
 * }
 * if (AppConfig.showRateDialog.value) showRateDialog()
 * ```
 *
 * The values come from the same fetch as the ads: `RemoteConfigSession` fetches once per session and never makes the
 * app wait. Until this session's fetch lands, [value] is the one from the last session's fetch, or [default] on the
 * very first launch. A parameter missing from Firebase, or one that isn't a valid value of its type, reads as
 * [default] (an invalid one is logged).
 *
 * @property key the parameter's name in the Firebase console.
 * @property default the value used when Firebase doesn't set a valid one.
 */
public class RemoteValue<T : Any> internal constructor(
    public val key: String,
    public val default: T,
    private val parse: (String) -> T?,
    private val copy: (T) -> T = { it },
) {
    /** The value in force now: Firebase's active one, or [default]. Never waits; any thread. */
    public val value: T
        get() = resolve(raw())

    /**
     * [value] now, then the new value each time Remote Config activates a different one (at most once per session,
     * when `RemoteConfigSession`'s fetch lands). Collect it where a screen must follow a change made in Firebase
     * while the app runs.
     */
    public fun updates(): Flow<T> = callbackFlow {
        trySend(raw())
        val remove = ActivationSignal.add { trySend(raw()) }
        awaitClose(remove)
    }.distinctUntilChanged().map(::resolve)

    override fun toString(): String = "RemoteValue(key=$key, default=$default)"

    private fun raw(): String? = try {
        (sourceOverride ?: sessionSource())?.string(key)
    } catch (e: Exception) {
        // Firebase isn't initialized (no google-services.json, or a process without it).
        null
    }

    private fun resolve(raw: String?): T {
        if (raw == null) return copy(default)
        val parsed = try {
            parse(raw)
        } catch (e: Exception) {
            null
        }
        if (parsed == null) {
            log(LogLevel.WARN, "Remote Config \"$key\": \"${raw.take(MAX_LOGGED)}\" isn't a valid value; using the default")
            return copy(default)
        }
        return parsed
    }

    /** Declaring a parameter by type. */
    public companion object {
        private const val MAX_LOGGED = 80

        /** Replaces Firebase in unit tests. */
        @Volatile
        internal var sourceOverride: RemoteConfigSource? = null

        private fun sessionSource(): RemoteConfigSource =
            RemoteConfigSession.current()?.source ?: FirebaseRemoteConfigSource(FirebaseRemoteConfig.getInstance())

        /** A true/false parameter. Firebase's `true`, `false`, `1`, `0`, `yes`, `no`, `on` and `off` are accepted. */
        @JvmStatic
        public fun boolean(key: String, default: Boolean): RemoteValue<Boolean> =
            RemoteValue(key, default, ::parseBoolean)

        /** A whole-number parameter. */
        @JvmStatic
        public fun long(key: String, default: Long): RemoteValue<Long> =
            RemoteValue(key, default, { it.trim().toLongOrNull() })

        /** A decimal-number parameter. */
        @JvmStatic
        public fun double(key: String, default: Double): RemoteValue<Double> =
            RemoteValue(key, default, { it.trim().toDoubleOrNull()?.takeIf(Double::isFinite) })

        /** A text parameter. An empty string set in Firebase is a value, not the default. */
        @JvmStatic
        public fun string(key: String, default: String): RemoteValue<String> =
            RemoteValue(key, default, { it })

        /**
         * A JSON object parameter (the console's JSON type). Each read returns a new object, so changing it never
         * changes the next read. Something other than a JSON object reads as [default].
         */
        @JvmStatic
        @JvmOverloads
        public fun json(key: String, default: JSONObject = JSONObject()): RemoteValue<JSONObject> =
            RemoteValue(key, JSONObject(default.toString()), ::parseJson, { JSONObject(it.toString()) })

        /**
         * A parameter of the app's own type: [parse] turns Firebase's text into it, or returns null (or throws) when
         * the text isn't valid, which reads as [default]. For example a JSON object parsed into a data class.
         */
        @JvmStatic
        public fun <T : Any> custom(key: String, default: T, parse: (String) -> T?): RemoteValue<T> =
            RemoteValue(key, default, parse)

        private fun parseBoolean(raw: String): Boolean? = when (raw.trim().lowercase()) {
            "true", "1", "yes", "y", "on", "t" -> true
            "false", "0", "no", "n", "off", "f" -> false
            else -> null
        }

        private fun parseJson(raw: String): JSONObject? = try {
            JSONObject(raw)
        } catch (e: JSONException) {
            null
        }
    }
}
