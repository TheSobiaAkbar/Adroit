package io.github.thesobiaakbar.adroit.billing

/**
 * A paywall's texts (JSON `texts`). Null uses the library's localized text. Texts may contain `{trial}`, `{price}`,
 * `{period}`, `{renewalPrice}` and `{savings}`, filled from Play's data only; a text that needs a value Play didn't
 * give (for example `{trial}` without a free trial) is replaced by the library's text. Immutable.
 *
 * @property title JSON `texts.title`.
 * @property subtitle JSON `texts.subtitle`.
 * @property features JSON `texts.features`: the list of benefits.
 * @property cta JSON `texts.cta`: the purchase button.
 * @property ctaTrial JSON `texts.ctaTrial`: the purchase button when the selected plan has a free trial.
 * @property footer JSON `texts.footer`: shown above the renewal terms, which are always shown.
 */
public class PaywallTexts @JvmOverloads constructor(
    public val title: String? = null,
    public val subtitle: String? = null,
    public val features: List<String> = emptyList(),
    public val cta: String? = null,
    public val ctaTrial: String? = null,
    public val footer: String? = null,
) {
    private fun fields(): List<Any?> = listOf(title, subtitle, features, cta, ctaTrial, footer)

    override fun equals(other: Any?): Boolean = other is PaywallTexts && fields() == other.fields()

    override fun hashCode(): Int = fields().hashCode()

    override fun toString(): String =
        "PaywallTexts(title=$title, subtitle=$subtitle, features=$features, cta=$cta, ctaTrial=$ctaTrial, " +
            "footer=$footer)"
}

/**
 * A paywall's look (JSON `style`). Null uses the library's theme. Immutable.
 *
 * @property accent JSON `style.accent` (`#RRGGBB` or `#AARRGGBB`) as an ARGB color.
 * @property background JSON `style.background` as an ARGB color.
 * @property headerImage JSON `style.headerImage`: an `https://` image URL, shown when the UI has an image loader.
 * @property dark JSON `style.dark`: true for light text on a dark background; null follows the system.
 */
public class PaywallStyle @JvmOverloads constructor(
    public val accent: Int? = null,
    public val background: Int? = null,
    public val headerImage: String? = null,
    public val dark: Boolean? = null,
) {
    init {
        headerImage?.let { require(it.startsWith("https://")) { "headerImage must be an https:// URL" } }
    }

    private fun fields(): List<Any?> = listOf(accent, background, headerImage, dark)

    override fun equals(other: Any?): Boolean = other is PaywallStyle && fields() == other.fields()

    override fun hashCode(): Int = fields().hashCode()

    override fun toString(): String =
        "PaywallStyle(accent=${accent?.let(::hex)}, background=${background?.let(::hex)}, " +
            "headerImage=$headerImage, dark=$dark)"

    private fun hex(color: Int): String = "#%08X".format(color)
}
