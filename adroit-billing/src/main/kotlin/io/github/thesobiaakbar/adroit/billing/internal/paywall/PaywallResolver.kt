package io.github.thesobiaakbar.adroit.billing.internal.paywall

import android.content.res.Resources
import io.github.thesobiaakbar.adroit.billing.BillingSettings
import io.github.thesobiaakbar.adroit.billing.CatalogState
import io.github.thesobiaakbar.adroit.billing.OfferContext
import io.github.thesobiaakbar.adroit.billing.OfferFallback
import io.github.thesobiaakbar.adroit.billing.OfferInfo
import io.github.thesobiaakbar.adroit.billing.OfferRule
import io.github.thesobiaakbar.adroit.billing.OneTimeOption
import io.github.thesobiaakbar.adroit.billing.PaywallModel
import io.github.thesobiaakbar.adroit.billing.PaywallModelState
import io.github.thesobiaakbar.adroit.billing.PaywallPlan
import io.github.thesobiaakbar.adroit.billing.PaywallTexts
import io.github.thesobiaakbar.adroit.billing.PlanSpec
import io.github.thesobiaakbar.adroit.billing.PriceText
import io.github.thesobiaakbar.adroit.billing.PricingPhase
import io.github.thesobiaakbar.adroit.billing.ProductInfo
import io.github.thesobiaakbar.adroit.billing.ProductType
import io.github.thesobiaakbar.adroit.billing.Recurrence
import io.github.thesobiaakbar.adroit.billing.SkipReason
import io.github.thesobiaakbar.adroit.billing.SubscriptionPlan
import io.github.thesobiaakbar.adroit.billing.internal.config.ResolvedPaywall
import io.github.thesobiaakbar.adroit.billing.internal.offers.DefaultOfferSelector
import io.github.thesobiaakbar.adroit.billing.internal.pricing.savingsPercent

/**
 * Turns a [ResolvedPaywall] and the catalog into a [PaywallModelState]: each plan gets an offer Play gives this user
 * (06 §5.2 offer selection), or `onOfferUnavailable` applies. Texts are filled from the chosen offer only, so a trial
 * or intro price Play doesn't give is never advertised. Pure apart from calling the app's hooks.
 */
internal class PaywallResolver(private val settings: BillingSettings, private val resources: Resources) {
    /** A plan whose configured offer wasn't available; [fallbackOfferId] is what replaced it (null: none, or the base plan). */
    data class Unavailable(val productId: String, val basePlanId: String?, val requested: String, val fallbackOfferId: String?)

    class Result(val state: PaywallModelState, val unavailable: List<Unavailable>)

    private val selector = settings.offerSelector ?: DefaultOfferSelector

    fun resolve(paywall: ResolvedPaywall, catalog: CatalogState): Result {
        val products = when (catalog) {
            CatalogState.Loading -> return Result(PaywallModelState.Loading, emptyList())
            is CatalogState.Failed -> return Result(PaywallModelState.Failed(catalog.error), emptyList())
            is CatalogState.Ready -> catalog.products
        }
        val spec = paywall.spec
        val unavailable = mutableListOf<Unavailable>()
        val resolved = spec.plans.map { plan ->
            val info = products[plan.product] ?: return@map null
            val (choice, miss) = choose(plan, info, paywall.name, spec.texts)
            if (miss == null) return@map choice
            when (spec.onOfferUnavailable) {
                OfferFallback.BASE_PLAN -> {
                    val fallback = base(plan, info, spec.texts)
                    unavailable += Unavailable(info.productId, miss.basePlanId, miss.requested, fallback?.offerId)
                    fallback
                }
                OfferFallback.HIDE_PLAN, OfferFallback.HIDE_PAYWALL -> {
                    unavailable += Unavailable(info.productId, miss.basePlanId, miss.requested, null)
                    null
                }
            }
        }
        val plans = resolved.filterNotNull()
        val state = when {
            spec.onOfferUnavailable == OfferFallback.HIDE_PAYWALL && unavailable.isNotEmpty() ->
                PaywallModelState.Unavailable(SkipReason.OFFER_UNAVAILABLE)
            plans.isEmpty() -> PaywallModelState.Unavailable(SkipReason.NO_PLANS)
            else -> {
                val selected = resolved.getOrNull(spec.selected)?.let(plans::indexOf) ?: 0
                PaywallModelState.Ready(PaywallModel(paywall.name, spec, plans, selected))
            }
        }
        return Result(state, unavailable)
    }

    /** The offer [rule] picks among those this user may buy, or null; also used for `OfferSelection.Auto`. */
    fun select(plan: SubscriptionPlan, rule: OfferRule, context: OfferContext): OfferInfo? {
        val eligible = plan.copy(offers = plan.offers.filter { eligible(it, context) })
        return selector.select(eligible, rule, context)?.takeIf { it in eligible.offers }
    }

    private class Miss(val basePlanId: String?, val requested: String)

    /** The plan with its configured offer, or (null, the miss). A plan whose base plan Play didn't return is (null, null). */
    private fun choose(plan: PlanSpec, info: ProductInfo, paywallId: String, texts: PaywallTexts): Pair<PaywallPlan?, Miss?> {
        val rule = plan.offer
        if (info.type != ProductType.SUBSCRIPTION) {
            val option = when {
                rule.ids.isNotEmpty() -> rule.ids.firstNotNullOfOrNull { id -> info.oneTimeOptions.firstOrNull { it.offerId == id } }
                rule.tags.isNotEmpty() -> info.oneTimeOptions.firstOrNull { it.offerId != null && it.offerTags.containsAll(rule.tags) }
                else -> regular(info)
            } ?: return null to Miss(null, describe(rule))
            return oneTime(plan, info, option, false, texts) to null
        }
        val sub = subscriptionPlan(plan, info) ?: return null to null
        val offer = select(sub, rule, OfferContext(plan.product, paywallId)) ?: return null to Miss(sub.basePlanId, describe(rule))
        return subscription(plan, info, sub, offer, false, texts) to null
    }

    /** The plan at its regular price, or null when Play has none to sell. */
    private fun base(plan: PlanSpec, info: ProductInfo, texts: PaywallTexts): PaywallPlan? {
        if (info.type != ProductType.SUBSCRIPTION) return regular(info)?.let { oneTime(plan, info, it, true, texts) }
        val sub = subscriptionPlan(plan, info) ?: return null
        return sub.baseOffer?.let { subscription(plan, info, sub, it, true, texts) }
    }

    private fun subscriptionPlan(plan: PlanSpec, info: ProductInfo): SubscriptionPlan? =
        if (plan.basePlanId == null) info.plans.firstOrNull() else info.plans.firstOrNull { it.basePlanId == plan.basePlanId }

    private fun regular(info: ProductInfo): OneTimeOption? =
        info.oneTimeOptions.firstOrNull { it.offerId == null } ?: info.oneTimeOptions.firstOrNull()

    private fun eligible(offer: OfferInfo, context: OfferContext): Boolean =
        offer.offerTags.none { it in settings.developerDeterminedTags } ||
            settings.offerEligibility?.isEligible(offer, context) == true

    private fun subscription(
        plan: PlanSpec,
        info: ProductInfo,
        sub: SubscriptionPlan,
        offer: OfferInfo,
        unavailable: Boolean,
        texts: PaywallTexts,
    ): PaywallPlan {
        val versus = plan.savingsVs?.let { id -> info.plans.firstOrNull { it.basePlanId == id }?.baseOffer }
        val savings = offer.basePhase?.let { phase -> versus?.basePhase?.let { savingsPercent(phase, it) } }
        return PaywallPlan(plan, info, sub.basePlanId, offer, null, unavailable, savings, fill(texts, offer, versus))
    }

    private fun oneTime(plan: PlanSpec, info: ProductInfo, option: OneTimeOption, unavailable: Boolean, texts: PaywallTexts): PaywallPlan {
        // A single unrepeated phase: {price} fills, while {trial}, {period} and the renewal placeholders can't.
        val asOffer = OfferInfo("", option.offerId, option.offerTags, listOf(PricingPhase(option.price, "", null, 1, Recurrence.NON_RECURRING)))
        return PaywallPlan(plan, info, null, null, option, unavailable, null, fill(texts, asOffer, null))
    }

    /** [t] filled from [offer]; a text that can't be filled is null, so the UI shows its default. */
    private fun fill(t: PaywallTexts, offer: OfferInfo, versus: OfferInfo?): PaywallTexts {
        fun fill(template: String?) = template?.let { PriceText.fill(resources, it, offer, versus) }
        val trialCta = if (offer.freeTrial != null) fill(t.ctaTrial) else null
        return PaywallTexts(
            title = fill(t.title),
            subtitle = fill(t.subtitle),
            features = t.features.mapNotNull(::fill),
            cta = trialCta ?: fill(t.cta),
            ctaTrial = null,
            footer = fill(t.footer),
        )
    }

    private fun describe(rule: OfferRule): String = when {
        rule.ids.isNotEmpty() -> "ids:" + rule.ids.joinToString(",")
        else -> listOfNotNull(
            rule.tags.takeIf { it.isNotEmpty() }?.joinToString(",", "tags:"),
            rule.trialDays?.let { "trialDays:$it" },
            "policy:${rule.policy.name.lowercase()}",
        ).joinToString(" ")
    }
}
