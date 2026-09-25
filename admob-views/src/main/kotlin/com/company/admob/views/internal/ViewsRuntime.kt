package com.company.admob.views.internal

import android.util.Log
import com.company.admob.AdsClient
import com.company.admob.CompanyAdMob

/**
 * Where the views helpers get their [AdsClient]: `CompanyAdMob` itself, or a test double set by this module's tests.
 * Also their log, which goes to Logcat under the library's tag: this module can't reach core's configured logger.
 */
internal object ViewsRuntime {
    const val TAG: String = "CompanyAdMob"

    @Volatile
    var clientOverride: AdsClient? = null

    val client: AdsClient get() = clientOverride ?: CompanyAdMob.client

    fun error(message: String) {
        Log.e(TAG, message)
    }

    fun warn(message: String) {
        Log.w(TAG, message)
    }

    fun debug(message: String) {
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, message)
    }
}
