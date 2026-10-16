package io.github.thesobiaakbar.adroit.billing

import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import io.github.thesobiaakbar.adroit.billing.Phases.base
import io.github.thesobiaakbar.adroit.billing.Phases.free
import io.github.thesobiaakbar.adroit.billing.Phases.offer
import io.github.thesobiaakbar.adroit.billing.Phases.phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class PriceTextTest {
    private val resources: Resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    @Test
    fun `durations of every Play period`() {
        mapOf(
            "P1W" to "1 week",
            "P1M" to "1 month",
            "P2M" to "2 months",
            "P3M" to "3 months",
            "P6M" to "6 months",
            "P1Y" to "1 year",
            "P3D" to "3 days",
            "P7D" to "7 days",
            "P1D" to "1 day",
            "P1Y2M" to "1 year 2 months",
        ).forEach { (iso, text) -> assertEquals(iso, text, PriceText.duration(resources, BillingPeriod.parse(iso)!!)) }
    }

    @Test
    fun `billing periods after a price`() {
        mapOf(
            "P1W" to "week",
            "P1M" to "month",
            "P2M" to "2 months",
            "P3M" to "3 months",
            "P6M" to "6 months",
            "P1Y" to "year",
            "P1Y6M" to "1 year 6 months",
        ).forEach { (iso, text) -> assertEquals(iso, text, PriceText.every(resources, BillingPeriod.parse(iso)!!)) }
    }

    @Test
    fun `a trial text is filled from the offer`() {
        val offer = offer(free("P1W"), base("P1M", 4_990_000))
        assertEquals(
            "1 week free, then USD 4.99 / month",
            PriceText.fill(resources, "{trial} free, then {renewalPrice} / {renewalPeriod}", offer),
        )
    }

    @Test
    fun `a trial text on an offer without a trial is null, never a false trial`() {
        assertNull(PriceText.fill(resources, "Start your {trial} free trial", offer(base("P1M", 4_990_000), offerId = null)))
    }

    @Test
    fun `price and period are the first payment`() {
        val offer = offer(phase("P1M", 990_000, Recurrence.FINITE, cycles = 3), base("P1M", 4_990_000))
        assertEquals("USD 0.99 / month", PriceText.fill(resources, "{price} / {period}", offer))
    }

    @Test
    fun `a prepaid plan has no renewal text`() {
        val prepaid = offer(phase("P1M", 3_990_000, Recurrence.NON_RECURRING), offerId = null)
        assertEquals("USD 3.99 for month", PriceText.fill(resources, "{price} for {period}", prepaid))
        assertNull(PriceText.fill(resources, "renews at {renewalPrice}", prepaid))
    }

    @Test
    fun `unknown placeholders make the text null`() {
        assertNull(PriceText.fill(resources, "{prise}", offer(base("P1M", 1))))
        assertEquals("No placeholders", PriceText.fill(resources, "No placeholders", offer(base("P1M", 1))))
    }

    @Test
    fun `per-week and per-month prices are rounded up`() {
        val yearly = offer(base("P1Y", 39_990_000))
        // 39.99 × 7 / 365.2425 = 0.7664…: shown as 0.77, never 0.76.
        assertEquals("$0.77", PriceText.fill(resources, "{perWeek}", yearly))
        // 39.99 / 12 = 3.3325: 3.34.
        assertEquals("$3.34", PriceText.fill(resources, "{perMonth}", yearly))
    }

    @Test
    fun `a currency without decimals keeps none`() {
        val yen = offer(base("P1Y", 10_000_000_000, currency = "JPY"))
        assertEquals("¥834", PriceText.fill(resources, "{perMonth}", yen))
    }

    @Test
    fun `savings against another plan`() {
        val yearly = offer(base("P1Y", 39_990_000))
        val monthly = offer(base("P1M", 4_990_000))
        assertEquals("Save 33%", PriceText.fill(resources, "Save {savings}", yearly, savingsVs = monthly))
        assertNull(PriceText.fill(resources, "Save {savings}", yearly))
        assertNull(PriceText.fill(resources, "Save {savings}", monthly, savingsVs = yearly))
    }
}
