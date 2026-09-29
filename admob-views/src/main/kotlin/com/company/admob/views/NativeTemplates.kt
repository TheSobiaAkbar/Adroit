package com.company.admob.views

import androidx.annotation.LayoutRes
import com.company.admob.NativeAdLayout
import com.company.admob.NativePlacement
import com.company.admob.NativeStyle
import com.company.admob.views.internal.ViewsRuntime
import java.util.concurrent.ConcurrentHashMap

/**
 * Ready-made native ad layouts, one per [NativeStyle], and the app's own layouts registered by name.
 *
 * The templates are built from framework widgets only. They follow the light and dark themes, support RTL, include
 * the required "Ad" attribution, and keep a top corner free for the AdChoices icon. Restyle them by overriding their
 * colors, dimens and drawables in your app (docs/NATIVE.md, "Styling"), for example
 * `<color name="company_admob_native_cta">#FF6D00</color>`.
 *
 * An app layout uses the standard IDs: `company_admob_native_headline` (required), `_body`, `_cta`, `_icon`,
 * `_media` (an empty FrameLayout), `_advertiser`, `_stars`, `_price`, `_store` and `_attribution`. Register it with
 * [register] so an ad profile or Firebase can pick it by name (`"style": "promo"`), or pass it to a view as
 * `company_admob_layout`. With a [NativeAdLayout] of your own IDs, register that instead.
 */
public object NativeTemplates {
    /** Icon, headline and call to action in one row. */
    public val Small: NativeAdLayout = NativeAdLayout(
        layoutRes = R.layout.company_admob_native_small,
        headlineId = R.id.company_admob_native_headline,
        callToActionId = R.id.company_admob_native_cta,
        iconId = R.id.company_admob_native_icon,
        attributionId = R.id.company_admob_native_attribution,
    )

    /** Header, body, 16:9 media, store, price and call to action. */
    public val Medium: NativeAdLayout = forLayout(R.layout.company_admob_native_medium)

    /** Full-width media first, then every asset and a full-width call to action. */
    public val Large: NativeAdLayout = forLayout(R.layout.company_admob_native_large)

    /** Icon, headline and body, with a full-width call to action below. No media. */
    public val SmallButtonBottom: NativeAdLayout = basic(R.layout.company_admob_native_small_button_bottom, media = false)

    /** A full-width call to action, with the icon, headline and body below it. No media. */
    public val SmallButtonTop: NativeAdLayout = basic(R.layout.company_admob_native_small_button_top, media = false)

    /** Icon, headline and body, with the call to action at the end of the row. No media. */
    public val SmallButtonEnd: NativeAdLayout = basic(R.layout.company_admob_native_small_button_end, media = false)

    /** Icon, headline and body, then full-width media and a full-width call to action. */
    public val LargeButtonBottom: NativeAdLayout = basic(R.layout.company_admob_native_large_button_bottom, media = true)

    /** A full-width call to action, then the icon, headline and body, then full-width media. */
    public val LargeButtonTop: NativeAdLayout = basic(R.layout.company_admob_native_large_button_top, media = true)

    /** Like [LargeButtonBottom], with a larger icon and shorter media. */
    public val LargeCompact: NativeAdLayout = basic(R.layout.company_admob_native_large_compact, media = true)

    /** Icon, headline and body beside the media, with a full-width call to action below. */
    public val SideMediaButtonBottom: NativeAdLayout =
        basic(R.layout.company_admob_native_side_media_button_bottom, media = true)

    /** A full-width call to action, with the icon, headline and body beside the media below it. */
    public val SideMediaButtonTop: NativeAdLayout =
        basic(R.layout.company_admob_native_side_media_button_top, media = true)

    private val registered = ConcurrentHashMap<String, NativeAdLayout>()
    private val warnedNames = ConcurrentHashMap.newKeySet<String>()

    /** The template for [style]. */
    public fun forStyle(style: NativeStyle): NativeAdLayout = when (style) {
        NativeStyle.SMALL -> Small
        NativeStyle.MEDIUM -> Medium
        NativeStyle.LARGE -> Large
        NativeStyle.SMALL_BUTTON_BOTTOM -> SmallButtonBottom
        NativeStyle.SMALL_BUTTON_TOP -> SmallButtonTop
        NativeStyle.SMALL_BUTTON_END -> SmallButtonEnd
        NativeStyle.LARGE_BUTTON_BOTTOM -> LargeButtonBottom
        NativeStyle.LARGE_BUTTON_TOP -> LargeButtonTop
        NativeStyle.LARGE_COMPACT -> LargeCompact
        NativeStyle.SIDE_MEDIA_BUTTON_BOTTOM -> SideMediaButtonBottom
        NativeStyle.SIDE_MEDIA_BUTTON_TOP -> SideMediaButtonTop
    }

    /**
     * Registers the app's own [layout] under [name] (letters, digits and underscores; case doesn't matter), so an ad
     * profile (`AdProfile.native(layout = "promo")`) or a Firebase value (`{"style": "promo"}`) can show native ads
     * in it. Register every layout in `Application.onCreate`, before any native ad shows. Registering a name again
     * replaces its layout. Any thread.
     *
     * @throws IllegalArgumentException if [name] isn't a valid name, or is a library style's name (`small`, ...).
     */
    @JvmStatic
    public fun register(name: String, layout: NativeAdLayout) {
        val key = name.trim().lowercase()
        require(NAME.matches(key)) { "A native layout name must be 1 to 64 letters, digits or underscores: \"$name\"" }
        require(NativeStyle.entries.none { it.name.lowercase() == key }) {
            "\"$name\" is a library style's name; pick another name for your layout"
        }
        registered[key] = layout
    }

    /** Registers [layoutRes], which uses the standard IDs (see the class description), under [name]. See [register]. */
    @JvmStatic
    public fun register(name: String, @LayoutRes layoutRes: Int) {
        register(name, forLayout(layoutRes))
    }

    /** The layout registered under [name], or null. */
    @JvmStatic
    public fun registered(name: String): NativeAdLayout? = registered[name.trim().lowercase()]

    /**
     * The layout [placement]'s ads show in now: the app layout its ad profile or Firebase names, if registered;
     * otherwise the template of its style. This is what every view and composable uses when the code passes no
     * layout. An unregistered name is logged once and shows the style's template. Main thread.
     */
    @JvmStatic
    public fun forPlacement(placement: NativePlacement): NativeAdLayout {
        val name = ViewsRuntime.nativeLayoutName(placement.key)
        if (name != null) {
            registered[name]?.let { return it }
            if (warnedNames.add(name)) {
                ViewsRuntime.warn(
                    "Native ad ${placement.key}: no layout is registered as \"$name\"; showing its style's template. " +
                        "Register it with NativeTemplates.register(\"$name\", R.layout.…) in Application.onCreate",
                )
            }
        }
        return forStyle(ViewsRuntime.client.native.style(placement))
    }

    /** A [NativeAdLayout] for [layoutRes] using every standard template ID; missing views are skipped (logged once). */
    internal fun forLayout(@LayoutRes layoutRes: Int): NativeAdLayout = NativeAdLayout(
        layoutRes = layoutRes,
        headlineId = R.id.company_admob_native_headline,
        bodyId = R.id.company_admob_native_body,
        callToActionId = R.id.company_admob_native_cta,
        iconId = R.id.company_admob_native_icon,
        mediaContainerId = R.id.company_admob_native_media,
        advertiserId = R.id.company_admob_native_advertiser,
        starRatingId = R.id.company_admob_native_stars,
        priceId = R.id.company_admob_native_price,
        storeId = R.id.company_admob_native_store,
        attributionId = R.id.company_admob_native_attribution,
    )

    /** The new templates: headline, body, call to action, icon and attribution, with or without media. */
    private fun basic(@LayoutRes layoutRes: Int, media: Boolean): NativeAdLayout = NativeAdLayout(
        layoutRes = layoutRes,
        headlineId = R.id.company_admob_native_headline,
        bodyId = R.id.company_admob_native_body,
        callToActionId = R.id.company_admob_native_cta,
        iconId = R.id.company_admob_native_icon,
        mediaContainerId = if (media) R.id.company_admob_native_media else 0,
        attributionId = R.id.company_admob_native_attribution,
    )

    private val NAME = Regex("[a-z0-9_]{1,64}")
}
