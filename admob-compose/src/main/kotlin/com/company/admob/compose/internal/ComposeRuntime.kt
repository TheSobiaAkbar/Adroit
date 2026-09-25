package com.company.admob.compose.internal

import android.util.Log
import com.company.admob.AdsClient
import com.company.admob.CompanyAdMob
import com.company.admob.CompanyAdMobInternal
import com.company.admob.InternalCompanyAdMobApi
import com.company.admob.NativeAdHandle

/**
 * Where the composables get their [AdsClient]: `CompanyAdMob` itself, or a test double set by this module's tests.
 * Also their log, which goes to Logcat under the library's tag: this module can't reach core's configured logger.
 */
internal object ComposeRuntime {
    const val TAG: String = "CompanyAdMob"

    @Volatile
    var clientOverride: AdsClient? = null

    val client: AdsClient get() = clientOverride ?: CompanyAdMob.client

    /** Drops a rendered native view without destroying the ad (ADR-012). Replaceable by tests. */
    @OptIn(InternalCompanyAdMobApi::class)
    var detachRenderedView: (NativeAdHandle) -> Unit = { CompanyAdMobInternal.detachRenderedView(it) }

    fun error(message: String) {
        Log.e(TAG, message)
    }

    fun debug(message: String) {
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, message)
    }
}
