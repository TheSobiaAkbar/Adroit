package io.github.thesobiaakbar.adroit.billing.firebase

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import io.github.thesobiaakbar.adroit.billing.BillingEvent
import io.github.thesobiaakbar.adroit.billing.BillingEventListener
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Sends billing events to Google Analytics for Firebase, as [BillingEvent.name] with [BillingEvent.toParams] (already
 * within Firebase's limits). Opt-in: `BillingSettings(events = listOf(FirebaseBillingEvents(context)))`.
 *
 * With [logRevenue], a [BillingEvent.PurchaseCompleted] with a known price also logs Firebase's `purchase` event with
 * `value` (currency units) and `currency`. It is off by default: when the Play app is linked to Firebase, Analytics
 * already imports in-app purchase revenue, and turning this on would count it twice. Turn it on only if Analytics
 * shows no `in_app_purchase` revenue for the app.
 *
 * A failure in Firebase is logged and never affects billing.
 */
public class FirebaseBillingEvents internal constructor(
    private val sink: (name: String, params: Map<String, Any>) -> Unit,
    private val logRevenue: Boolean,
) : BillingEventListener {
    /** Logs to [analytics]. */
    @JvmOverloads
    public constructor(analytics: FirebaseAnalytics, logRevenue: Boolean = false) :
        this({ name, params -> analytics.logEvent(name, params.toBundle()) }, logRevenue)

    /**
     * Uses `FirebaseAnalytics.getInstance(context)`. The permissions lint asks for come from Firebase's own manifests.
     */
    @SuppressLint("MissingPermission")
    @JvmOverloads
    public constructor(context: Context, logRevenue: Boolean = false) :
        this(FirebaseAnalytics.getInstance(context), logRevenue)

    override fun onEvent(event: BillingEvent) {
        log(event.name, event.toParams())
        if (logRevenue && event is BillingEvent.PurchaseCompleted) {
            val micros = event.priceMicros ?: return
            val currency = event.currency ?: return
            log(
                FirebaseAnalytics.Event.PURCHASE,
                mapOf(FirebaseAnalytics.Param.VALUE to micros / MICROS_PER_UNIT, FirebaseAnalytics.Param.CURRENCY to currency),
            )
        }
    }

    private fun log(name: String, params: Map<String, Any>) {
        try {
            sink(name, params)
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Analytics couldn't log $name: $e")
        }
    }
}

private const val MICROS_PER_UNIT = 1_000_000.0

private fun Map<String, Any>.toBundle(): Bundle = Bundle().also { bundle ->
    forEach { (key, value) ->
        when (value) {
            is Long -> bundle.putLong(key, value)
            is Double -> bundle.putDouble(key, value)
            else -> bundle.putString(key, value.toString())
        }
    }
}
