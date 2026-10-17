package io.github.thesobiaakbar.adroit.billing.internal.offers

import io.github.thesobiaakbar.adroit.billing.BillingPeriod
import io.github.thesobiaakbar.adroit.billing.OfferContext
import io.github.thesobiaakbar.adroit.billing.OfferInfo
import io.github.thesobiaakbar.adroit.billing.OfferPolicy
import io.github.thesobiaakbar.adroit.billing.OfferRule
import io.github.thesobiaakbar.adroit.billing.OfferSelector
import io.github.thesobiaakbar.adroit.billing.SubscriptionPlan

/**
 * The [OfferRule] logic (06 §5.2). With `ids`, the first listed ID Play gave wins. Otherwise `tags` (all must match)
 * and `trialDays` (a free first phase of exactly that many days) narrow the offers, and nothing left is a miss: a
 * requested trial that Play doesn't give is never replaced by another one. The `policy` then picks among what is left;
 * with no `tags` or `trialDays` it picks among every offer, the base plan included. Ties go to Play's order.
 */
internal object DefaultOfferSelector : OfferSelector {
    override fun select(plan: SubscriptionPlan, rule: OfferRule, context: OfferContext): OfferInfo? {
        val offers = plan.offers
        if (rule.ids.isNotEmpty()) return rule.ids.firstNotNullOfOrNull { id -> offers.firstOrNull { it.offerId == id } }
        val filtered = rule.tags.isNotEmpty() || rule.trialDays != null
        val candidates = if (!filtered) offers else offers.filter { offer ->
            offer.offerId != null && offer.offerTags.containsAll(rule.tags) &&
                (rule.trialDays == null || offer.freeTrial?.let { days(it) } == rule.trialDays)
        }
        if (candidates.isEmpty()) return null
        val base = candidates.firstOrNull { it.offerId == null }
        return when (rule.policy) {
            OfferPolicy.PREFER_FREE_TRIAL -> candidates.firstOrNull { it.freeTrial != null } ?: base ?: candidates.first()
            OfferPolicy.PREFER_INTRO_PRICE -> candidates.firstOrNull { it.introPhases.isNotEmpty() } ?: base ?: candidates.first()
            OfferPolicy.LOWEST_FIRST_PAYMENT -> candidates.minByOrNull { it.phases.firstOrNull()?.price?.micros ?: Long.MAX_VALUE }
            OfferPolicy.BASE_ONLY -> if (filtered) candidates.first() else base
        }
    }

    /** The exact length of a period in days, or null when it has months or years (which have no fixed length). */
    private fun days(period: BillingPeriod): Int? =
        if (period.years == 0 && period.months == 0) period.weeks * 7 + period.days else null
}
