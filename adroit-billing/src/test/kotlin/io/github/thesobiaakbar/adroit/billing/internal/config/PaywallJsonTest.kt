package io.github.thesobiaakbar.adroit.billing.internal.config

import io.github.thesobiaakbar.adroit.billing.BackBehavior
import io.github.thesobiaakbar.adroit.billing.Campaign
import io.github.thesobiaakbar.adroit.billing.CloseBehavior
import io.github.thesobiaakbar.adroit.billing.CloseButtonPosition
import io.github.thesobiaakbar.adroit.billing.OfferFallback
import io.github.thesobiaakbar.adroit.billing.OfferPolicy
import io.github.thesobiaakbar.adroit.billing.OfferRule
import io.github.thesobiaakbar.adroit.billing.PaywallSpec
import io.github.thesobiaakbar.adroit.billing.PaywallStyle
import io.github.thesobiaakbar.adroit.billing.PaywallTemplate
import io.github.thesobiaakbar.adroit.billing.PaywallTexts
import io.github.thesobiaakbar.adroit.billing.PlanSpec
import io.github.thesobiaakbar.adroit.billing.ShowRules
import io.github.thesobiaakbar.adroit.billing.SuccessAction
import io.github.thesobiaakbar.adroit.billing.TestPaywalls
import io.github.thesobiaakbar.adroit.billing.TestProducts
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PaywallJsonTest {
    private val names = PaywallJson.Names(TestProducts.entries.associateBy { it.name }, TestPaywalls.entries.map { it.name }.toSet(), "Onboarding")
    private val issues = mutableListOf<String>()

    private fun fixture(name: String) = JSONObject(javaClass.classLoader!!.getResource(name)!!.readText())

    private fun merge(base: PaywallSpec, json: String) = PaywallJson.merge(base, JSONObject(json), names, issues::add)

    private val minimal = PaywallSpec(plans = listOf(PlanSpec(TestProducts.PremiumSub)))

    /** paywall_full.json as a spec. */
    private val full = PaywallSpec(
        plans = listOf(
            PlanSpec(
                TestProducts.PremiumSub, "monthly",
                OfferRule(listOf("intro-1"), listOf("trial"), 7, OfferPolicy.PREFER_INTRO_PRICE),
                badge = "POPULAR", savingsVs = "weekly",
            ),
            PlanSpec(TestProducts.Lifetime),
        ),
        enabled = false,
        template = PaywallTemplate.BOTTOM_SHEET,
        selected = 1,
        onOfferUnavailable = OfferFallback.HIDE_PLAN,
        close = CloseBehavior(5, showCountdown = true, CloseButtonPosition.TOP_START, BackBehavior.AFTER_DELAY),
        show = ShowRules(counter = 2, timerSeconds = 60, maxPerSession = 1, maxPerDay = 3, firstAfterLaunches = 2, hideIfEntitled = setOf("premium")),
        campaign = Campaign("autumn26", 1_790_812_800_000, 1_793_491_199_000, "autumn26_onb"),
        exitPaywall = "ExitDiscount",
        texts = PaywallTexts("Go Premium", "Everything unlocked", listOf("No ads", "All filters"), "Continue", "Start {trial} free trial", "Thanks"),
        style = PaywallStyle(0xFF7C4DFF.toInt(), 0x80101014.toInt(), "https://example.com/h.png", dark = true),
        showRestore = false,
        showManage = true,
        showTerms = false,
        successAction = SuccessAction.STAY_WITH_MESSAGE,
    )

    @Test
    fun `every field parses, unknown fields are ignored`() {
        assertEquals(full, PaywallJson.merge(minimal, fixture("paywall_full.json"), names, issues::add))
        assertEquals(emptyList<String>(), issues)
    }

    @Test
    fun `every invalid field is reported and keeps the base value`() {
        assertEquals(full, PaywallJson.merge(full, fixture("paywall_invalid.json"), names, issues::add))
        val expected = listOf(
            "\"enabled\"", "\"template\"", "plan 1: \"product\" 'Hacker'", "plan 2: \"basePlan\"", "plan 3: not an object",
            "\"plans\" has no valid plan", "\"selected\"", "\"onOfferUnavailable\"", "\"close.delaySeconds\"",
            "\"close.showCountdown\"", "\"close.position\"", "\"close.back\"", "\"show.counter\"", "\"show.maxPerSession\"",
            "\"show.maxPerDay\"", "\"show.timerSeconds\"", "\"show.hideIfEntitled\"", "\"campaign.id\"", "\"campaign.start\"",
            "\"campaign.end\"", "\"exitPaywall\"", "\"texts.title\"", "\"texts.subtitle\"", "\"texts.cta\"", "\"style.accent\"",
            "\"style.background\"", "\"style.headerImage\"", "\"style.dark\"", "\"showRestore\"", "\"showManage\"",
            "\"showTerms\"", "\"successAction\"",
        )
        expected.forEach { e -> assertTrue("$e in $issues", issues.any { it.contains(e) }) }
        assertEquals(expected.size, issues.size)
    }

    @Test
    fun `missing fields keep the base`() {
        assertEquals(full, merge(full, "{}"))
        assertEquals(full, merge(full, """{"close": {}, "show": {}, "texts": {}, "style": {}, "campaign": {}}"""))
        assertEquals(emptyList<String>(), issues)
    }

    @Test
    fun `the close delay accepts -1 to 60 and clamps larger values`() {
        assertEquals(CloseBehavior.NO_CLOSE_BUTTON, merge(minimal, """{"close": {"delaySeconds": -1}}""").close.delaySeconds)
        assertEquals(60, merge(minimal, """{"close": {"delaySeconds": 60}}""").close.delaySeconds)
        assertEquals(emptyList<String>(), issues)
        assertEquals(60, merge(minimal, """{"close": {"delaySeconds": 600}}""").close.delaySeconds)
        assertEquals(1, issues.size)
        assertEquals(0, merge(minimal, """{"close": {"delaySeconds": 2.5}}""").close.delaySeconds)
        assertEquals(2, issues.size)
    }

    @Test
    fun `selected is clamped into the plans`() {
        assertEquals(1, merge(minimal, """{"plans": [{"product": "PremiumSub"}, {"product": "Lifetime"}], "selected": 5}""").selected)
        assertEquals(1, issues.size)
        // A base selection beyond fewer remote plans is clamped silently.
        assertEquals(0, merge(full, """{"plans": [{"product": "Coins"}]}""").selected)
        assertEquals(1, issues.size)
    }

    @Test
    fun `invalid plan details are dropped, the plan stays`() {
        val plan = merge(
            minimal,
            """{"plans": [{"product": "PremiumSub", "basePlan": "yearly", "offer": {"ids": ["ok", "Bad ID"], "trialDays": 0,
               "policy": "CHEAPEST"}, "badge": "<i>x</i>", "savingsVs": "Not valid"}]}""",
        ).plans.single()
        assertEquals(PlanSpec(TestProducts.PremiumSub, "yearly", OfferRule(ids = listOf("ok"))), plan)
        assertEquals(5, issues.size)
    }

    @Test
    fun `texts are at most 300 characters without HTML`() {
        val ok = "x".repeat(300)
        val texts = merge(minimal, """{"texts": {"title": "$ok", "subtitle": "${ok}y", "features": ["A & B", "<script>", ""], "footer": "1 &lt; 2"}}""").texts
        assertEquals(PaywallTexts(title = ok, features = listOf("A & B")), texts)
        assertEquals(4, issues.size)
        // Placeholders are carried; the UI resolves them.
        assertEquals("Start {trial}", merge(minimal, """{"texts": {"cta": "Start {trial}"}}""").texts.cta)
    }

    @Test
    fun `campaign times are ISO-8601 instants with a zone, ending after they start`() {
        val c = merge(minimal, """{"campaign": {"id": "a", "start": "2026-10-01T02:00:00+02:00", "end": "2026-10-01T00:00:01.500Z"}}""").campaign
        assertEquals(Campaign("a", 1_790_812_800_000, 1_790_812_801_500), c)
        assertNull(merge(minimal, """{"campaign": {"id": "a", "start": "2026-10-02T00:00:00Z", "end": "2026-10-01T00:00:00Z"}}""").campaign)
        assertNull(merge(minimal, """{"campaign": {"start": "2026-10-02T00:00:00Z"}}""").campaign)
        assertNull(merge(minimal, """{"campaign": {"id": "a", "start": "2026-10-01T00:00:00"}}""").campaign?.startMillis)
        assertEquals(3, issues.size)
    }

    @Test
    fun `colors and instants round-trip`() {
        assertEquals(0xFF7C4DFF.toInt(), JsonFields.parseColor("#7c4dff"))
        assertEquals("#7C4DFF", JsonFields.formatColor(0xFF7C4DFF.toInt()))
        assertEquals("#00000000", JsonFields.formatColor(0))
        assertNull(JsonFields.parseColor("#7C4DF"))
        assertEquals("2026-10-01T00:00:01.500Z", JsonFields.formatInstant(1_790_812_801_500))
    }

    @Test
    fun `defaults print as JSON that parses back to the defaults`() {
        assertEquals(full, PaywallJson.merge(minimal, JSONObject(PaywallJson.write(full).toString()), names, issues::add))
        for (paywall in TestPaywalls.entries) {
            val own = PaywallJson.Names(names.products, names.paywalls, paywall.name)
            assertEquals(paywall.name, paywall.defaults, PaywallJson.merge(minimal, JSONObject(paywall.toJson()), own, issues::add))
        }
        assertEquals(emptyList<String>(), issues)
        val printed = JSONObject(TestPaywalls.Onboarding.toJson())
        assertEquals(3, printed.getJSONObject("close").getInt("delaySeconds"))
        assertEquals("plans", printed.getString("template"))
        assertEquals("yearly", printed.getJSONArray("plans").getJSONObject(0).getString("basePlan"))
    }
}
