package io.github.thesobiaakbar.adroit.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BillingPeriodTest {
    @Test
    fun `Play periods parse`() {
        mapOf(
            "P1W" to BillingPeriod(weeks = 1),
            "P1M" to BillingPeriod(months = 1),
            "P2M" to BillingPeriod(months = 2),
            "P3M" to BillingPeriod(months = 3),
            "P6M" to BillingPeriod(months = 6),
            "P1Y" to BillingPeriod(years = 1),
            "P3D" to BillingPeriod(days = 3),
            "P7D" to BillingPeriod(days = 7),
            "P14D" to BillingPeriod(days = 14),
            "P30D" to BillingPeriod(days = 30),
            "P4W" to BillingPeriod(weeks = 4),
            "P1Y2M" to BillingPeriod(years = 1, months = 2),
            "P2W3D" to BillingPeriod(weeks = 2, days = 3),
            "P1Y0M" to BillingPeriod(years = 1),
            " P1M " to BillingPeriod(months = 1),
        ).forEach { (iso, expected) -> assertEquals(iso, expected, BillingPeriod.parse(iso)) }
    }

    @Test
    fun `anything else is null, never an exception`() {
        listOf("", "P", "P0D", "P0Y0M", "PT1H", "P1DT2H", "P1H", "1M", "P-1M", "P1.5M", "p1m", "P1M1Y", "P99999D", "garbage")
            .forEach { assertNull(it, BillingPeriod.parse(it)) }
    }

    @Test
    fun `a year is exactly twelve months and a week seven days`() {
        assertEquals(BillingPeriod(years = 1).approxDays, BillingPeriod(months = 12).approxDays, 1e-9)
        assertEquals(7.0, BillingPeriod(weeks = 1).approxDays, 1e-9)
        assertEquals(365.2425, BillingPeriod(years = 1).approxDays, 1e-9)
    }
}
