package io.github.thesobiaakbar.adroit.billing.internal.paywall

import android.content.Context
import androidx.core.content.edit

/** What the show rules need to remember across processes: the launch count, and per paywall the day's showings and the last one. */
internal class ShowCounterStore(context: Context) {
    private val prefs = context.getSharedPreferences("adroit_billing_paywalls", Context.MODE_PRIVATE)

    val launches: Int
        get() = prefs.getInt(LAUNCHES, 0)

    fun countLaunch() = prefs.edit { putInt(LAUNCHES, launches + 1) }

    /** Showings of [paywall] on [day] (a local-date key). */
    fun shownOn(paywall: String, day: String): Int =
        if (prefs.getString("$paywall.day", null) == day) prefs.getInt("$paywall.dayCount", 0) else 0

    /** Wall-clock time of [paywall]'s last showing, in any process. */
    fun lastShownAt(paywall: String): Long? = prefs.getLong("$paywall.lastShown", -1).takeIf { it >= 0 }

    fun recordShown(paywall: String, day: String, atMillis: Long) = prefs.edit {
        putInt("$paywall.dayCount", shownOn(paywall, day) + 1)
        putString("$paywall.day", day)
        putLong("$paywall.lastShown", atMillis)
    }

    private companion object {
        const val LAUNCHES = "launches"
    }
}
