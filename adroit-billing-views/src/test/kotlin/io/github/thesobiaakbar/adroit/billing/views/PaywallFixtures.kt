package io.github.thesobiaakbar.adroit.billing.views

import android.app.Activity
import io.github.thesobiaakbar.adroit.billing.BillingClientApi
import io.github.thesobiaakbar.adroit.billing.BillingConnectionState
import io.github.thesobiaakbar.adroit.billing.BillingPeriod
import io.github.thesobiaakbar.adroit.billing.CatalogState
import io.github.thesobiaakbar.adroit.billing.CloseBehavior
import io.github.thesobiaakbar.adroit.billing.ConsumableGrant
import io.github.thesobiaakbar.adroit.billing.EntitlementSnapshot
import io.github.thesobiaakbar.adroit.billing.OfferInfo
import io.github.thesobiaakbar.adroit.billing.PaywallDefinition
import io.github.thesobiaakbar.adroit.billing.PaywallModel
import io.github.thesobiaakbar.adroit.billing.PaywallPlan
import io.github.thesobiaakbar.adroit.billing.PaywallSpec
import io.github.thesobiaakbar.adroit.billing.PaywallTemplate
import io.github.thesobiaakbar.adroit.billing.PaywallTexts
import io.github.thesobiaakbar.adroit.billing.PlanSpec
import io.github.thesobiaakbar.adroit.billing.Price
import io.github.thesobiaakbar.adroit.billing.PricingPhase
import io.github.thesobiaakbar.adroit.billing.ProductDefinition
import io.github.thesobiaakbar.adroit.billing.ProductInfo
import io.github.thesobiaakbar.adroit.billing.ProductSpec
import io.github.thesobiaakbar.adroit.billing.ProductType
import io.github.thesobiaakbar.adroit.billing.PurchaseRequest
import io.github.thesobiaakbar.adroit.billing.PurchaseResult
import io.github.thesobiaakbar.adroit.billing.PurchaseUpdate
import io.github.thesobiaakbar.adroit.billing.Recurrence
import io.github.thesobiaakbar.adroit.billing.RestoreResult
import io.github.thesobiaakbar.adroit.billing.SubscriptionPlan
import io.github.thesobiaakbar.adroit.billing.SuccessAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

enum class TestProduct(override val defaults: ProductSpec) : ProductDefinition {
    Premium(ProductSpec.subscription("premium_sub", setOf("premium"))),
}

class TestPaywall(override val defaults: PaywallSpec, override val name: String = "Onboarding") : PaywallDefinition

/** Play-like data: a monthly plan with a 7-day trial and a yearly plan without one. */
object Fixtures {
    private fun phase(iso: String, period: BillingPeriod, micros: Long, recurrence: Recurrence) = PricingPhase(
        price = Price(if (micros == 0L) "Free" else "$" + "%.2f".format(java.util.Locale.US, micros / 1_000_000.0), micros, "USD"),
        billingPeriod = iso,
        period = period,
        billingCycleCount = if (recurrence == Recurrence.INFINITE) 0 else 1,
        recurrence = recurrence,
    )

    val monthlyTrial = OfferInfo(
        "monthly", "trial", emptyList(),
        listOf(phase("P7D", BillingPeriod(days = 7), 0, Recurrence.FINITE), phase("P1M", BillingPeriod(months = 1), 4_990_000, Recurrence.INFINITE)),
    )
    val yearly = OfferInfo("yearly", null, emptyList(), listOf(phase("P1Y", BillingPeriod(years = 1), 29_990_000, Recurrence.INFINITE)))

    private val product = ProductInfo(
        TestProduct.Premium, "premium_sub", ProductType.SUBSCRIPTION, "Premium (Test)", "Premium", "All features",
        emptyList(), listOf(SubscriptionPlan("monthly", listOf(monthlyTrial)), SubscriptionPlan("yearly", listOf(yearly))),
    )

    private fun plan(offer: OfferInfo, texts: PaywallTexts, badge: String? = null) = PaywallPlan(
        PlanSpec(TestProduct.Premium, basePlanId = offer.basePlanId, badge = badge), product, offer.basePlanId, offer, null,
        offerUnavailable = false, savingsPercent = null, texts = texts,
    )

    fun spec(
        close: CloseBehavior = CloseBehavior(),
        template: PaywallTemplate = PaywallTemplate.PLANS,
        successAction: SuccessAction = SuccessAction.CLOSE,
        texts: PaywallTexts = PaywallTexts(title = "Go premium", features = listOf("No ads", "All filters")),
    ) = PaywallSpec(
        plans = listOf(PlanSpec(TestProduct.Premium, basePlanId = "monthly"), PlanSpec(TestProduct.Premium, basePlanId = "yearly")),
        template = template,
        close = close,
        texts = texts,
        successAction = successAction,
    )

    fun model(spec: PaywallSpec) = PaywallModel(
        "Onboarding", spec, listOf(plan(monthlyTrial, spec.texts), plan(yearly, spec.texts, badge = "BEST_VALUE")), selected = 0,
    )
}

class FakeBilling : BillingClientApi {
    val requests = mutableListOf<PurchaseRequest>()
    var result = CompletableDeferred<PurchaseResult>()
    override val entitlements: StateFlow<EntitlementSnapshot> = MutableStateFlow(EntitlementSnapshot.EMPTY_UNKNOWN)
    override val connection: StateFlow<BillingConnectionState> = MutableStateFlow(BillingConnectionState.Connected)
    override val catalog: StateFlow<CatalogState> = MutableStateFlow(CatalogState.Loading)
    override val purchaseUpdates: SharedFlow<PurchaseUpdate> = MutableSharedFlow()
    override val consumables: Flow<ConsumableGrant> = emptyFlow()
    override suspend fun purchase(activity: Activity, request: PurchaseRequest): PurchaseResult {
        requests += request
        return result.await()
    }
    override suspend fun restore(): RestoreResult = RestoreResult.NothingToRestore
    override suspend fun refresh() {}
    override fun openManageSubscriptions(activity: Activity, product: ProductDefinition?): Boolean = true
    override fun setAccount(accountId: String?) {}
}
