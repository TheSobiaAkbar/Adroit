# Native ads

## When to use them

A native ad is built from assets (headline, body, icon, media, call to action) rendered in a layout that matches your
app. Use it inside content and in lists ([Google](https://developers.google.com/admob/android/next-gen/native)).

## Integration

### Compose

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/Native.kt#compose-native -->
```kotlin
@Composable
fun ArticleFooterAd() {
    // Kept by the screen's ViewModelStoreOwner: recomposition and rotation re-render the same ad.
    AdNative(
        placement = Placements.FeedNative,
        modifier = Modifier.fillMaxWidth(),
        layout = NativeTemplates.Medium,
    )
}
```

In a `LazyColumn`, give each ad item a distinct, stable `slotKey`:

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/Native.kt#compose-feed -->
```kotlin
@Composable
fun Feed(articles: List<String>) {
    LazyColumn {
        articles.forEachIndexed { index, article ->
            item(key = article) { Text(article) }
            if (index % 6 == 5) {
                val slot = "ad-${index / 6}"
                // A distinct, stable slotKey per ad item: scrolling away and back doesn't reload it.
                item(key = slot) { AdNative(Placements.FeedNative, layout = NativeTemplates.Small, slotKey = slot) }
            }
        }
    }
}
```

For your own Compose rendering of the assets, `rememberNativeAd` gives you the loaded handle. Clicks and impressions
are only recorded when the ad is rendered by the library (`AdNative`, the views helpers or `native.render`), so use
this for extra information, not instead of rendering:

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/Native.kt#compose-remember -->
```kotlin
@Composable
fun SponsoredHeadline() {
    val result by rememberNativeAd(Placements.FeedNative)
    when (val ad = result) {
        null -> Text("Loading…")
        is NativeAdResult.Loaded -> Text("Sponsored: ${ad.handle.assets.headline}")
        is NativeAdResult.Failed -> Unit // show nothing
    }
}
```

### XML

<!-- snippet: docs-snippets/src/main/res/layout/snippet_banner.xml#xml-native -->
```xml
<com.company.admob.views.AdNativeView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:company_admob_placement="feed_native"
    app:company_admob_template="medium" />
```

### A container from code

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/Native.kt#views-attach -->
```kotlin
class ArticleFragment : Fragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<FrameLayout>(R.id.banner_container)
            .attachNativeAd(viewLifecycleOwner, Placements.FeedNative, NativeTemplates.Large)
    }
}
```

### RecyclerView

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/Native.kt#views-holder -->
```kotlin
/** RecyclerView: one holder per ad position, kept in the ViewModel so scrolling and rotation don't reload. */
class FeedViewModel : ViewModel() {
    private val holders = mutableMapOf<Int, NativeAdHolder>()

    fun adHolder(slot: Int): NativeAdHolder = holders.getOrPut(slot) { NativeAdHolder(Placements.FeedNative) }

    override fun onCleared() {
        holders.values.forEach(NativeAdHolder::clear) // destroys the ads
    }
}

// In the adapter's onBindViewHolder, for an ad row:
fun bindAdRow(viewModel: FeedViewModel, slot: Int, container: ViewGroup, viewLifecycleOwner: LifecycleOwner) {
    viewModel.adHolder(slot).bindTo(container, viewLifecycleOwner, NativeTemplates.Small)
}
```

## Templates and custom layouts

The library has one template per style:

| Style (`NativeStyle`, Firebase name) | Template (`NativeTemplates`) | Layout |
|---|---|---|
| `SMALL`, `small` | `Small` | Icon, "Ad" badge and headline, call to action at the end. One row. |
| `MEDIUM`, `medium` (default) | `Medium` | Header (icon, headline, advertiser, rating), body, 16:9 media, store, price and call to action. |
| `LARGE`, `large` | `Large` | Full-width media first, then every asset and a full-width call to action. |
| `SMALL_BUTTON_BOTTOM`, `small_button_bottom` | `SmallButtonBottom` | Icon, badge, headline and body, full-width call to action below. No media. |
| `SMALL_BUTTON_TOP`, `small_button_top` | `SmallButtonTop` | Full-width call to action on top, then icon, headline and body. No media. |
| `SMALL_BUTTON_END`, `small_button_end` | `SmallButtonEnd` | Icon, badge, headline and body, call to action at the end of the row. No media. |
| `LARGE_BUTTON_BOTTOM`, `large_button_bottom` | `LargeButtonBottom` | Icon, badge, headline and body, then full-width media and call to action. |
| `LARGE_BUTTON_TOP`, `large_button_top` | `LargeButtonTop` | Call to action on top, then icon, headline and body, then full-width media. |
| `LARGE_COMPACT`, `large_compact` | `LargeCompact` | Like `large_button_bottom`, with a larger icon and shorter (120dp) media. |
| `SIDE_MEDIA_BUTTON_BOTTOM`, `side_media_button_bottom` | `SideMediaButtonBottom` | Icon, headline and body beside the media, call to action below. |
| `SIDE_MEDIA_BUTTON_TOP`, `side_media_button_top` | `SideMediaButtonTop` | Call to action on top, then icon, headline and body beside the media. |

They use framework widgets only, follow the light and dark themes, support RTL, include the required "Ad" badge,
and keep a top corner free for the AdChoices icon.

When the code passes no layout (`AdSlotView`, `AdNativeView` without `company_admob_template`, `attachNativeAd`,
`NativeAdHolder.bindTo`, `AdSlot`, `AdNative`), an ad shows in the layout its `style` names now: a style's template,
or [your own registered layout](#your-own-layout-picked-by-name). The configuration, the
[ad profile](AD_PROFILES.md) or Firebase (`{"style": "side_media_button_top"}`) sets it, so it can change without a
release. `NativeTemplates.forPlacement(placement)` returns that layout, and `NativeTemplates.forStyle(style)` a style's
template.

### Styling

Every color, size and shape of the templates is a resource. To change one, declare a resource with the same name in
your app (for example in `res/values/ads_style.xml`); yours replaces the library's, in every template:

```xml
<resources>
    <color name="company_admob_native_attribution">#FFC107</color>      <!-- the "Ad" badge -->
    <color name="company_admob_native_attribution_text">#FFFFFF</color>
    <color name="company_admob_native_cta">#FF6D00</color>              <!-- the button -->
    <color name="company_admob_native_cta_text">#FFFFFF</color>
    <dimen name="company_admob_native_cta_corner_radius">22dp</dimen>
</resources>
```

| Resource | Default | What |
|---|---|---|
| `color/company_admob_native_background` | transparent | The card's fill |
| `color/company_admob_native_stroke` | the text color at 12% | The card's border |
| `color/company_admob_native_headline` | `?android:textColorPrimary` | Headline text |
| `color/company_admob_native_body` | `?android:textColorSecondary` | Body, advertiser, store and price text |
| `color/company_admob_native_cta` | `?android:colorAccent` | The call-to-action button |
| `color/company_admob_native_cta_text` | `?android:textColorPrimaryInverse` | Its text |
| `color/company_admob_native_attribution` | `?android:colorAccent` | The "Ad" badge |
| `color/company_admob_native_attribution_text` | `?android:textColorPrimaryInverse` | Its text |
| `dimen/company_admob_native_card_corner_radius`, `_card_stroke_width` | 8dp, 1dp | The card's corners and border |
| `dimen/company_admob_native_cta_corner_radius`, `_attribution_corner_radius` | 6dp, 2dp | The button's and the badge's corners |
| `dimen/company_admob_native_headline_text_size`, `_body_text_size`, `_caption_text_size`, `_cta_text_size`, `_attribution_text_size` | 15, 13, 12, 14, 10sp | Text sizes |
| `dimen/company_admob_native_icon_size`, `_icon_size_large` | 40dp, 48dp | The icon |
| `dimen/company_admob_native_media_height`, `_media_height_compact` | 160dp, 120dp | Media in the new large and side-media styles. Keep it at least 120dp, Google's minimum for video. |
| `dimen/company_admob_native_spacing`, `_spacing_small`, `_spacing_large` | 8, 4, 16dp | Padding and gaps |
| `drawable/company_admob_native_card` | a rounded card with a border | The whole background shape |
| `drawable/company_admob_native_cta_background` | a rounded button with a ripple | The whole button shape |
| `drawable/company_admob_native_attribution_background` | a rounded rectangle | The whole badge shape |
| `string/company_admob_ad_attribution` | `Ad` | The badge text: translate it in `values-xx` |

Replace a drawable to change a shape itself, for example a gradient button or a badge with a border. The colors
follow the light and dark themes until you override them; to override them per theme, put yours in `values` and
`values-night`. Overriding the `CompanyAdMob.ThemeOverlay.Native` style replaces its items all at once.

### Your own layout, picked by name

Any layout can show native ads. Write it with ordinary views only: no `NativeAdView` and no `MediaView`, which the
library adds itself. Then either give its views the standard IDs, or map your own IDs with a `NativeAdLayout`:

| Standard ID | View | |
|---|---|---|
| `@id/company_admob_native_headline` | `TextView` | Required |
| `@id/company_admob_native_body` | `TextView` | |
| `@id/company_admob_native_cta` | `Button` (write `<android.widget.Button>`: see below) | |
| `@id/company_admob_native_icon` | `ImageView` | |
| `@id/company_admob_native_media` | an empty `FrameLayout`, at least 120dp tall | The image or video goes inside |
| `@id/company_admob_native_attribution` | `TextView` saying "Ad" | Required by Google's policy |
| `@id/company_admob_native_advertiser`, `_stars` (a `RatingBar`), `_price`, `_store` | | |

Views whose asset an ad lacks are hidden. Keep a top corner free (about 24dp) for the AdChoices icon the SDK adds.
In an app with a Material theme, a plain `<Button>` becomes a `MaterialButton`, which paints the theme's color over
your background: write `<android.widget.Button>` to keep yours.

Register the layout under a name in `Application.onCreate`. An ad profile can then use it by default, and Firebase
can switch any native ad to it, or back to a library style, with `"style"`:

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/Native.kt#native-register -->
```kotlin
class PromoAdsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Your own layout under a name: ad profiles and Firebase can now pick it with "style": "promo".
        NativeTemplates.register("promo", ArticleAdLayout)
        // Or, for a layout using the standard IDs: NativeTemplates.register("promo", R.layout.my_native_ad)
    }
}

/** An ad shown in the registered layout by default; Firebase can still switch it to any style or layout. */
enum class PromoAds(override val defaults: AdProfile) : AdDefinition {
    PromoNative(AdProfile.native(layout = "promo")),
}
```

In XML, `app:company_admob_layout="@layout/my_native_ad"` on an `AdNativeView` shows that layout (standard IDs only)
whatever the style. A name Firebase gives that the app hasn't registered is logged once, and the ad shows its style's
template.

### The shimmer

While a native ad loads (with `showLoading`, the default), its place shows a shimmer made from the layout it will
show in: the library inflates that layout without drawing it, and draws the whole card, then a block over each view
inside it: one per line of text, one per icon, badge, button and media area, at their real positions and sizes. A
highlight sweeps across the card and the blocks together. So every template and every layout of your own gets a
shimmer of the ad's own shape and size, with nothing to write. A banner's shimmer is its whole area, with blocks
like a banner's content. Its colors are `company_admob_shimmer_card`, `company_admob_shimmer_base` and
`company_admob_shimmer_highlight`, with night variants; override them like the others.

### A layout from code

For your own layout, map its views with a `NativeAdLayout`, and style them with a `NativeAdRenderer`:

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/Native.kt#custom-layout -->
```kotlin
/** Your own layout: map its view IDs. Keep an "Ad" attribution label and room for the AdChoices icon. */
val ArticleAdLayout = NativeAdLayout(
    layoutRes = R.layout.snippet_native_ad,
    headlineId = R.id.ad_headline,
    bodyId = R.id.ad_body,
    callToActionId = R.id.ad_cta,
    iconId = R.id.ad_icon,
    attributionId = R.id.ad_attribution,
)

/** Styling after the standard binding. Never set click listeners: the SDK handles clicks. */
val ArticleAdRenderer = NativeAdRenderer { root, assets ->
    root.findViewById<TextView>(R.id.ad_headline).setTypeface(null, Typeface.BOLD)
    root.findViewById<TextView>(R.id.ad_body).visibility = if (assets.body == null) View.GONE else View.VISIBLE
}

@Composable
fun CustomNativeAd() {
    AdNative(Placements.FeedNative, layout = ArticleAdLayout, renderer = ArticleAdRenderer)
}
```

Only `headlineId` is required. The library wraps your layout in Google's `NativeAdView`, fills and registers the
views, hides views whose asset is missing, and creates the media view inside `mediaContainerId` (an empty
`FrameLayout`).

## Lifecycle and memory

- A native ad must be destroyed when it's no longer shown; the library does it for you: `AdNative` when the screen's
  ViewModel is cleared, `attachNativeAd` and `AdNativeView` when their lifecycle owner is destroyed, `NativeAdHolder`
  on `clear()`. If you call `native.load` yourself, you own the handle: call `destroy()`.
- `AdNative` and `rememberNativeAd` keep at most 3 ads per screen; beyond that the least recently used one is
  destroyed, preferring slots not on screen.
- Rotation: `AdNative`, `rememberNativeAd` and `NativeAdHolder` re-render the same ad. `AdNativeView` and
  `attachNativeAd` load a new one; use a holder where that matters.
- Each placement keeps a small pool (`poolSize`, default 1, at most 3). Don't cache more ads than you're about to show.

## State and events

`NativeAdHandle.state`: `Active`, `Blocked(reason)` (ad-free, consent, remote) or `Destroyed`. Events: `LoadRequested`,
`Loaded`, `LoadFailed`, `Impression`, `Clicked`, `Paid`, `Expired` and `Destroyed`. Failed loads retry after 10 s,
doubling up to 120 s, 3 attempts; a pooled ad expires after 55 minutes.

## Policy notes

Google's native ad requirements ([native policies](https://support.google.com/admob/answer/6329638),
[native ads guide](https://developers.google.com/admob/android/next-gen/native/advanced)):

- **Ad attribution:** the ad must be labelled as an ad. The templates include an "Ad" label; keep one in custom
  layouts (`attributionId`).
- **AdChoices:** the SDK adds the AdChoices icon in a corner. Leave space for it (the templates keep the top end free).
- **No custom click handlers** on the ad or its asset views: the SDK handles clicks. A `NativeAdRenderer` styles
  views only.
- **Video** needs hardware acceleration, on by default
  ([Google](https://developers.google.com/admob/android/next-gen/banner#hardware_acceleration_for_video_ads)).

## FAQ

**Every rotation loads a new ad.** Use `AdNative`, or a `NativeAdHolder` in a ViewModel, instead of `AdNativeView`.

**Logcat warns about retained native ads, or memory grows.** Something holds handles without destroying them: see
[Troubleshooting](TROUBLESHOOTING.md#memory-warnings-with-native-ads).

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-29
