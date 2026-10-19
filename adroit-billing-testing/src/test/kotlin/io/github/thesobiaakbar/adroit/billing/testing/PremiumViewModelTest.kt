package io.github.thesobiaakbar.adroit.billing.testing

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.thesobiaakbar.adroit.billing.Attribution
import io.github.thesobiaakbar.adroit.billing.BillingClientApi
import io.github.thesobiaakbar.adroit.billing.GrantState
import io.github.thesobiaakbar.adroit.billing.ProductDefinition
import io.github.thesobiaakbar.adroit.billing.ProductSpec
import io.github.thesobiaakbar.adroit.billing.PurchaseRequest
import io.github.thesobiaakbar.adroit.billing.PurchaseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The app's products, declared the way an app does. */
enum class AppProducts(override val defaults: ProductSpec) : ProductDefinition {
    Premium(ProductSpec.subscription("premium_sub", setOf("premium"))),
}

/** App code under test: a screen that gates a feature on "premium" and sells it from the settings placement. */
class PremiumViewModel(private val billing: BillingClientApi) : ViewModel() {
    val isPremium: StateFlow<Boolean> =
        billing.entitlements.map { it.has("premium") }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val buying = MutableStateFlow(false)

    fun buy(activity: Activity): Job = viewModelScope.launch {
        buying.value = true
        billing.purchase(activity, PurchaseRequest(AppProducts.Premium, placement = "settings"))
        buying.value = false
    }
}

/** A consumer-style test of the premium flow, on the plain JVM: no Robolectric, no Play. */
@OptIn(ExperimentalCoroutinesApi::class)
class PremiumViewModelTest {
    private val billing = FakeBilling()

    @Before
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun `buying from settings unlocks premium`() = runTest {
        val vm = PremiumViewModel(billing)
        assertFalse(vm.isPremium.value)

        vm.buy(Activity())

        assertTrue(vm.isPremium.value)
        assertEquals("settings", billing.purchaseRequests.single().placement)
    }

    @Test
    fun `a pending payment unlocks nothing`() = runTest {
        billing.nextPurchaseResult = PurchaseResult.Pending(Attribution.unknown("premium_sub"))
        val vm = PremiumViewModel(billing)

        vm.buy(Activity())

        assertFalse(vm.isPremium.value)
        assertEquals(GrantState.PENDING, billing.entitlements.value.grants.single().state)
    }

    @Test
    fun `a cancelled purchase unlocks nothing, and the next one succeeds`() = runTest {
        billing.nextPurchaseResult = PurchaseResult.Cancelled(Attribution.unknown("premium_sub"))
        val vm = PremiumViewModel(billing)

        vm.buy(Activity())
        assertFalse(vm.isPremium.value)
        vm.buy(Activity())

        assertTrue(vm.isPremium.value)
    }

    @Test
    fun `a held purchase shows the buying state until it completes`() = runTest {
        val result = billing.holdNextPurchase()
        val vm = PremiumViewModel(billing)

        vm.buy(Activity())
        assertTrue(vm.buying.value)
        result.complete(PurchaseResult.AlreadyOwned(Attribution.unknown("premium_sub")))

        assertFalse(vm.buying.value)
        assertTrue(vm.isPremium.value)
    }

    @Test
    fun `own sets what the user owns`() {
        val vm = PremiumViewModel(billing)

        billing.own(AppProducts.Premium)

        assertTrue(vm.isPremium.value)
    }
}
