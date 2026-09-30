package io.github.thesobiaakbar.adroit.events

/**
 * Impression-level revenue for one ad impression.
 *
 * @property valueMicros revenue in micro-units of [currencyCode] (1,000,000 micros = 1 unit).
 * @property currencyCode ISO 4217 currency code, for example `"USD"`.
 * @property precision how exact the value is.
 */
public data class AdRevenue(
    public val valueMicros: Long,
    public val currencyCode: String,
    public val precision: RevenuePrecision,
)

/**
 * How exact an [AdRevenue] value is.
 *
 * Stability: new values may be added in minor versions; include an `else` branch when matching exhaustively.
 */
public enum class RevenuePrecision {
    UNKNOWN,
    ESTIMATED,
    PUBLISHER_PROVIDED,
    PRECISE,
}
