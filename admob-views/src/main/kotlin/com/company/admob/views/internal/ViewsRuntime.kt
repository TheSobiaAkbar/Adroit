package com.company.admob.views.internal

import com.company.admob.AdsClient
import com.company.admob.CompanyAdMob
import com.company.admob.CompanyAdMobInternal
import com.company.admob.InternalCompanyAdMobApi
import com.company.admob.logging.LogLevel

/**
 * Where the views helpers get their [AdsClient]: `CompanyAdMob` itself, or a test double set by this module's tests.
 * Also their log, which goes through the host's configured logger and level (ADR-012).
 */
@OptIn(InternalCompanyAdMobApi::class)
internal object ViewsRuntime {
    @Volatile
    var clientOverride: AdsClient? = null

    val client: AdsClient get() = clientOverride ?: CompanyAdMob.client

    /** For this module's tests: which keys show a shimmer while loading. */
    @Volatile
    var showsLoadingOverride: ((String) -> Boolean)? = null

    /** For this module's tests: the registered layout name of each native key. */
    @Volatile
    var nativeLayoutNameOverride: ((String) -> String?)? = null

    /** The registered app layout the native ad [key] shows now, or null for its style's template. */
    fun nativeLayoutName(key: String): String? =
        nativeLayoutNameOverride?.let { return it(key) } ?: CompanyAdMobInternal.nativeLayoutName(key)

    /** Whether the ad [key] shows a shimmer while it loads (its `showLoading`). */
    fun showsLoading(key: String): Boolean = showsLoadingOverride?.invoke(key) ?: CompanyAdMobInternal.showsLoading(key)

    fun error(message: String) {
        CompanyAdMobInternal.log(LogLevel.ERROR, message)
    }

    fun warn(message: String) {
        CompanyAdMobInternal.log(LogLevel.WARN, message)
    }

    fun debug(message: String) {
        CompanyAdMobInternal.log(LogLevel.DEBUG, message)
    }
}
