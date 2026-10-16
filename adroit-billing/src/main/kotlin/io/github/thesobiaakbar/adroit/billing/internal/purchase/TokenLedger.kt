package io.github.thesobiaakbar.adroit.billing.internal.purchase

import android.annotation.SuppressLint
import android.content.Context
import io.github.thesobiaakbar.adroit.billing.internal.sha256Hex

/** How far the library got with one purchase token. */
internal enum class TokenState {
    /** Granted (a consumable: delivered to the app); not yet acknowledged or consumed. */
    GRANTED,

    ACKNOWLEDGED,

    /** A consumable the app confirmed; consuming it may still be outstanding. */
    CONFIRMED,

    CONSUMED,

    /** The app's verifier rejected it. */
    REJECTED,
}

/**
 * Processed purchase tokens in SharedPreferences `adroit_billing_ledger`, keyed by SHA-256 of the token, never the
 * token. Keeps the [capacity] most recently used; older ones are dropped.
 */
@SuppressLint("UseKtx")
internal class TokenLedger(context: Context, private val capacity: Int = CAPACITY) {
    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** In access order, so the first key is the least recently used. */
    private val states = LinkedHashMap<String, TokenState>(16, 0.75f, true).apply {
        prefs.getString(KEY, null)?.split(',')?.forEach { entry ->
            val (hash, state) = entry.split(':').takeIf { it.size == 2 } ?: return@forEach
            TokenState.entries.firstOrNull { it.name == state }?.let { put(hash, it) }
        }
    }

    @Synchronized
    operator fun get(token: String): TokenState? = states[sha256Hex(token)]

    /** Writes synchronously, so the state survives the process dying right after. False when the write failed. */
    @Synchronized
    @SuppressLint("ApplySharedPref")
    operator fun set(token: String, state: TokenState): Boolean {
        states[sha256Hex(token)] = state
        while (states.size > capacity) states.remove(states.keys.first())
        return prefs.edit().putString(KEY, states.entries.joinToString(",") { "${it.key}:${it.value.name}" }).commit()
    }

    companion object {
        const val NAME = "adroit_billing_ledger"
        const val CAPACITY = 500
        private const val KEY = "tokens"
    }
}
