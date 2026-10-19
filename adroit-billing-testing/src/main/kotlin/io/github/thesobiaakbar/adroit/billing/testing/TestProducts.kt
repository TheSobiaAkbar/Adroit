package io.github.thesobiaakbar.adroit.billing.testing

import io.github.thesobiaakbar.adroit.billing.BillingPeriod
import io.github.thesobiaakbar.adroit.billing.OfferInfo
import io.github.thesobiaakbar.adroit.billing.OneTimeOption
import io.github.thesobiaakbar.adroit.billing.Price
import io.github.thesobiaakbar.adroit.billing.PricingPhase
import io.github.thesobiaakbar.adroit.billing.ProductDefinition
import io.github.thesobiaakbar.adroit.billing.ProductInfo
import io.github.thesobiaakbar.adroit.billing.ProductType
import io.github.thesobiaakbar.adroit.billing.Recurrence
import io.github.thesobiaakbar.adroit.billing.SubscriptionPlan

/**
 * Builders of [ProductInfo], shaped the way Google Play describes products, for host app unit tests. Every price is
 * the one the test passes; nothing is made up.
 *
 * ```
 * val premium = TestProducts.subscription(AppProducts.Premium) {
 *     basePlan("monthly") { trial("P7D"); recurring("P1M", "$4.99", 4_990_000, "USD") }
 *     basePlan("yearly") { recurring("P1Y", "$29.99", 29_990_000, "USD") }
 * }
 * val coins = TestProducts.oneTime(AppProducts.Coins, "$0.99", 990_000, "USD")
 * val billing = FakeBilling(products = listOf(premium, coins))
 * ```
 */
public object TestProducts {
    /** A subscription of [definition] with the base plans declared in [plans]. */
    public fun subscription(definition: ProductDefinition, plans: SubscriptionBuilder.() -> Unit): ProductInfo {
        val built = SubscriptionBuilder().apply(plans).plans
        require(built.isNotEmpty()) { "Declare at least one basePlan" }
        return info(definition, emptyList(), built)
    }

    /** A one-time product of [definition] (consumable or not) priced [formatted] ([micros] of [currencyCode]). */
    public fun oneTime(definition: ProductDefinition, formatted: String, micros: Long, currencyCode: String): ProductInfo =
        info(definition, listOf(OneTimeOption(null, null, emptyList(), Price(formatted, micros, currencyCode))), emptyList())

    private fun info(definition: ProductDefinition, options: List<OneTimeOption>, plans: List<SubscriptionPlan>): ProductInfo {
        val id = definition.defaults.productId
        return ProductInfo(definition, id, definition.defaults.type, "$id (Test)", id, "", options, plans)
    }

    /** Declares the base plans of [subscription]. */
    public class SubscriptionBuilder internal constructor() {
        internal val plans = mutableListOf<SubscriptionPlan>()

        /**
         * A base plan [basePlanId]. Its [PhasesBuilder.recurring] price is required. A [PhasesBuilder.trial] or
         * [PhasesBuilder.intro] declared directly in the block makes an offer with ID `intro`; declare more with
         * [BasePlanBuilder.offer].
         */
        public fun basePlan(basePlanId: String, block: BasePlanBuilder.() -> Unit) {
            plans += BasePlanBuilder(basePlanId).apply(block).build()
        }
    }

    /** Declares pricing phases before the recurring price. */
    public open class PhasesBuilder internal constructor() {
        private class Phase(val period: String, val price: Price?, val cycles: Int)

        private val intro = mutableListOf<Phase>()

        internal val hasIntro: Boolean get() = intro.isNotEmpty()

        /** A free trial of [period] (ISO 8601, for example `P7D`). */
        public fun trial(period: String) {
            intro += Phase(period, null, 1)
        }

        /** An introductory price of [formatted] ([micros] of [currencyCode]) for [cycles] periods of [period]. */
        public fun intro(period: String, formatted: String, micros: Long, currencyCode: String, cycles: Int = 1) {
            intro += Phase(period, Price(formatted, micros, currencyCode), cycles)
        }

        internal fun phases(recurring: PricingPhase): List<PricingPhase> = intro.map {
            PricingPhase(
                it.price ?: Price("Free", 0, recurring.price.currencyCode), it.period, BillingPeriod.parse(it.period),
                it.cycles, Recurrence.FINITE,
            )
        } + recurring
    }

    /** Declares one base plan. */
    public class BasePlanBuilder internal constructor(private val basePlanId: String) : PhasesBuilder() {
        private var recurring: PricingPhase? = null
        private val offers = mutableListOf<(PricingPhase) -> OfferInfo>()

        /** The price the plan renews at: [formatted] ([micros] of [currencyCode]) every [period]. */
        public fun recurring(period: String, formatted: String, micros: Long, currencyCode: String) {
            recurring = PricingPhase(Price(formatted, micros, currencyCode), period, BillingPeriod.parse(period), 0, Recurrence.INFINITE)
        }

        /** An offer [offerId] with Play offer [tags] and the phases in [block], then the recurring price. */
        public fun offer(offerId: String, tags: List<String> = emptyList(), block: PhasesBuilder.() -> Unit) {
            val phases = PhasesBuilder().apply(block)
            offers += { base -> OfferInfo(basePlanId, offerId, tags, phases.phases(base)) }
        }

        internal fun build(): SubscriptionPlan {
            val base = checkNotNull(recurring) { "Base plan '$basePlanId' needs a recurring price" }
            val direct = listOfNotNull(OfferInfo(basePlanId, "intro", emptyList(), phases(base)).takeIf { hasIntro })
            return SubscriptionPlan(basePlanId, offers.map { it(base) } + direct + OfferInfo(basePlanId, null, emptyList(), listOf(base)))
        }
    }
}
