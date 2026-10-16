package io.github.thesobiaakbar.adroit.billing.internal

import android.util.Log
import io.github.thesobiaakbar.adroit.billing.BillingLogLevel
import java.security.MessageDigest

/**
 * The billing library's only path to logcat (tag `AdroitBilling`). Never pass it a purchase token or an account ID.
 * [sink] is replaceable for tests.
 */
internal class BillingLog(
    @Volatile var minLevel: BillingLogLevel,
    private val sink: (priority: Int, message: String) -> Unit = { priority, message -> Log.println(priority, TAG, message) },
) {
    fun log(level: BillingLogLevel, message: String) {
        if (level == BillingLogLevel.NONE || level < minLevel) return
        sink(level.priority(), message)
    }

    private fun BillingLogLevel.priority(): Int = when (this) {
        BillingLogLevel.VERBOSE -> Log.VERBOSE
        BillingLogLevel.DEBUG -> Log.DEBUG
        BillingLogLevel.INFO -> Log.INFO
        BillingLogLevel.WARN -> Log.WARN
        BillingLogLevel.ERROR, BillingLogLevel.NONE -> Log.ERROR
    }

    companion object {
        const val TAG: String = "AdroitBilling"
    }
}

/** A purchase token's first 12 hex digits of SHA-256, to tell purchases apart in logs without the token. */
internal fun tokenHash(token: String): String =
    MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }.take(12)
