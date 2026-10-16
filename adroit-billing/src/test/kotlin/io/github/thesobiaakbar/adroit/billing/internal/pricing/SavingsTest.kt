package io.github.thesobiaakbar.adroit.billing.internal.pricing

import io.github.thesobiaakbar.adroit.billing.Phases.base
import io.github.thesobiaakbar.adroit.billing.Phases.free
import io.github.thesobiaakbar.adroit.billing.Phases.phase
import io.github.thesobiaakbar.adroit.billing.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Test

class SavingsTest {
    private val monthly = base("P1M", 4_990_000)

    @Test
    fun `yearly against monthly`() {
        // 39.99 / (12 × 4.99) = 0.6678…: 33.2% cheaper, shown as 33.
        assertEquals(33, savingsPercent(base("P1Y", 39_990_000), monthly))
    }

    @Test
    fun `an exact third stays 33, not 32 through float error`() {
        assertEquals(33, savingsPercent(base("P1Y", 8 * 4_990_000L), monthly))
    }

    @Test
    fun `savings are rounded down, never up`() {
        // 0.995 of the monthly price per day: 0.5% cheaper, which is no whole percent.
        assertEquals(null, savingsPercent(base("P1Y", (12 * 4_990_000L * 995) / 1000), monthly))
        // 59.88 × 0.99 = 59.2812: 1% cheaper, not more.
        assertEquals(1, savingsPercent(base("P1Y", 59_281_200), monthly))
    }

    @Test
    fun `quarterly and six-monthly against monthly`() {
        assertEquals(19, savingsPercent(base("P3M", 11_990_000), monthly))
        assertEquals(33, savingsPercent(base("P6M", 19_990_000), monthly))
    }

    @Test
    fun `no honest saving is null`() {
        val cases = listOf(
            base("P1Y", 59_880_000) to monthly, // the same price per day
            base("P1Y", 69_990_000) to monthly, // dearer
            base("P1Y", 39_990_000, currency = "EUR") to monthly, // another currency
            free("P1W") to monthly, // free
            base("P1Y", 39_990_000) to free("P1W"), // free reference
            base("PT1H", 1) to monthly, // unreadable period
            phase("P1Y", 39_990_000, Recurrence.NON_RECURRING) to base("PT1M", 1), // unreadable reference
        )
        cases.forEach { (phase, reference) -> assertEquals(phase.billingPeriod, null, savingsPercent(phase, reference)) }
    }
}
