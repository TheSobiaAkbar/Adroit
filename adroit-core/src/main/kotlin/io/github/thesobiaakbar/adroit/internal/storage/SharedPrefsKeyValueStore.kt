package io.github.thesobiaakbar.adroit.internal.storage

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import io.github.thesobiaakbar.adroit.internal.port.KeyValueStore

/**
 * [KeyValueStore] in the private preferences file `adroit_state`. It holds [KEY_LAUNCH_COUNT] only
 * (architecture §9): consent is never stored here, and neither is the ad-free flag.
 */
internal class SharedPrefsKeyValueStore(context: Context) : KeyValueStore {
    private val preferences: SharedPreferences by lazy {
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    }

    override fun getInt(key: String, default: Int): Int = preferences.getInt(key, default)

    // Platform API on purpose: core-ktx only reaches this module transitively.
    @SuppressLint("UseKtx")
    override fun putInt(key: String, value: Int) {
        preferences.edit().putInt(key, value).apply()
    }

    companion object {
        const val FILE_NAME: String = "adroit_state"
        const val KEY_LAUNCH_COUNT: String = "launch_count"
    }
}
