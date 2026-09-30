package io.github.thesobiaakbar.adroit.compose.internal

import io.github.thesobiaakbar.adroit.AdsClient
import io.github.thesobiaakbar.adroit.Adroit
import io.github.thesobiaakbar.adroit.AdroitInternal
import io.github.thesobiaakbar.adroit.InternalAdroitApi
import io.github.thesobiaakbar.adroit.NativeAdHandle
import io.github.thesobiaakbar.adroit.logging.LogLevel

/**
 * Where the composables get their [AdsClient]: `Adroit` itself, or a test double set by this module's tests.
 * Also their log, which goes through the host's configured logger and level (ADR-012).
 */
@OptIn(InternalAdroitApi::class)
internal object ComposeRuntime {
    @Volatile
    var clientOverride: AdsClient? = null

    val client: AdsClient get() = clientOverride ?: Adroit.client

    /** For this module's tests: which keys show a shimmer while loading. */
    @Volatile
    var showsLoadingOverride: ((String) -> Boolean)? = null

    /** Whether the ad [key] shows a shimmer while it loads (its `showLoading`, PROMPT 29C). */
    fun showsLoading(key: String): Boolean = showsLoadingOverride?.invoke(key) ?: AdroitInternal.showsLoading(key)

    /** Drops a rendered native view without destroying the ad (ADR-012). Replaceable by tests. */
    var detachRenderedView: (NativeAdHandle) -> Unit = { AdroitInternal.detachRenderedView(it) }

    fun error(message: String) {
        AdroitInternal.log(LogLevel.ERROR, message)
    }

    fun debug(message: String) {
        AdroitInternal.log(LogLevel.DEBUG, message)
    }
}
