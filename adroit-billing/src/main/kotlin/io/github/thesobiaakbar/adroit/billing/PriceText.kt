package io.github.thesobiaakbar.adroit.billing

import android.content.res.Resources
import io.github.thesobiaakbar.adroit.billing.internal.pricing.savingsPercent
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Text for prices and periods, built only from Google Play's data, for custom paywalls and paywall texts.
 *
 * Durations and periods come from the library's plural resources (`adroit_billing_duration_*`,
 * `adroit_billing_every_*`), which an app can translate or override. Prices are Play's own strings.
 */
public object PriceText {
    private val PLACEHOLDER = Regex("""\{(\w+)\}""")

    /** A duration with its number, for a trial or an intro price: "7 days", "1 week", "1 year 2 months". */
    @JvmStatic
    public fun duration(resources: Resources, period: BillingPeriod): String =
        parts(period).map { (unit, count) -> resources.getQuantityString(unit.duration, count, count) }
            .reduce { first, second -> resources.getString(R.string.adroit_billing_period_join, first, second) }

    /** A billing period after a price, as in "€4.99 / month": "month", "3 months"; a combined period as [duration]. */
    @JvmStatic
    public fun every(resources: Resources, period: BillingPeriod): String {
        val parts = parts(period)
        if (parts.size > 1) return duration(resources, period)
        val (unit, count) = parts.single()
        return resources.getQuantityString(unit.every, count, count)
    }

    /**
     * [template] with its placeholders filled from [offer], or null when one can't be filled from Play's data (so the
     * caller shows its default text instead of a false or broken one):
     *
     * - `{trial}`: the free trial, "7 days". Null without a free trial.
     * - `{price}` and `{period}`: the first payment, "€0.99" and "month" (an intro price, else the regular price).
     * - `{renewalPrice}` and `{renewalPeriod}`: what it renews at. Null for a prepaid plan.
     * - `{perWeek}` and `{perMonth}`: the renewal price spread over a week or month, rounded up so it never looks
     *   cheaper than it is. Null for a prepaid plan.
     * - `{savings}`: how much cheaper per day the renewal price is than [savingsVs]'s, "33%", rounded down. Null when
     *   [savingsVs] is null or there is no honest saving.
     *
     * Any other `{name}` makes the result null too, so a typo in remote text never reaches users.
     */
    @JvmStatic
    @JvmOverloads
    public fun fill(resources: Resources, template: String, offer: OfferInfo, savingsVs: OfferInfo? = null): String? {
        val locale = resources.locale()
        var missing = false
        val text = PLACEHOLDER.replace(template) { match ->
            val value = when (match.groupValues[1]) {
                "trial" -> offer.freeTrial?.let { duration(resources, it) }
                "price" -> offer.firstPayment?.price?.formatted
                "period" -> offer.firstPayment?.period?.let { every(resources, it) }
                "renewalPrice" -> offer.basePhase?.price?.formatted
                "renewalPeriod" -> offer.basePhase?.period?.let { every(resources, it) }
                "perWeek" -> offer.basePhase?.let { spread(it, BillingPeriod(weeks = 1).approxDays, locale) }
                "perMonth" -> offer.basePhase?.let { spread(it, BillingPeriod(months = 1).approxDays, locale) }
                "savings" -> savings(offer, savingsVs, locale)
                else -> null
            }
            value ?: run {
                missing = true
                ""
            }
        }
        return text.takeUnless { missing }
    }

    /**
     * The renewal terms shown under a paywall's purchase button, from Play's data only:
     *
     * - free trial: "7 days free, then €4.99/month. Cancel anytime in Google Play.";
     * - intro price: "€0.99 for 3 months, then €4.99/month. Cancel anytime in Google Play.";
     * - regular subscription: "€4.99/month, renews automatically. Cancel anytime in Google Play.";
     * - prepaid plan: "€9.99 for 1 month. Doesn't renew automatically.";
     * - one-time product: "One-time payment of €19.99.".
     *
     * Null when Play's data can't fill it (an unreadable period). Texts are the `adroit_billing_disclosure_*` resources.
     */
    @JvmStatic
    public fun disclosure(resources: Resources, plan: PaywallPlan): String? {
        plan.option?.let { return resources.getString(R.string.adroit_billing_disclosure_one_time, it.price.formatted) }
        val offer = plan.offer ?: return null
        val base = offer.basePhase
        val basePeriod = base?.period?.let { every(resources, it) }
        val first = offer.firstPayment
        return when {
            offer.isPrepaid -> {
                val length = first?.totalPeriod ?: return null
                resources.getString(R.string.adroit_billing_disclosure_prepaid, first.price.formatted, duration(resources, length))
            }
            base == null || basePeriod == null -> null
            offer.freeTrial != null -> resources.getString(
                R.string.adroit_billing_disclosure_trial,
                duration(resources, offer.freeTrial!!),
                base.price.formatted,
                basePeriod,
            )
            offer.introPhases.isNotEmpty() -> {
                val intro = offer.introPhases.first()
                val length = intro.totalPeriod ?: return null
                resources.getString(
                    R.string.adroit_billing_disclosure_intro,
                    intro.price.formatted,
                    duration(resources, length),
                    base.price.formatted,
                    basePeriod,
                )
            }
            else -> resources.getString(R.string.adroit_billing_disclosure_renewing, base.price.formatted, basePeriod)
        }
    }

    /**
     * A plan's price as shown on its card: "€4.99 / month" for a subscription (what it renews at), "€9.99 / 1 month"
     * for a prepaid plan, Play's price for a one-time product. Null when Play's data can't fill it.
     */
    @JvmStatic
    public fun planPrice(resources: Resources, plan: PaywallPlan): String? {
        plan.option?.let { return it.price.formatted }
        val offer = plan.offer ?: return null
        val phase = offer.basePhase ?: offer.firstPayment ?: return null
        val period = (if (offer.isPrepaid) phase.totalPeriod?.let { duration(resources, it) } else null)
            ?: phase.period?.let { every(resources, it) }
            ?: return null
        return resources.getString(R.string.adroit_billing_paywall_price_per, phase.price.formatted, period)
    }

    /** The purchase button's text: the remote `texts.cta` when set, else "Start free trial" or "Continue". */
    @JvmStatic
    public fun cta(resources: Resources, plan: PaywallPlan): String = plan.texts.cta
        ?: resources.getString(
            if (plan.offer?.freeTrial != null) R.string.adroit_billing_paywall_cta_trial else R.string.adroit_billing_paywall_cta,
        )

    private fun savings(offer: OfferInfo, versus: OfferInfo?, locale: Locale): String? {
        val phase = offer.basePhase ?: return null
        val reference = versus?.basePhase ?: return null
        val percent = savingsPercent(phase, reference) ?: return null
        return NumberFormat.getPercentInstance(locale).format(percent / 100.0)
    }

    /** [phase]'s price over [days] days, in its currency, rounded up to the currency's smallest unit. */
    private fun spread(phase: PricingPhase, days: Double, locale: Locale): String? {
        val periodDays = phase.period?.approxDays ?: return null
        val currency = runCatching { Currency.getInstance(phase.price.currencyCode) }.getOrNull() ?: return null
        val digits = currency.defaultFractionDigits.coerceAtLeast(0)
        val amount = BigDecimal(phase.price.micros).movePointLeft(6)
            .multiply(BigDecimal(days / periodDays))
            .setScale(digits, RoundingMode.UP)
        return NumberFormat.getCurrencyInstance(locale).apply {
            this.currency = currency
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }.format(amount)
    }

    private enum class Unit(val duration: Int, val every: Int) {
        YEAR(R.plurals.adroit_billing_duration_years, R.plurals.adroit_billing_every_years),
        MONTH(R.plurals.adroit_billing_duration_months, R.plurals.adroit_billing_every_months),
        WEEK(R.plurals.adroit_billing_duration_weeks, R.plurals.adroit_billing_every_weeks),
        DAY(R.plurals.adroit_billing_duration_days, R.plurals.adroit_billing_every_days),
    }

    private fun parts(period: BillingPeriod): List<Pair<Unit, Int>> = listOf(
        Unit.YEAR to period.years,
        Unit.MONTH to period.months,
        Unit.WEEK to period.weeks,
        Unit.DAY to period.days,
    ).filter { it.second > 0 }

    private fun Resources.locale(): Locale = configuration.locales[0] ?: Locale.getDefault()
}
