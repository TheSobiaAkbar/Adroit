package io.github.thesobiaakbar.adroit.billing.internal.config

import io.github.thesobiaakbar.adroit.billing.BillingSettings
import io.github.thesobiaakbar.adroit.billing.TestPaywalls
import io.github.thesobiaakbar.adroit.billing.TestProducts
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * docs/PAYWALL_REMOTE_CONFIG.md documents every field the parsers read, and nothing more (B23). The parsers' fields
 * aren't listed by hand: they are recorded while [PaywallJson] and [BillingGlobalJson] read a JSON that sets
 * everything, and compared with the first column of the doc's "Field" tables.
 */
@RunWith(RobolectricTestRunner::class)
class RemoteConfigDocTest {
    /** A JSON object that records the path of every field read from it. */
    private class Recording(private val path: String, private val read: MutableSet<String>) : JSONObject() {
        override fun opt(name: String?): Any? {
            read += path + name
            return super.opt(name)
        }

        override fun has(name: String?): Boolean {
            read += path + name
            return super.has(name)
        }

        fun with(name: String, value: Any): Recording = apply { put(name, value) }
    }

    private val read = mutableSetOf<String>()

    private fun obj(path: String) = Recording(path, read)

    /** The fields read, without the objects and lists read on the way to them ("close" for "close.back"). */
    private fun leaves(fields: Set<String>): Set<String> =
        fields.filterNot { field -> fields.any { it.startsWith("$field.") || it.startsWith("$field[].") } }.toSortedSet()

    private fun paywallJson(): JSONObject {
        val plan = obj("plans[].")
            .with("product", "PremiumSub")
            .with("basePlan", "yearly")
            .with(
                "offer",
                obj("plans[].offer.")
                    .with("ids", JSONArray(listOf("summer")))
                    .with("tags", JSONArray(listOf("trial")))
                    .with("trialDays", 7)
                    .with("policy", "BASE_ONLY"),
            )
            .with("badge", "BEST_VALUE")
            .with("savingsVs", "monthly")
        return obj("")
            .with("enabled", true)
            .with("template", "dialog")
            .with("plans", JSONArray().put(plan))
            .with("selected", 0)
            .with("onOfferUnavailable", "HIDE_PLAN")
            .with(
                "close",
                obj("close.").with("delaySeconds", 5).with("showCountdown", true).with("position", "TOP_START")
                    .with("back", "NEVER"),
            )
            .with(
                "show",
                obj("show.").with("counter", 2).with("timerSeconds", 60).with("maxPerSession", 1).with("maxPerDay", 2)
                    .with("firstAfterLaunches", 3).with("hideIfEntitled", JSONArray(listOf("premium"))),
            )
            .with(
                "campaign",
                obj("campaign.").with("id", "bf").with("start", "2026-11-27T00:00:00Z").with("end", "2026-12-01T00:00:00Z")
                    .with("analyticsId", "a"),
            )
            .with("exitPaywall", "ExitDiscount")
            .with(
                "texts",
                obj("texts.").with("title", "t").with("subtitle", "s").with("features", JSONArray(listOf("f")))
                    .with("cta", "c").with("ctaTrial", "ct").with("footer", "fo"),
            )
            .with(
                "style",
                obj("style.").with("accent", "#112233").with("background", "#000000")
                    .with("headerImage", "https://example.com/a.png").with("dark", true),
            )
            .with("showRestore", false)
            .with("showManage", true)
            .with("showTerms", false)
            .with("successAction", "STAY_WITH_MESSAGE")
    }

    private fun globalJson(): JSONObject = obj("")
        .with("enabled", false)
        .with("adFreeEntitlements", JSONArray(listOf("premium")))
        .with("offlineGraceHours", 48)
        .with(
            "products",
            obj("products.").with("PremiumSub", obj("products.<ProductName>.").with("entitlements", JSONArray(listOf("vip")))),
        )
        .with("configVersion", 3)

    /** The first cells of the doc's "| Field |" tables, before or after the AdroitBilling heading. */
    private fun documented(global: Boolean): Set<String> {
        val doc = File("../docs/PAYWALL_REMOTE_CONFIG.md").readLines()
        val split = doc.indexOfFirst { it.startsWith("## The `AdroitBilling` parameter") }
        val lines = if (global) doc.drop(split).takeWhile { it != "## Putting it in Firebase" } else doc.take(split)
        val fields = mutableSetOf<String>()
        var inFieldTable = false
        for (line in lines) {
            if (!line.startsWith("|")) {
                inFieldTable = false
                continue
            }
            if (line.startsWith("| Field |")) inFieldTable = true
            if (!inFieldTable) continue
            Regex("""^\| `([^`]+)` \|""").find(line)?.let { fields += it.groupValues[1] }
        }
        return fields
    }

    @Test
    fun `every paywall field the parser reads is in the doc, and nothing else`() {
        val names = PaywallJson.Names(TestProducts.entries.associateBy { it.name }, TestPaywalls.entries.map { it.name }.toSet(), "Onboarding")
        val issues = mutableListOf<String>()
        PaywallJson.merge(TestPaywalls.Onboarding.defaults, paywallJson(), names) { issues += it }
        assertEquals("the test JSON must be valid", emptyList<String>(), issues)
        assertEquals(leaves(read), documented(global = false).toSortedSet())
    }

    @Test
    fun `every AdroitBilling field the parser reads is in the doc, and nothing else`() {
        val issues = mutableListOf<String>()
        BillingGlobalJson.merge(GlobalConfig.defaults(BillingSettings(TestProducts.entries)), globalJson()) { issues += it }
        assertEquals("the test JSON must be valid", emptyList<String>(), issues)
        // The product name is a placeholder in the doc: "products.<ProductName>.entitlements".
        assertEquals(leaves(read - "products.PremiumSub"), documented(global = true).toSortedSet())
    }
}
