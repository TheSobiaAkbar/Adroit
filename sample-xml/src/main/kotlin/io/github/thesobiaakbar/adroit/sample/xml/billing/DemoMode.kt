package io.github.thesobiaakbar.adroit.sample.xml.billing

import android.content.Context
import io.github.thesobiaakbar.adroit.sample.xml.BuildConfig

/**
 * Settings' "Demo billing" switch (debug builds): made-up products and purchases instead of Google Play, until the
 * products exist in Play Console. On by default in debug builds; read once per launch, so a change applies on the
 * next one. Release builds never use it, and the library ignores it there anyway.
 */
object DemoMode {
    private const val PREFS = "sample_billing"
    private const val KEY = "demo"

    fun isOn(context: Context): Boolean =
        BuildConfig.DEBUG && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun set(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }
}
