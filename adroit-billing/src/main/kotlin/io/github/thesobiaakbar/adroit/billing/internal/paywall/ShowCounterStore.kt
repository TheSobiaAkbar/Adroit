package io.github.thesobiaakbar.adroit.billing.internal.paywall

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit

/**
 * What the show rules need to remember across processes: the launch count, and per paywall the day's showings and the
 * last one. The file is read on first use, which [warmUp] moves off the main thread. With [countsLaunch], that first
 * use also counts this launch, so no rule ever sees the count before it.
 */
internal class ShowCounterStore(context: Context, countsLaunch: Boolean = false) {
    private val prefs by lazy {
        context.getSharedPreferences("adroit_billing_paywalls", Context.MODE_PRIVATE)
            .also { if (countsLaunch) it.edit { putInt(LAUNCHES, it.getInt(LAUNCHES, 0) + 1) } }
    }

    /** Reads the file now, on the calling thread. */
    fun warmUp() {
        prefs
    }

    val launches: Int
        get() = prefs.getInt(LAUNCHES, 0)

    @VisibleForTesting
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
