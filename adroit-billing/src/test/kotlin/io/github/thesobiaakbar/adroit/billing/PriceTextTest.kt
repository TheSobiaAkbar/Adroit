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

    private fun plan(offer: OfferInfo? = null, option: OneTimeOption? = null, cta: String? = null): PaywallPlan {
        val product = ProductInfo(TestProducts.PremiumSub, "premium_sub", ProductType.SUBSCRIPTION, "P", "P", "", emptyList(), emptyList())
        return PaywallPlan(PlanSpec(TestProducts.PremiumSub), product, offer?.basePlanId, offer, option, false, null, PaywallTexts(cta = cta))
    }

    @Test
    fun `disclosures say what is charged and when, from Play's data only`() {
        val monthly = base("P1M", 4_990_000)
        assertEquals(
            "7 days free, then USD 4.99/month. Cancel anytime in Google Play.",
            PriceText.disclosure(resources, plan(offer(free("P7D"), monthly))),
        )
        assertEquals(
            "USD 0.99 for 3 months, then USD 4.99/month. Cancel anytime in Google Play.",
            PriceText.disclosure(resources, plan(offer(phase("P1M", 990_000, Recurrence.FINITE, cycles = 3), monthly))),
        )
        assertEquals(
            "USD 4.99/month, renews automatically. Cancel anytime in Google Play.",
            PriceText.disclosure(resources, plan(offer(monthly))),
        )
        assertEquals(
            "USD 9.99 for 1 month. Doesn't renew automatically.",
            PriceText.disclosure(resources, plan(offer(phase("P1M", 9_990_000, Recurrence.NON_RECURRING)))),
        )
        val lifetime = OneTimeOption(null, null, emptyList(), Price("USD 19.99", 19_990_000, "USD"))
        assertEquals("One-time payment of USD 19.99.", PriceText.disclosure(resources, plan(option = lifetime)))
        assertNull(PriceText.disclosure(resources, plan(offer(phase("PXYZ", 4_990_000, Recurrence.INFINITE)))))
    }

    @Test
    fun `plan price and purchase button`() {
        val trial = plan(offer(free("P7D"), base("P1Y", 29_990_000)))
        assertEquals("USD 29.99 / year", PriceText.planPrice(resources, trial))
        assertEquals("Start free trial", PriceText.cta(resources, trial))
        assertEquals("Continue", PriceText.cta(resources, plan(offer(base("P1M", 4_990_000)))))
        assertEquals("Subscribe now", PriceText.cta(resources, plan(offer(base("P1M", 4_990_000)), cta = "Subscribe now")))
    }
}
