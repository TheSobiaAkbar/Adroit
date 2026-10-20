package io.github.thesobiaakbar.adroit.docs.billing

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import io.github.thesobiaakbar.adroit.Adroit
import io.github.thesobiaakbar.adroit.billing.AdroitBilling
import io.github.thesobiaakbar.adroit.billing.BillingSettings
import io.github.thesobiaakbar.adroit.billing.DemoBilling
import io.github.thesobiaakbar.adroit.billing.firebase.FirebaseBillingConfig
import io.github.thesobiaakbar.adroit.billing.firebase.FirebaseBillingEvents
import io.github.thesobiaakbar.adroit.docs.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Steps 3, 4 and 8 of the billing guides.

// region billing-app
class MyApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        AdroitBilling.initialize(
            this,
            BillingSettings(
                products = AppProducts.entries,
                paywalls = AppPaywalls.entries,
                config = FirebaseBillingConfig.source(), // the paywalls' Firebase JSON
                events = listOf(FirebaseBillingEvents(this)), // purchases and paywalls in Analytics
                demo = DemoBilling(BuildConfig.DEBUG), // made-up products until Play Console has yours
            ),
        )
        // Premium means no ads: the one line between billing and ads.
        appScope.launch { AdroitBilling.entitlements.collect { Adroit.setAdFree(it.adFree) } }
        Coins.start(this, appScope)
    }
}
// endregion

// region billing-consumables
/** The coins bought with AppProducts.Coins100. A real app keeps the balance on its server. */
object Coins {
    private val mutableBalance = MutableStateFlow(0)
    val balance: StateFlow<Int> = mutableBalance.asStateFlow()

    /** Call once, from Application.onCreate, so a purchase is credited whichever screen is open. */
    fun start(context: Context, scope: CoroutineScope) {
        val prefs = context.getSharedPreferences("coins", Context.MODE_PRIVATE)
        mutableBalance.value = prefs.getInt("balance", 0)
        scope.launch {
            AdroitBilling.consumables.collect { grant ->
                val credited = prefs.getStringSet("credited", emptySet()).orEmpty()
                if (grant.orderIdHash !in credited) { // each order once, even when it is delivered again
                    mutableBalance.value += 100 * grant.quantity
                    prefs.edit(commit = true) {
                        putInt("balance", mutableBalance.value)
                        putStringSet("credited", credited + grant.orderIdHash)
                    }
                }
                grant.confirm() // now Play may sell it again
            }
        }
    }
}
// endregion
