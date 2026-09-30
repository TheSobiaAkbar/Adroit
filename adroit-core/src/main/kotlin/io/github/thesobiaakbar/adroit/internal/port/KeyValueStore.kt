package io.github.thesobiaakbar.adroit.internal.port

/** Small persistent store. Holds the app launch count only (architecture §9). Implemented in Prompt 06. */
internal interface KeyValueStore {
    fun getInt(key: String, default: Int): Int

    fun putInt(key: String, value: Int)
}
