package io.github.thesobiaakbar.adroit.views.internal

import io.github.thesobiaakbar.adroit.AdsClient
import io.github.thesobiaakbar.adroit.Adroit
import io.github.thesobiaakbar.adroit.AdroitInternal
import io.github.thesobiaakbar.adroit.InternalAdroitApi
import io.github.thesobiaakbar.adroit.logging.LogLevel

/**
 * Where the views helpers get their [AdsClient]: `Adroit` itself, or a test double set by this module's tests.
 * Also their log, which goes through the host's configured logger and level (ADR-012).
 */
@OptIn(InternalAdroitApi::class)
internal object ViewsRuntime {
    @Volatile
    var clientOverride: AdsClient? = null

    val client: AdsClient get() = clientOverride ?: Adroit.client

    /** For this module's tests: which keys show a shimmer while loading. */
    @Volatile
    var showsLoadingOverride: ((String) -> Boolean)? = null

    /** For this module's tests: the registered layout name of each native key. */
    @Volatile
    var nativeLayoutNameOverride: ((String) -> String?)? = null

    /** The registered app layout the native ad [key] shows now, or null for its style's template. */
    fun nativeLayoutName(key: String): String? =
        nativeLayoutNameOverride?.let { return it(key) } ?: AdroitInternal.nativeLayoutName(key)

    /** Whether the ad [key] shows a shimmer while it loads (its `showLoading`). */
    fun showsLoading(key: String): Boolean = showsLoadingOverride?.invoke(key) ?: AdroitInternal.showsLoading(key)

    fun error(message: String) {
        AdroitInternal.log(LogLevel.ERROR, message)
    }

    fun warn(message: String) {
        AdroitInternal.log(LogLevel.WARN, message)
    }

    fun debug(message: String) {
        AdroitInternal.log(LogLevel.DEBUG, message)
    }
}
