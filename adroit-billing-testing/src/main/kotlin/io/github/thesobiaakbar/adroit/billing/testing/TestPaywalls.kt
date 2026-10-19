package io.github.thesobiaakbar.adroit.billing.testing

import io.github.thesobiaakbar.adroit.billing.OfferInfo
import io.github.thesobiaakbar.adroit.billing.OfferPolicy
import io.github.thesobiaakbar.adroit.billing.PaywallDefinition
import io.github.thesobiaakbar.adroit.billing.PaywallModel
import io.github.thesobiaakbar.adroit.billing.PaywallPlan
import io.github.thesobiaakbar.adroit.billing.PaywallSpec
import io.github.thesobiaakbar.adroit.billing.PlanSpec
import io.github.thesobiaakbar.adroit.billing.ProductInfo

/**
 * Builds [PaywallModel]s for tests of custom paywall UIs, without Firebase or Play.
 *
 * ```
 * val model = TestPaywalls.model(AppPaywalls.Onboarding, listOf(premium))
 * val controller = PaywallController.forModel(AppPaywalls.Onboarding, model, "onboarding", scope, FakeBilling())
 * ```
 */
public object TestPaywalls {
    /**
     * The model of [spec] (the definition's defaults unless given) over [products], with plan [selected] chosen.
     * Plans whose product isn't in [products] are left out, as the library leaves out products Play didn't return.
     *
     * Offers are chosen by a simplified rule: [PlanSpec.offer]'s `ids`, then its `tags`, then (unless `BASE_ONLY`)
     * the first offer with a free trial, then the base plan. `trialDays`, the other policies, eligibility and savings
     * aren't applied; test those through `AdroitBilling`.
     */
    public fun model(
        definition: PaywallDefinition,
        products: List<ProductInfo>,
        selected: Int = 0,
        spec: PaywallSpec = definition.defaults,
    ): PaywallModel {
        val plans = spec.plans.mapNotNull { plan ->
            products.firstOrNull { it.definition == plan.product }?.let { plan(plan, it, spec) }
        }
        return PaywallModel(definition.name, spec, plans, selected.coerceIn(0, (plans.size - 1).coerceAtLeast(0)))
    }

    private fun plan(spec: PlanSpec, product: ProductInfo, paywall: PaywallSpec): PaywallPlan {
        val base = product.plans.firstOrNull { spec.basePlanId == null || it.basePlanId == spec.basePlanId }
        val offer = base?.let { offer(spec, it.offers) ?: it.baseOffer }
        val option = product.oneTimeOptions.firstOrNull()
        return PaywallPlan(spec, product, base?.basePlanId, offer, option, offerUnavailable = false, savingsPercent = null, texts = paywall.texts)
    }

    private fun offer(spec: PlanSpec, offers: List<OfferInfo>): OfferInfo? {
        val rule = spec.offer
        val withId = offers.filter { it.offerId != null }
        return rule.ids.firstNotNullOfOrNull { id -> withId.firstOrNull { it.offerId == id } }
            ?: withId.firstOrNull { rule.tags.isNotEmpty() && it.offerTags.containsAll(rule.tags) }
            ?: withId.firstOrNull { rule.policy != OfferPolicy.BASE_ONLY && it.freeTrial != null }
    }
}
