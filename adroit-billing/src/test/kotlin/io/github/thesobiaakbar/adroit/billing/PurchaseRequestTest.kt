package io.github.thesobiaakbar.adroit.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PurchaseRequestTest {
    @Test
    fun `valid placements are accepted`() {
        listOf("onboarding", "monthly_premium_home", "exit.dialog", "home-banner", "A1", "x".repeat(64)).forEach {
            assertEquals(it, PurchaseRequest(TestProducts.PremiumSub, placement = it).placement)
        }
    }

    @Test
    fun `invalid placements are rejected`() {
        listOf("", " ", "with space", "accenté", "slash/key", "x".repeat(65)).forEach {
            assertThrows(it, IllegalArgumentException::class.java) {
                PurchaseRequest(TestProducts.PremiumSub, placement = it)
            }
        }
    }

    @Test
    fun `an invalid base plan ID is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PurchaseRequest(TestProducts.PremiumSub, placement = "home", basePlanId = "Monthly_Plan")
        }
    }

    @Test
    fun `defaults choose the offer automatically and carry no campaign`() {
        val request = PurchaseRequest(TestProducts.PremiumSub, placement = "home")
        assertEquals(OfferSelection.Auto, request.offer)
        assertNull(request.campaignId)
        assertNull(request.basePlanId)
        assertNull(request.paywallId)
    }
}
