package io.github.thesobiaakbar.adroit.sample.compose.billing

import android.content.Context
import androidx.core.content.edit
import io.github.thesobiaakbar.adroit.billing.AdroitBilling
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The coins bought with [SampleProducts.Coins100], kept in preferences. Collected for the whole process, so a
 * purchase is credited even when its screen is gone. Each order is credited once (by `orderIdHash`), then confirmed,
 * which lets Play sell the product again. A real app would keep the balance on its server.
 */
object CoinWallet {
    private const val PREFS = "sample_coins"
    private const val BALANCE = "balance"
    private const val CREDITED = "credited"
    private const val COINS_PER_PACK = 100

    private val mutableBalance = MutableStateFlow(0)

    val balance: StateFlow<Int> = mutableBalance.asStateFlow()

    fun start(context: Context, scope: CoroutineScope) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mutableBalance.value = prefs.getInt(BALANCE, 0)
        scope.launch {
            AdroitBilling.consumables.collect { grant ->
                val credited = prefs.getStringSet(CREDITED, emptySet()).orEmpty()
                if (grant.orderIdHash !in credited) {
                    mutableBalance.value += COINS_PER_PACK * grant.quantity
                    // commit, not apply: the credit is on disk before the purchase is confirmed (consumed).
                    prefs.edit(commit = true) { putInt(BALANCE, mutableBalance.value).putStringSet(CREDITED, credited + grant.orderIdHash) }
                }
                grant.confirm()
            }
        }
    }
}
