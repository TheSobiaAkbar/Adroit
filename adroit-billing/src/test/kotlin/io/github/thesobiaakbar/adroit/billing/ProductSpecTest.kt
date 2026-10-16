package io.github.thesobiaakbar.adroit.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductSpecTest {
    @Test
    fun `factories set the type and keep entitlements`() {
        assertEquals(ProductType.SUBSCRIPTION, ProductSpec.subscription("a.b_1", setOf("premium")).type)
        assertEquals(setOf("premium"), ProductSpec.nonConsumable("lifetime", setOf("premium")).entitlements)
        assertTrue(ProductSpec.consumable("coins").entitlements.isEmpty())
    }

    @Test
    fun `valid Play product IDs are accepted`() {
        listOf("p", "0premium", "premium_sub", "com.app.premium", "a".repeat(139)).forEach {
            ProductSpec.consumable(it)
        }
    }

    @Test
    fun `invalid Play product IDs are rejected`() {
        listOf("", "Premium", "_premium", ".premium", "premium-sub", "premium sub", "a".repeat(140)).forEach {
            assertThrows(it, IllegalArgumentException::class.java) { ProductSpec.consumable(it) }
        }
    }

    @Test
    fun `invalid entitlements are rejected`() {
        listOf("", "no spaces", "x".repeat(41)).forEach {
            assertThrows(it, IllegalArgumentException::class.java) { ProductSpec.subscription("sub", setOf(it)) }
        }
    }

    @Test
    fun `entitlements are copied, so later changes to the caller's set don't leak in`() {
        val set = mutableSetOf("premium")
        val spec = ProductSpec.subscription("sub", set)
        set += "other"
        assertEquals(setOf("premium"), spec.entitlements)
    }

    @Test
    fun `equality is by value`() {
        val a = ProductSpec.subscription("sub", setOf("a"))
        assertEquals(a, ProductSpec.subscription("sub", setOf("a")))
        assertEquals(a.hashCode(), ProductSpec.subscription("sub", setOf("a")).hashCode())
        assertNotEquals(a, ProductSpec.nonConsumable("sub", setOf("a")))
    }
}
