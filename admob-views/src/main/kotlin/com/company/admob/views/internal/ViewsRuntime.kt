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
