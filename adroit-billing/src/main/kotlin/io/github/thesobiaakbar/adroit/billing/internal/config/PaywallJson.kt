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
import io.github.thesobiaakbar.adroit.billing.ProductDefinition
import io.github.thesobiaakbar.adroit.billing.ProductType
import io.github.thesobiaakbar.adroit.billing.ShowRules
import io.github.thesobiaakbar.adroit.billing.SuccessAction
import org.json.JSONArray
import org.json.JSONObject

/**
 * A paywall's JSON (06 §5.2): [merge] lays one layer over a [PaywallSpec] field by field, [write] prints a spec in
 * the same JSON. Never throws: a wrong field is reported and the base value stays. Unknown fields are ignored.
 */
internal object PaywallJson {
    /** What a paywall's JSON may refer to: the declared products by name and the other paywalls' names. */
    class Names(val products: Map<String, ProductDefinition>, val paywalls: Set<String>, val self: String)

    const val MAX_CLOSE_DELAY = 60

    fun merge(base: PaywallSpec, json: JSONObject, names: Names, issue: (String) -> Unit): PaywallSpec {
        val f = JsonFields(json, "", issue)
        val plans = f.array("plans")?.let { plans(it, names, issue) } ?: base.plans
        var selected = f.int("selected", 0..Int.MAX_VALUE) ?: base.selected
        if (selected > plans.lastIndex) {
            if (json.has("selected")) issue("\"selected\" is $selected but there are ${plans.size} plans; using ${plans.lastIndex}")
            selected = plans.lastIndex.coerceAtLeast(0)
        }
        return PaywallSpec(
            plans = plans,
            enabled = f.boolean("enabled") ?: base.enabled,
            template = f.string("template")?.let { template(it) ?: f.wrong("template", "plans|single|bottom_sheet|dialog") }
                ?: base.template,
            selected = selected,
            onOfferUnavailable = f.enum("onOfferUnavailable", OfferFallback.entries.toTypedArray()) ?: base.onOfferUnavailable,
            close = f.obj("close")?.let { close(base.close, it, issue) } ?: base.close,
            show = f.obj("show")?.let { show(base.show, it) } ?: base.show,
            campaign = f.obj("campaign")?.let { campaign(base.campaign, it, issue) } ?: base.campaign,
            exitPaywall = f.string("exitPaywall")?.let {
                if (it != names.self && it in names.paywalls) it else f.wrong("exitPaywall", "the name of another paywall")
            } ?: base.exitPaywall,
            texts = f.obj("texts")?.let { texts(base.texts, it) } ?: base.texts,
            style = f.obj("style")?.let { style(base.style, it) } ?: base.style,
            showRestore = f.boolean("showRestore") ?: base.showRestore,
            showManage = f.boolean("showManage") ?: base.showManage,
            showTerms = f.boolean("showTerms") ?: base.showTerms,
            successAction = f.enum("successAction", SuccessAction.entries.toTypedArray()) ?: base.successAction,
        )
    }

    /** The plans of [array]; a plan with an unknown product or an invalid base plan is dropped. Null when none is left. */
    private fun plans(array: JSONArray, names: Names, issue: (String) -> Unit): List<PlanSpec>? {
        val plans = (0 until array.length()).mapNotNull { i ->
            val planIssue: (String) -> Unit = { issue("plan ${i + 1}: $it") }
            val json = array.optJSONObject(i) ?: return@mapNotNull null.also { planIssue("not an object; dropped") }
            plan(JsonFields(json, "", planIssue), names, planIssue)
        }
        return plans.ifEmpty { null.also { issue("\"plans\" has no valid plan; ignored") } }
    }

    private fun plan(f: JsonFields, names: Names, issue: (String) -> Unit): PlanSpec? {
        val productName = f.string("product") ?: return null.also { issue("\"product\" is missing; dropped") }
        val product = names.products[productName]
            ?: return null.also { issue("\"product\" '$productName' isn't a declared product; dropped") }
        val basePlan = f.string("basePlan")
        if (basePlan != null) {
            if (product.defaults.type != ProductType.SUBSCRIPTION) return null.also { issue("\"basePlan\" on a one-time product; dropped") }
            if (!PlanSpec.PLAY_ID.matches(basePlan)) return null.also { issue("\"basePlan\" '$basePlan' isn't a Play ID; dropped") }
        }
        val offer = f.obj("offer")?.let { o ->
            OfferRule(
                ids = o.strings("ids", PlanSpec.PLAY_ID::matches).orEmpty(),
                tags = o.strings("tags").orEmpty(),
                trialDays = o.int("trialDays", 1..Int.MAX_VALUE),
                policy = o.enum("policy", OfferPolicy.entries.toTypedArray()) ?: OfferPolicy.PREFER_FREE_TRIAL,
            )
        } ?: OfferRule()
        return PlanSpec(
            product, basePlan, offer,
            badge = f.text("badge"),
            savingsVs = f.string("savingsVs")?.let { if (PlanSpec.PLAY_ID.matches(it)) it else f.wrong("savingsVs", "a base plan ID") },
        )
    }

    private fun close(base: CloseBehavior, f: JsonFields, issue: (String) -> Unit): CloseBehavior {
        var delay = f.int("delaySeconds", CloseBehavior.NO_CLOSE_BUTTON..Int.MAX_VALUE)
        if (delay != null && delay > MAX_CLOSE_DELAY) {
            issue("\"close.delaySeconds\" is $delay; using $MAX_CLOSE_DELAY, the maximum")
            delay = MAX_CLOSE_DELAY
        }
        return CloseBehavior(
            delaySeconds = delay ?: base.delaySeconds,
            showCountdown = f.boolean("showCountdown") ?: base.showCountdown,
            position = f.enum("position", CloseButtonPosition.entries.toTypedArray()) ?: base.position,
            back = f.enum("back", BackBehavior.entries.toTypedArray()) ?: base.back,
        )
    }

    private fun show(base: ShowRules, f: JsonFields): ShowRules {
        val count = 0..Int.MAX_VALUE
        return ShowRules(
            counter = f.int("counter", count) ?: base.counter,
            timerSeconds = f.int("timerSeconds", count) ?: base.timerSeconds,
            maxPerSession = f.int("maxPerSession", count) ?: base.maxPerSession,
            maxPerDay = f.int("maxPerDay", count) ?: base.maxPerDay,
            firstAfterLaunches = f.int("firstAfterLaunches", count) ?: base.firstAfterLaunches,
            hideIfEntitled = f.strings("hideIfEntitled")?.toSet() ?: base.hideIfEntitled,
        )
    }

    private fun campaign(base: Campaign?, f: JsonFields, issue: (String) -> Unit): Campaign? {
        val id = f.string("id")?.takeIf { it.isNotBlank() } ?: base?.id
            ?: return null.also { issue("\"campaign.id\" is missing; campaign ignored") }
        val start = f.instant("start") ?: base?.startMillis
        val end = f.instant("end") ?: base?.endMillis
        if (start != null && end != null && start >= end) return null.also { issue("\"campaign\" ends before it starts; ignored") }
        return Campaign(id, start, end, f.string("analyticsId") ?: base?.analyticsId)
    }

    private fun texts(base: PaywallTexts, f: JsonFields) = PaywallTexts(
        title = f.text("title") ?: base.title,
        subtitle = f.text("subtitle") ?: base.subtitle,
        features = f.strings("features") { JsonFields.checkText(it) != null && it.isNotBlank() } ?: base.features,
        cta = f.text("cta") ?: base.cta,
        ctaTrial = f.text("ctaTrial") ?: base.ctaTrial,
        footer = f.text("footer") ?: base.footer,
    )

    private fun style(base: PaywallStyle, f: JsonFields) = PaywallStyle(
        accent = f.color("accent") ?: base.accent,
        background = f.color("background") ?: base.background,
        headerImage = f.string("headerImage")?.let { if (it.startsWith("https://")) it else f.wrong("headerImage", "an https:// URL") }
            ?: base.headerImage,
        dark = f.boolean("dark") ?: base.dark,
    )

    private fun template(name: String): PaywallTemplate? =
        PaywallTemplate.entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    /** [spec] as its complete JSON, ready for a Firebase parameter. Unset (null) values are left out. */
    fun write(spec: PaywallSpec): JSONObject = JSONObject()
        .put("enabled", spec.enabled)
        .put("template", spec.template.name.lowercase())
        .put("plans", JSONArray(spec.plans.map(::write)))
        .put("selected", spec.selected)
        .put("onOfferUnavailable", spec.onOfferUnavailable.name)
        .put(
            "close",
            JSONObject()
                .put("delaySeconds", spec.close.delaySeconds)
                .put("showCountdown", spec.close.showCountdown)
                .put("position", spec.close.position.name)
                .put("back", spec.close.back.name),
        )
        .put(
            "show",
            JSONObject()
                .put("counter", spec.show.counter)
                .put("maxPerSession", spec.show.maxPerSession)
                .put("maxPerDay", spec.show.maxPerDay)
                .put("timerSeconds", spec.show.timerSeconds)
                .put("firstAfterLaunches", spec.show.firstAfterLaunches)
                .putOpt("hideIfEntitled", spec.show.hideIfEntitled?.let { JSONArray(it.toList()) }),
        )
        .putOpt(
            "campaign",
            spec.campaign?.let {
                JSONObject()
                    .put("id", it.id)
                    .putOpt("start", it.startMillis?.let(JsonFields::formatInstant))
                    .putOpt("end", it.endMillis?.let(JsonFields::formatInstant))
                    .putOpt("analyticsId", it.analyticsId)
            },
        )
        .putOpt("exitPaywall", spec.exitPaywall)
        .put(
            "texts",
            JSONObject()
                .putOpt("title", spec.texts.title)
                .putOpt("subtitle", spec.texts.subtitle)
                .put("features", JSONArray(spec.texts.features))
                .putOpt("cta", spec.texts.cta)
                .putOpt("ctaTrial", spec.texts.ctaTrial)
                .putOpt("footer", spec.texts.footer),
        )
        .put(
            "style",
            JSONObject()
                .putOpt("accent", spec.style.accent?.let(JsonFields::formatColor))
                .putOpt("background", spec.style.background?.let(JsonFields::formatColor))
                .putOpt("headerImage", spec.style.headerImage)
                .putOpt("dark", spec.style.dark),
        )
        .put("showRestore", spec.showRestore)
        .put("showManage", spec.showManage)
        .put("showTerms", spec.showTerms)
        .put("successAction", spec.successAction.name)

    private fun write(plan: PlanSpec): JSONObject = JSONObject()
        .put("product", plan.product.name)
        .putOpt("basePlan", plan.basePlanId)
        .put(
            "offer",
            JSONObject()
                .apply { if (plan.offer.ids.isNotEmpty()) put("ids", JSONArray(plan.offer.ids)) }
                .apply { if (plan.offer.tags.isNotEmpty()) put("tags", JSONArray(plan.offer.tags)) }
                .putOpt("trialDays", plan.offer.trialDays)
                .put("policy", plan.offer.policy.name),
        )
        .putOpt("badge", plan.badge)
        .putOpt("savingsVs", plan.savingsVs)
}
