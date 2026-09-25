package com.company.admob.fakes

import com.company.admob.internal.port.KeyValueStore

/** A [KeyValueStore] backed by a map, so tests can inspect [values] directly. */
internal class InMemoryKeyValueStore : KeyValueStore {
    val values = mutableMapOf<String, Int>()

    override fun getInt(key: String, default: Int): Int = values[key] ?: default

    override fun putInt(key: String, value: Int) {
        values[key] = value
    }
}
