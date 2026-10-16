package io.github.thesobiaakbar.adroit.billing

/**
 * How often a paywall may show (JSON `show`), with the same counter and timer meaning as the ad profiles
 * (docs/AD_PROFILES.md). 0 turns a rule off. Immutable.
 *
 * @property counter JSON `show.counter`: 0 = off; N = the paywall shows on every Nth time the app asks.
 * @property timerSeconds JSON `show.timerSeconds`: 0 = off; the minimum time between two showings. Ignored while a
 *   counter is set.
 * @property maxPerSession JSON `show.maxPerSession`: 0 = no limit; showings per app session.
 * @property maxPerDay JSON `show.maxPerDay`: 0 = no limit; showings per calendar day.
 * @property firstAfterLaunches JSON `show.firstAfterLaunches`: 0 = from the first launch; N = not before the Nth
 *   launch of the app.
 * @property hideIfEntitled JSON `show.hideIfEntitled`: the paywall doesn't show to a user with any of these
 *   entitlements. Null uses the entitlements of the paywall's products.
 */
public class ShowRules @JvmOverloads constructor(
    public val counter: Int = 0,
    public val timerSeconds: Int = 0,
    public val maxPerSession: Int = 0,
    public val maxPerDay: Int = 0,
    public val firstAfterLaunches: Int = 0,
    public val hideIfEntitled: Set<String>? = null,
) {
    init {
        require(counter >= 0 && timerSeconds >= 0 && maxPerSession >= 0 && maxPerDay >= 0 && firstAfterLaunches >= 0) {
            "Show rules must be 0 or more: $this"
        }
    }

    private fun fields(): List<Any?> =
        listOf(counter, timerSeconds, maxPerSession, maxPerDay, firstAfterLaunches, hideIfEntitled)

    override fun equals(other: Any?): Boolean = other is ShowRules && fields() == other.fields()

    override fun hashCode(): Int = fields().hashCode()

    override fun toString(): String =
        "ShowRules(counter=$counter, timerSeconds=$timerSeconds, maxPerSession=$maxPerSession, " +
            "maxPerDay=$maxPerDay, firstAfterLaunches=$firstAfterLaunches, hideIfEntitled=$hideIfEntitled)"
}

/**
 * A promotional campaign (JSON `campaign`). The window uses the device clock, so it is for presentation only, never
 * for access. Immutable.
 *
 * @property id JSON `campaign.id`: the campaign ID, sent with purchase attribution and events.
 * @property startMillis JSON `campaign.start` (ISO-8601) as epoch milliseconds; null = already started.
 * @property endMillis JSON `campaign.end` (ISO-8601) as epoch milliseconds; null = no end.
 * @property analyticsId JSON `campaign.analyticsId`: an extra ID for analytics; null for none.
 */
public class Campaign @JvmOverloads constructor(
    public val id: String,
    public val startMillis: Long? = null,
    public val endMillis: Long? = null,
    public val analyticsId: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Campaign id must not be blank" }
        if (startMillis != null && endMillis != null) require(startMillis < endMillis) { "Campaign ends before it starts" }
    }

    private fun fields(): List<Any?> = listOf(id, startMillis, endMillis, analyticsId)

    override fun equals(other: Any?): Boolean = other is Campaign && fields() == other.fields()

    override fun hashCode(): Int = fields().hashCode()

    override fun toString(): String =
        "Campaign(id=$id, startMillis=$startMillis, endMillis=$endMillis, analyticsId=$analyticsId)"
}
