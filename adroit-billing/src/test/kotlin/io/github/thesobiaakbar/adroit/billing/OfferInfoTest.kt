package io.github.thesobiaakbar.adroit.billing

import io.github.thesobiaakbar.adroit.billing.Phases.base
import io.github.thesobiaakbar.adroit.billing.Phases.free
import io.github.thesobiaakbar.adroit.billing.Phases.offer
import io.github.thesobiaakbar.adroit.billing.Phases.phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferInfoTest {
    @Test
    fun `free trial then monthly`() {
        val offer = offer(free("P1W"), base("P1M", 4_990_000))
        assertEquals(BillingPeriod(weeks = 1), offer.freeTrial)
        assertTrue(offer.introPhases.isEmpty())
        assertEquals(4_990_000L, offer.basePhase?.price?.micros)
        assertEquals(offer.basePhase, offer.firstPayment)
        assertFalse(offer.isPrepaid)
    }

    @Test
    fun `a trial over several cycles lasts all of them`() {
        assertEquals(BillingPeriod(days = 6), offer(free("P3D", cycles = 2), base("P1M", 1)).freeTrial)
    }

    @Test
    fun `free trial, then an intro price, then the base price`() {
        val intro = phase("P1M", 990_000, Recurrence.FINITE, cycles = 3)
        val offer = offer(free("P3D"), intro, base("P1M", 4_990_000))
        assertEquals(BillingPeriod(days = 3), offer.freeTrial)
        assertEquals(listOf(intro), offer.introPhases)
        assertEquals(intro, offer.firstPayment)
        assertEquals(BillingPeriod(months = 3), intro.totalPeriod)
    }

    @Test
    fun `a non-recurring intro price counts as intro`() {
        val intro = phase("P3M", 1_990_000, Recurrence.NON_RECURRING)
        assertEquals(listOf(intro), offer(intro, base("P1Y", 29_990_000)).introPhases)
    }

    @Test
    fun `the base plan alone has no trial and no intro`() {
        val offer = offer(base("P1Y", 29_990_000), offerId = null)
        assertNull(offer.freeTrial)
        assertTrue(offer.introPhases.isEmpty())
        assertEquals(BillingPeriod(years = 1), offer.basePhase?.period)
    }

    @Test
    fun `a prepaid plan has no renewal price`() {
        val offer = offer(phase("P1M", 3_990_000, Recurrence.NON_RECURRING), offerId = null)
        assertTrue(offer.isPrepaid)
        assertNull(offer.basePhase)
        assertEquals(3_990_000L, offer.firstPayment?.price?.micros)
    }

    @Test
    fun `a free first phase with an unreadable period is never a trial`() {
        assertNull(offer(free("PT72H"), base("P1M", 1)).freeTrial)
    }

    @Test
    fun `an empty offer is neither prepaid nor priced`() {
        val offer = offer()
        assertFalse(offer.isPrepaid)
        assertNull(offer.basePhase)
        assertNull(offer.freeTrial)
        assertNull(offer.firstPayment)
    }

    @Test
    fun `the plan's base offer is the one without an offer ID`() {
        val trial = offer(free("P1W"), base("P1M", 1))
        val baseOnly = offer(base("P1M", 1), offerId = null)
        assertEquals(baseOnly, SubscriptionPlan("monthly", listOf(trial, baseOnly)).baseOffer)
        assertNull(SubscriptionPlan("monthly", listOf(trial)).baseOffer)
    }
}
