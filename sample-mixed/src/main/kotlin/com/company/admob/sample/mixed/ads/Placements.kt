package com.company.admob.sample.mixed.ads

import com.company.admob.BannerPlacement
import com.company.admob.InterstitialPlacement
import com.company.admob.NativePlacement

/** Every place this app shows an ad. The keys match the XML attributes. */
object Placements {
    /** The XML `AdBannerView` in [com.company.admob.sample.mixed.LegacyXmlActivity]. */
    val LegacyBanner = BannerPlacement("mixed_legacy_banner")

    /** `AdNative` in a `ComposeView` inside the XML screen. */
    val ComposeInXmlNative = NativePlacement("mixed_compose_in_xml_native")

    /** The XML `AdNativeView` inside an `AndroidView` of the Compose screen. */
    val XmlInComposeNative = NativePlacement("mixed_xml_in_compose_native")

    val Interstitial = InterstitialPlacement("mixed_interstitial")
}
