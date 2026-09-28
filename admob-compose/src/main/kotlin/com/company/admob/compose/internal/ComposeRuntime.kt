package com.company.admob.compose.internal

import com.company.admob.AdsClient
import com.company.admob.CompanyAdMob
import com.company.admob.CompanyAdMobInternal
import com.company.admob.InternalCompanyAdMobApi
import com.company.admob.NativeAdHandle
import com.company.admob.logging.LogLevel

/**
 * Where the composables get their [AdsClient]: `CompanyAdMob` itself, or a test double set by this module's tests.
 * Also their log, which goes through the host's configured logger and level (ADR-012).
 */
@OptIn(InternalCompanyAdMobApi::class)
internal object ComposeRuntime {
    @Volatile
    var clientOverride: AdsClient? = null

    val client: AdsClient get() = clientOverride ?: CompanyAdMob.client

    /** For this module's tests: which keys show a shimmer while loading. */
    @Volatile
    var showsLoadingOverride: ((String) -> Boolean)? = null

    /** Whether the ad [key] shows a shimmer while it loads (its `showLoading`, PROMPT 29C). */
    fun showsLoading(key: String): Boolean = showsLoadingOverride?.invoke(key) ?: CompanyAdMobInternal.showsLoading(key)

    /** Drops a rendered native view without destroying the ad (ADR-012). Replaceable by tests. */
    var detachRenderedView: (NativeAdHandle) -> Unit = { CompanyAdMobInternal.detachRenderedView(it) }

    fun error(message: String) {
        CompanyAdMobInternal.log(LogLevel.ERROR, message)
    }

    fun debug(message: String) {
        CompanyAdMobInternal.log(LogLevel.DEBUG, message)
    }
}
