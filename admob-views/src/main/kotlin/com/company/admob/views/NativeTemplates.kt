package com.company.admob.views

import androidx.annotation.LayoutRes
import com.company.admob.NativeAdLayout

/**
 * Ready-made, brand-neutral native ad layouts built from framework widgets only. They follow the light/dark theme,
 * support RTL, include the required "Ad" attribution, and keep the top end free for the AdChoices icon.
 *
 * Restyle them by overriding `CompanyAdMob.ThemeOverlay.Native` (or the resources it references) in your app. A
 * custom layout for `AdNativeView`'s `company_admob_layout` must use the standard IDs:
 * `company_admob_native_headline` (required), `_body`, `_cta`, `_icon`, `_media` (an empty FrameLayout),
 * `_advertiser`, `_stars`, `_price`, `_store` and `_attribution`; with the other helpers, pass your own
 * [NativeAdLayout] with your IDs.
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

    /** A [NativeAdLayout] for [layoutRes] using the standard template IDs; missing views are skipped (logged once). */
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
}
