package io.github.thesobiaakbar.adroit.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SpecEqualityTest {
    private fun spec(delay: Int = 3) = PaywallSpec(
        plans = listOf(PlanSpec(TestProducts.PremiumSub, basePlanId = "monthly", offer = OfferRule(trialDays = 7))),
        close = CloseBehavior(delaySeconds = delay, showCountdown = true, back = BackBehavior.AFTER_DELAY),
        show = ShowRules(counter = 3, maxPerDay = 2),
        campaign = Campaign("autumn26", startMillis = 1, endMillis = 2),
        texts = PaywallTexts(title = "Go Premium", features = listOf("No ads")),
        style = PaywallStyle(accent = 0xFF7C4DFF.toInt(), headerImage = "https://example.com/h.png"),
    )

    @Test
    fun `equal specs are equal with equal hash codes`() {
        assertEquals(spec(), spec())
        assertEquals(spec().hashCode(), spec().hashCode())
        assertNotEquals(spec(3), spec(5))
    }

    @Test
    fun `defaults show the close button at once and allow back`() {
        val close = PaywallSpec(listOf(PlanSpec(TestProducts.Coins))).close
        assertEquals(0, close.delaySeconds)
        assertEquals(BackBehavior.ALWAYS, close.back)
    }

    @Test
    fun `-1 means no close button and lower values are rejected`() {
        assertEquals(CloseBehavior.NO_CLOSE_BUTTON, CloseBehavior(delaySeconds = -1).delaySeconds)
        assertThrows(IllegalArgumentException::class.java) { CloseBehavior(delaySeconds = -2) }
    }

    @Test
    fun `invalid values are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ShowRules(counter = -1) }
        assertThrows(IllegalArgumentException::class.java) { OfferRule(trialDays = 0) }
        assertThrows(IllegalArgumentException::class.java) { Campaign("c", startMillis = 2, endMillis = 1) }
        assertThrows(IllegalArgumentException::class.java) { PaywallStyle(headerImage = "http://insecure") }
        assertThrows(IllegalArgumentException::class.java) { PlanSpec(TestProducts.PremiumSub, basePlanId = "Bad_Id") }
        assertThrows(IllegalArgumentException::class.java) { BillingPeriod() }
        assertThrows(IllegalArgumentException::class.java) { BillingSettings(TestProducts.entries, offlineGraceHours = 0) }
    }
}
