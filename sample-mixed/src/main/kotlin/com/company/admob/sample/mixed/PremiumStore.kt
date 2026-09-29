package com.company.admob.sample.mixed

import android.content.Context

/**
 * The user's premium (ad-free) status, kept across launches: a stand-in for a real purchase check. The splash reads
 * it to skip consent and ads; the Settings switch writes it.
 */
object PremiumStore {
    private const val PREFS = "sample_premium"
    private const val KEY = "premium"

    fun isPremium(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setPremium(context: Context, premium: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, premium).apply()
    }
}
