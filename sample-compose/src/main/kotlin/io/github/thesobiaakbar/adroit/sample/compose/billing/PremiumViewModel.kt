package io.github.thesobiaakbar.adroit.sample.compose.billing

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.thesobiaakbar.adroit.billing.AdroitBilling
import io.github.thesobiaakbar.adroit.billing.BillingClientApi
import io.github.thesobiaakbar.adroit.billing.EntitlementSnapshot
import io.github.thesobiaakbar.adroit.billing.RestoreResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Settings' premium rows. It takes [BillingClientApi], not `AdroitBilling` directly, so a test injects `FakeBilling`
 * (PremiumViewModelTest) and an app with DI binds it once. It keeps no Activity: [manage] takes one per call.
 */
class PremiumViewModel(private val billing: BillingClientApi = AdroitBilling) : ViewModel() {
    private val results = Channel<RestoreResult>(Channel.BUFFERED)

    /** What the user owns, and how sure that is (Fresh, Cached, Unknown). */
    val entitlements: StateFlow<EntitlementSnapshot> = billing.entitlements

    /** One result per [restore], for a message; delivered once, even across a rotation. */
    val restoreResults: Flow<RestoreResult> = results.receiveAsFlow()

    fun restore() {
        viewModelScope.launch { results.send(billing.restore()) }
    }

    fun manage(activity: Activity): Boolean = billing.openManageSubscriptions(activity, SampleProducts.PremiumSub)
}
