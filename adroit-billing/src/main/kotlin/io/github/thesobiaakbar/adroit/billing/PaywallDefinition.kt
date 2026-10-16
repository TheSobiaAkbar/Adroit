package io.github.thesobiaakbar.adroit.billing

/**
 * One paywall of the app, with its default settings: usually an enum entry.
 *
 * ```
 * enum class AppPaywalls(override val defaults: PaywallSpec) : PaywallDefinition {
 *     Onboarding(PaywallSpec(plans = listOf(PlanSpec(AppProducts.PremiumSub, basePlanId = "yearly")),
 *         close = CloseBehavior(delaySeconds = 3))),
 *     Settings(PaywallSpec(plans = listOf(PlanSpec(AppProducts.PremiumSub), PlanSpec(AppProducts.Lifetime)))),
 * }
 * ```
 *
 * [name] is the paywall's key: the Firebase Remote Config parameter whose JSON value overrides [defaults], field by
 * field (docs/architecture/06_BILLING_ARCHITECTURE.md §5.2). An enum supplies it: the entry's name. Register every
 * entry with `BillingSettings(paywalls = …)`.
 */
public interface PaywallDefinition {
    /** The paywall's key and Firebase parameter name: letters, digits and underscores, up to 40. */
    public val name: String

    /** The values used until, and wherever, Firebase doesn't set one. */
    public val defaults: PaywallSpec
}

/** How a paywall is laid out (JSON `template`). */
public enum class PaywallTemplate {
    /** A full screen listing every plan (`"plans"`). */
    PLANS,

    /** A full screen for one plan (`"single"`). */
    SINGLE,

    /** A sheet from the bottom of the screen (`"bottom_sheet"`). */
    BOTTOM_SHEET,

    /** A centered dialog (`"dialog"`). */
    DIALOG,
}

/** What happens to a plan whose requested offer Play doesn't give this user (JSON `onOfferUnavailable`). */
public enum class OfferFallback {
    /** Show the plan with its base price and no trial or intro text. */
    BASE_PLAN,

    /** Leave the plan out. */
    HIDE_PLAN,

    /** Don't show the paywall at all. */
    HIDE_PAYWALL,
}

/** What the paywall does after a successful purchase (JSON `successAction`). */
public enum class SuccessAction {
    /** It closes. */
    CLOSE,

    /** It stays open with a thank-you message. */
    STAY_WITH_MESSAGE,
}

/**
 * The default settings of one paywall, as written in its [PaywallDefinition]. Each property matches the field of the
 * same name in the paywall's Firebase JSON. Immutable.
 *
 * @property enabled JSON `enabled`: whether the paywall may show.
 * @property template JSON `template`: the layout.
 * @property plans JSON `plans`: the products and plans offered, in order.
 * @property selected JSON `selected`: the index of the plan selected when the paywall opens.
 * @property onOfferUnavailable JSON `onOfferUnavailable`: what happens when a plan's offer isn't available.
 * @property close JSON `close`: when and how the user can close the paywall.
 * @property show JSON `show`: how often the paywall may show.
 * @property campaign JSON `campaign`: an optional campaign window and analytics ID.
 * @property exitPaywall JSON `exitPaywall`: the name of the paywall shown when the user closes this one, at most once
 *   per session; null for none.
 * @property texts JSON `texts`: the paywall's texts. Null texts use the library's.
 * @property style JSON `style`: colors and header image. Null values use the library's.
 * @property showRestore JSON `showRestore`: whether a "Restore purchases" action is shown.
 * @property showManage JSON `showManage`: whether a "Manage subscription" action is shown.
 * @property showTerms JSON `showTerms`: whether the terms and privacy links are shown.
 * @property successAction JSON `successAction`: what happens after a successful purchase.
 */
public class PaywallSpec @JvmOverloads constructor(
    public val plans: List<PlanSpec>,
    public val enabled: Boolean = true,
    public val template: PaywallTemplate = PaywallTemplate.PLANS,
    public val selected: Int = 0,
    public val onOfferUnavailable: OfferFallback = OfferFallback.BASE_PLAN,
    public val close: CloseBehavior = CloseBehavior(),
    public val show: ShowRules = ShowRules(),
    public val campaign: Campaign? = null,
    public val exitPaywall: String? = null,
    public val texts: PaywallTexts = PaywallTexts(),
    public val style: PaywallStyle = PaywallStyle(),
    public val showRestore: Boolean = true,
    public val showManage: Boolean = false,
    public val showTerms: Boolean = true,
    public val successAction: SuccessAction = SuccessAction.CLOSE,
) {
    init {
        require(selected >= 0) { "selected must be 0 or more, was $selected" }
    }

    private fun fields(): List<Any?> = listOf(
        plans, enabled, template, selected, onOfferUnavailable, close, show, campaign, exitPaywall, texts, style,
        showRestore, showManage, showTerms, successAction,
    )

    override fun equals(other: Any?): Boolean = other is PaywallSpec && fields() == other.fields()

    override fun hashCode(): Int = fields().hashCode()

    override fun toString(): String =
        "PaywallSpec(plans=$plans, enabled=$enabled, template=$template, selected=$selected, " +
            "onOfferUnavailable=$onOfferUnavailable, close=$close, show=$show, campaign=$campaign, " +
            "exitPaywall=$exitPaywall, texts=$texts, style=$style, showRestore=$showRestore, " +
            "showManage=$showManage, showTerms=$showTerms, successAction=$successAction)"
}

/**
 * One plan on a paywall (an element of JSON `plans`).
 *
 * @property product JSON `product`: the product, by its [ProductDefinition.name].
 * @property basePlanId JSON `basePlan`: the subscription's base plan ID in Play Console, for example `"monthly"`.
 *   Null takes the first base plan Play returns. Must be null for a one-time product.
 * @property offer JSON `offer`: how the offer is chosen among those Play gives this user.
 * @property badge JSON `badge`: a short label on the plan, for example `"BEST_VALUE"`; null for none.
 * @property savingsVs JSON `savingsVs`: the base plan ID this plan's savings are computed against (shown only when
 *   Play's prices allow it); null for none.
 */
public class PlanSpec @JvmOverloads constructor(
    public val product: ProductDefinition,
    public val basePlanId: String? = null,
    public val offer: OfferRule = OfferRule(),
    public val badge: String? = null,
    public val savingsVs: String? = null,
) {
    init {
        basePlanId?.let { require(PLAY_ID.matches(it)) { "Invalid base plan ID '$it'" } }
        savingsVs?.let { require(PLAY_ID.matches(it)) { "Invalid savingsVs base plan ID '$it'" } }
    }

    private fun fields(): List<Any?> = listOf(product, basePlanId, offer, badge, savingsVs)

    override fun equals(other: Any?): Boolean = other is PlanSpec && fields() == other.fields()

    override fun hashCode(): Int = fields().hashCode()

    override fun toString(): String =
        "PlanSpec(product=${product.name}, basePlanId=$basePlanId, offer=$offer, badge=$badge, savingsVs=$savingsVs)"

    internal companion object {
        /** Play's rule for base plan and offer IDs. */
        val PLAY_ID = Regex("[a-z0-9][a-z0-9-]{0,62}")
    }
}

/** The order in which an offer is preferred when a plan's [OfferRule] names none (JSON `offer.policy`). */
public enum class OfferPolicy {
    /** An offer with a free trial, if Play gives this user one; else the base plan. */
    PREFER_FREE_TRIAL,

    /** An offer with a paid introductory price, if Play gives this user one; else the base plan. */
    PREFER_INTRO_PRICE,

    /** The offer with the lowest first payment (a free trial counts as 0). */
    LOWEST_FIRST_PAYMENT,

    /** Always the base plan, never an offer. */
    BASE_ONLY,
}

/**
 * How a plan's offer is chosen among the offers Play gives this user (JSON `offer`). The first rule that matches
 * wins: [ids], then [tags], then [trialDays], then [policy]. Firebase can only choose among Play's offers; it can't
 * create a trial or a price.
 *
 * @property ids JSON `ids`: offer IDs, in order of preference.
 * @property tags JSON `tags`: an offer with all of these Play offer tags.
 * @property trialDays JSON `trialDays`: an offer whose free trial lasts exactly this many days; null for any.
 * @property policy JSON `policy`: the preference when nothing above matches.
 */
public class OfferRule @JvmOverloads constructor(
    public val ids: List<String> = emptyList(),
    public val tags: List<String> = emptyList(),
    public val trialDays: Int? = null,
    public val policy: OfferPolicy = OfferPolicy.PREFER_FREE_TRIAL,
) {
    init {
        trialDays?.let { require(it > 0) { "trialDays must be positive, was $it" } }
    }

    private fun fields(): List<Any?> = listOf(ids, tags, trialDays, policy)

    override fun equals(other: Any?): Boolean = other is OfferRule && fields() == other.fields()

    override fun hashCode(): Int = fields().hashCode()

    override fun toString(): String = "OfferRule(ids=$ids, tags=$tags, trialDays=$trialDays, policy=$policy)"
}
