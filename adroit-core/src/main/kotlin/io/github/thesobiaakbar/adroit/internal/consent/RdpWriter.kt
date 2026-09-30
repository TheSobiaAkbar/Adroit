package io.github.thesobiaakbar.adroit.internal.consent

import android.annotation.SuppressLint
import android.content.Context
import io.github.thesobiaakbar.adroit.internal.logging.AdLog

/**
 * Writes Google's restricted data processing signal (`gad_rdp`) for US state privacy laws. The SDK reads it from the
 * app's default shared preferences, so it must be written **before** SDK initialization. `gad_rdp` is an SDK input,
 * not a consent cache.
 *
 * The file name is `packageName + "_preferences"`, the name `PreferenceManager.getDefaultSharedPreferences` uses in
 * both the framework (`getDefaultSharedPreferencesName`) and `androidx.preference`. It's built by hand so the
 * library doesn't depend on `androidx.preference`; `RdpWriterTest` checks it against the framework's
 * `PreferenceManager`. See https://developers.google.com/admob/android/next-gen/privacy/us-states.
 */
internal class RdpWriter(
    private val appContext: Context,
    private val log: AdLog,
) {
    // Platform API on purpose: core-ktx only reaches this module transitively. commit, not apply: the value must be
    // on disk before the SDK initializes and reads it.
    @SuppressLint("UseKtx", "ApplySharedPref")
    fun apply(restrictedDataProcessing: Boolean) {
        try {
            val preferences = appContext.getSharedPreferences(defaultPreferencesName(appContext), Context.MODE_PRIVATE)
            val editor = preferences.edit()
            if (restrictedDataProcessing) editor.putInt(KEY, 1) else editor.remove(KEY)
            editor.commit()
            log.d { "Restricted data processing ${if (restrictedDataProcessing) "enabled" else "disabled"}" }
        } catch (e: Exception) {
            log.e(e) { "Writing $KEY failed" }
        }
    }

    companion object {
        const val KEY: String = "gad_rdp"

        fun defaultPreferencesName(context: Context): String = context.packageName + "_preferences"
    }
}
