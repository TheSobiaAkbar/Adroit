package io.github.thesobiaakbar.adroit.billing.internal.config

import io.github.thesobiaakbar.adroit.billing.BackBehavior
import io.github.thesobiaakbar.adroit.billing.BillingConfigSource
import io.github.thesobiaakbar.adroit.billing.BillingEvent
import io.github.thesobiaakbar.adroit.billing.BillingEventListener
import io.github.thesobiaakbar.adroit.billing.BillingLogLevel
import io.github.thesobiaakbar.adroit.billing.BillingSettings
import io.github.thesobiaakbar.adroit.billing.TestPaywalls
import io.github.thesobiaakbar.adroit.billing.TestProducts
import io.github.thesobiaakbar.adroit.billing.internal.BillingLog
import io.github.thesobiaakbar.adroit.billing.internal.events.EventBus
import io.github.thesobiaakbar.adroit.billing.localSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BillingConfigTest {
    private val events = mutableListOf<BillingEvent>()
    private val warnings = mutableListOf<String>()
    private val log = BillingLog(BillingLogLevel.WARN) { _, message -> warnings += message }
    private val remote = mutableMapOf<String, String?>()

    private fun config(overrides: Map<String, String> = emptyMap(), source: BillingConfigSource = BillingConfigSource { remote[it] }) =
        BillingConfig(
            BillingSettings(TestProducts.entries, TestPaywalls.entries, config = source, overrides = overrides, adFreeEntitlements = setOf("premium")),
            EventBus(listOf(BillingEventListener { events += it }), log),
            log,
            now = { 1 },
        )

    private fun invalid() = events.filterIsInstance<BillingEvent.ConfigInvalid>()

    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun `each field comes from the first layer that sets it validly`() {
        remote["Onboarding"] = """{"close": {"delaySeconds": 10, "showCountdown": true}, "showManage": true}"""
        val overrides = mapOf("Onboarding" to """{"close": {"delaySeconds": "x"}, "enabled": false}""")
        val spec = config(overrides).paywall(TestPaywalls.Onboarding).spec
        assertEquals(10, spec.close.delaySeconds) // override invalid → remote
        assertTrue(spec.close.showCountdown) // remote only
        assertFalse(spec.enabled) // override
        assertTrue(spec.showManage) // remote
        assertEquals(TestPaywalls.Onboarding.defaults.plans, spec.plans) // defaults
        val issue = invalid().single()
        assertEquals("Onboarding", issue.key)
        assertTrue(issue.reason, issue.reason.startsWith("override: \"close.delaySeconds\""))
        assertEquals(1, warnings.size)
        // Paywalls without values keep their defaults.
        assertEquals(TestPaywalls.Credits.defaults, config(overrides).paywall(TestPaywalls.Credits).spec)
    }

    @Test
    fun `the global key parses, remaps only declared products and records its version`() {
        remote["AdroitBilling"] = fixture("billing_global.json")
        val global = config(mapOf("AdroitBilling" to """{"offlineGraceHours": 0, "adFreeEntitlements": ["premium", "ad_free"]}""")).global
        assertFalse(global.enabled)
        assertEquals(setOf("premium", "ad_free"), global.adFreeEntitlements)
        assertEquals(24, global.offlineGraceHours)
        assertEquals(
            mapOf("PremiumSub" to setOf("premium"), "Lifetime" to setOf("premium", "ad_free"), "Coins" to emptySet()),
            global.productEntitlements,
        )
        assertEquals(listOf("remote: \"products.Hacker\"", "override: \"offlineGraceHours\""), invalid().map { it.reason.substringBefore(" isn't").substringBefore(" must") })
        assertEquals("3", events.filterIsInstance<BillingEvent.ConfigLoaded>().single().version)
    }

    @Test
    fun `without values everything is the in-code defaults`() {
        val config = config()
        val global = config.global
        assertTrue(global.enabled)
        assertEquals(setOf("premium"), global.adFreeEntitlements)
        assertEquals(72, global.offlineGraceHours)
        assertEquals(TestProducts.entries.associate { it.name to it.defaults.entitlements }, global.productEntitlements)
        assertNull(global.version)
        TestPaywalls.entries.forEach { assertEquals(it.defaults, config.paywall(it).spec) }
        assertEquals(listOf(BillingEvent.ConfigLoaded(null, 1)), events)
    }

    @Test
    fun `empty, null, array, huge and throwing values are ignored without a crash`() {
        remote["Onboarding"] = ""
        remote["ExitDiscount"] = "null"
        remote["Credits"] = "[1, 2]"
        remote["AdroitBilling"] = """{"configVersion": "${"x".repeat(1_000_000)}"}"""
        val config = config()
        TestPaywalls.entries.forEach { assertEquals(it.defaults, config.paywall(it).spec) }
        assertNull(config.global.version)
        assertEquals(listOf("AdroitBilling", "ExitDiscount", "Credits"), invalid().map { it.key })
        assertTrue(invalid().first().reason.contains("64 KB"))

        events.clear()
        val throwing = config(source = { error("source bug") })
        assertEquals(TestPaywalls.Onboarding.defaults, throwing.paywall(TestPaywalls.Onboarding).spec)
        assertEquals(1 + TestPaywalls.entries.size, invalid().size)
    }

    @Test
    fun `a hard paywall is allowed and reported once per process`() {
        remote["Onboarding"] = """{"close": {"delaySeconds": -1, "back": "NEVER"}}"""
        val config = config()
        assertTrue(config.paywall(TestPaywalls.Onboarding).hard)
        config.reload()
        assertEquals(listOf(BillingEvent.HardPaywall("Onboarding", 1)), events.filterIsInstance<BillingEvent.HardPaywall>())
        assertEquals(1, warnings.count { "hard paywall" in it })
        assertFalse(config.paywall(TestPaywalls.ExitDiscount).hard)
        assertEquals(BackBehavior.NEVER, config.paywall(TestPaywalls.Onboarding).spec.close.back)
    }

    @Test
    fun `a reload applies to the next opening, an opened paywall keeps its settings`() {
        val config = config()
        val opened = config.paywall(TestPaywalls.Onboarding)
        remote["Onboarding"] = """{"close": {"delaySeconds": 9}}"""
        assertEquals(3, config.paywall(TestPaywalls.Onboarding).spec.close.delaySeconds)
        config.reload()
        assertEquals(9, config.paywall(TestPaywalls.Onboarding).spec.close.delaySeconds)
        assertEquals(3, opened.spec.close.delaySeconds)
        assertEquals(2, events.filterIsInstance<BillingEvent.ConfigLoaded>().size)
    }

    @Test
    fun `a local source maps each top-level key to its JSON value`() {
        val source = localSource("""{"AdroitBilling": {"enabled": false}, "Onboarding": {"selected": 0}, "Note": "text"}""", "test")
        assertEquals("""{"enabled":false}""", source.raw("AdroitBilling"))
        assertEquals("text", source.raw("Note"))
        assertNull(source.raw("Credits"))
        assertFalse(config(source = source).global.enabled)
        assertThrows(IllegalArgumentException::class.java) { localSource("[]", "test") }
    }
}
