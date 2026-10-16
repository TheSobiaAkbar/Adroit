package io.github.thesobiaakbar.adroit.billing.internal.pricing

import io.github.thesobiaakbar.adroit.billing.PricingPhase
import kotlin.math.floor

/**
 * How much cheaper [phase] is than [reference] per day, as a whole percent rounded down so a saving is never
 * overstated, or null when it can't be said honestly: different currencies, a free or unreadable phase, or no saving.
 */
internal fun savingsPercent(phase: PricingPhase, reference: PricingPhase): Int? {
    if (phase.price.currencyCode != reference.price.currencyCode) return null
    if (phase.price.micros <= 0 || reference.price.micros <= 0) return null
    val days = phase.period?.approxDays ?: return null
    val referenceDays = reference.period?.approxDays ?: return null
    val ratio = (phase.price.micros / days) / (reference.price.micros / referenceDays)
    // The epsilon keeps an exact 1/3 (yearly 8 × monthly vs 12 × monthly) from flooring to 32 through float error.
    val percent = floor((1 - ratio) * 100 + 1e-9).toInt()
    return percent.takeIf { it > 0 }
}
