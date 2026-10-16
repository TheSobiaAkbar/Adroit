package io.github.thesobiaakbar.adroit.billing

/**
 * A product as Google Play describes it to this user now: localized texts, prices, plans and the offers this user
 * may get. Nothing here is hardcoded or remotely configured; it all comes from Play.
 *
 * @property definition the app's definition of the product.
 * @property productId the Play product ID.
 * @property type the kind of product.
 * @property title Play's localized title (it may include the app name).
 * @property displayName Play's localized product name.
 * @property description Play's localized description.
 * @property oneTimeOptions the purchase options of a one-time product; empty for a subscription.
 * @property plans the base plans of a subscription that Play returned; empty for a one-time product.
 */
public data class ProductInfo(
    public val definition: ProductDefinition,
    public val productId: String,
    public val type: ProductType,
    public val title: String,
    public val displayName: String,
    public val description: String,
    public val oneTimeOptions: List<OneTimeOption>,
    public val plans: List<SubscriptionPlan>,
)

/**
 * A price as Play formats it for this user.
 *
 * @property formatted Play's localized price, for example `"€4.99"`. Always show this string, never a rebuilt one.
 * @property micros the amount in millionths of the currency unit (4.99 = 4_990_000).
 * @property currencyCode the ISO 4217 currency code, for example `"EUR"`.
 */
public data class Price(
    public val formatted: String,
    public val micros: Long,
    public val currencyCode: String,
)

/**
 * One way to buy a one-time product (Play's purchase option, optionally with an offer).
 *
 * @property purchaseOptionId Play's purchase option ID, or null for a product without purchase options.
 * @property offerId Play's offer ID, or null for the option's regular price.
 * @property offerTags the offer's tags in Play Console.
 * @property price what the user pays.
 */
public data class OneTimeOption(
    public val purchaseOptionId: String?,
    public val offerId: String?,
    public val offerTags: List<String>,
    public val price: Price,
)

/**
 * A subscription base plan and the offers Play gives this user on it.
 *
 * @property basePlanId the base plan ID in Play Console, for example `"monthly"`.
 * @property offers every offer Play returned for this base plan, the base plan's own offer ([OfferInfo.offerId] null)
 *   included. Play leaves out offers this user isn't eligible for, except developer-determined ones.
 */
public data class SubscriptionPlan(
    public val basePlanId: String,
    public val offers: List<OfferInfo>,
) {
    /** The base plan's own offer (no offer ID): its regular price, without trial or intro price. */
    public val baseOffer: OfferInfo?
        get() = offers.firstOrNull { it.offerId == null }
}

/**
 * One subscription offer: its pricing phases, in the order the user goes through them. The properties below read
 * those phases; nothing about a duration or price is assumed.
 *
 * @property basePlanId the base plan it belongs to.
 * @property offerId the offer ID, or null for the base plan itself.
 * @property offerTags the offer's tags in Play Console.
 * @property phases the pricing phases, for example a free week, then a monthly price.
 */
public data class OfferInfo(
    public val basePlanId: String,
    public val offerId: String?,
    public val offerTags: List<String>,
    public val phases: List<PricingPhase>,
) {
    /**
     * How long the free trial lasts, or null without one: the first phase, when it costs nothing. Null too when Play
     * sent a period this library can't read, so an unreadable trial is never advertised.
     */
    public val freeTrial: BillingPeriod?
        get() = phases.firstOrNull()?.takeIf { it.price.micros == 0L }?.totalPeriod

    /** The paid introductory phases: paid, limited in time, and not the last phase. */
    public val introPhases: List<PricingPhase>
        get() = phases.dropLast(1).filter { it.price.micros > 0 && it.recurrence != Recurrence.INFINITE }

    /** The price the subscription renews at: the last phase that repeats until cancelled. Null for a prepaid plan. */
    public val basePhase: PricingPhase?
        get() = phases.lastOrNull()?.takeIf { it.recurrence == Recurrence.INFINITE }

    /** Whether this is a prepaid plan, which doesn't renew by itself (no phase repeats until cancelled). */
    public val isPrepaid: Boolean
        get() = phases.isNotEmpty() && phases.none { it.recurrence == Recurrence.INFINITE }

    /** The first phase the user pays for: an intro price, the base price, or a prepaid period. */
    public val firstPayment: PricingPhase?
        get() = phases.firstOrNull { it.price.micros > 0 }
}

/** How often a pricing phase repeats. */
public enum class Recurrence {
    /** It repeats until the user cancels: a regular subscription price. */
    INFINITE,

    /** It repeats [PricingPhase.billingCycleCount] times, for example an intro price for 3 months. */
    FINITE,

    /** It happens once, for example a free trial or a prepaid plan. */
    NON_RECURRING,
}

/**
 * One pricing phase of a subscription offer.
 *
 * @property price the price of one billing period (0 micros for a free trial).
 * @property billingPeriod Play's ISO-8601 period, for example `"P1W"`, `"P1M"`, `"P3M"` or `"P1Y"`.
 * @property period [billingPeriod] parsed, or null if Play sent a period this library can't read.
 * @property billingCycleCount how many periods the phase lasts; 0 for an [Recurrence.INFINITE] phase.
 * @property recurrence how the phase repeats.
 */
public data class PricingPhase(
    public val price: Price,
    public val billingPeriod: String,
    public val period: BillingPeriod?,
    public val billingCycleCount: Int,
    public val recurrence: Recurrence,
) {
    /** The whole phase: [period] times [billingCycleCount] (at least once). Null when [period] is unreadable. */
    public val totalPeriod: BillingPeriod?
        get() = period?.times(billingCycleCount.coerceAtLeast(1))
}

/**
 * A calendar period, as Play's ISO-8601 billing periods describe it (`P1W` is 1 week, `P3M` 3 months). Any
 * combination is possible; nothing assumes a fixed list of durations.
 */
public data class BillingPeriod(
    public val years: Int = 0,
    public val months: Int = 0,
    public val weeks: Int = 0,
    public val days: Int = 0,
) {
    init {
        require(years >= 0 && months >= 0 && weeks >= 0 && days >= 0) { "A billing period can't be negative: $this" }
        require(years + months + weeks + days > 0) { "A billing period can't be empty" }
    }

    /**
     * The period's average length in days, for comparisons only (a year is 365.2425 days, a month a twelfth of it),
     * never shown as a duration. A year is exactly 12 months here, so yearly and monthly prices compare exactly.
     */
    public val approxDays: Double
        get() = years * DAYS_PER_YEAR + months * DAYS_PER_YEAR / 12 + weeks * 7.0 + days

    internal fun times(count: Int): BillingPeriod = BillingPeriod(years * count, months * count, weeks * count, days * count)

    /** Parsing. */
    public companion object {
        private const val DAYS_PER_YEAR = 365.2425
        private val ISO = Regex("""P(?:(\d{1,4})Y)?(?:(\d{1,4})M)?(?:(\d{1,4})W)?(?:(\d{1,4})D)?""")

        /**
         * Parses Play's ISO-8601 billing period (`P1W`, `P1M`, `P3M`, `P1Y`, `P7D`, or a combination such as `P1Y2M`),
         * or returns null for anything else, including time parts (`PT…`) and empty periods. Never throws.
         */
        @JvmStatic
        public fun parse(iso: String): BillingPeriod? {
            val match = ISO.matchEntire(iso.trim()) ?: return null
            val (y, m, w, d) = match.destructured
            val parts = listOf(y, m, w, d).map { it.toIntOrNull() ?: 0 }
            if (parts.sum() == 0) return null
            return BillingPeriod(parts[0], parts[1], parts[2], parts[3])
        }
    }
}
