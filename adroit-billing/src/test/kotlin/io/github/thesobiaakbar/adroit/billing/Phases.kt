package io.github.thesobiaakbar.adroit.billing

/** Builders of Play-like pricing data for tests. */
object Phases {
    fun phase(
        iso: String,
        micros: Long,
        recurrence: Recurrence,
        cycles: Int = if (recurrence == Recurrence.INFINITE) 0 else 1,
        currency: String = "USD",
    ) = PricingPhase(
        price = Price(formatted(micros, currency), micros, currency),
        billingPeriod = iso,
        period = BillingPeriod.parse(iso),
        billingCycleCount = cycles,
        recurrence = recurrence,
    )

    fun free(iso: String, cycles: Int = 1) = phase(iso, 0, Recurrence.FINITE, cycles)

    fun base(iso: String, micros: Long, currency: String = "USD") = phase(iso, micros, Recurrence.INFINITE, currency = currency)

    fun offer(vararg phases: PricingPhase, offerId: String? = "offer", basePlanId: String = "plan") =
        OfferInfo(basePlanId, offerId, emptyList(), phases.toList())

    private fun formatted(micros: Long, currency: String) = "$currency ${micros / 1_000_000.0}"
}
