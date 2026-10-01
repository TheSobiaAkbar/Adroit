# Native ads

## When to use them

A native ad is built from assets (headline, body, icon, media, call to action) rendered in a layout that matches your
app. Use it inside content and in lists ([Google](https://developers.google.com/admob/android/next-gen/native)).

## Integration

### Compose

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/Native.kt#compose-native -->
```kotlin
@Composable
fun ArticleFooterAd() {
    // Kept by the screen's ViewModelStoreOwner: recomposition and rotation re-render the same ad.
    AdNative(
        placement = Placements.FeedNative,
        modifier = Modifier.fillMaxWidth(),
        layout = NativeTemplates.LargeButtonBottom,
    )
}
```

In a `LazyColumn`, give each ad item a distinct, stable `slotKey`:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/Native.kt#compose-feed -->
```kotlin
@Composable
fun Feed(articles: List<String>) {
    LazyColumn {
        articles.forEachIndexed { index, article ->
            item(key = article) { Text(article) }
            if (index % 6 == 5) {
                val slot = "ad-${index / 6}"
                // A distinct, stable slotKey per ad item: scrolling away and back doesn't reload it.
                item(key = slot) { AdNative(Placements.FeedNative, layout = NativeTemplates.SmallButtonEnd, slotKey = slot) }
            }
        }
    }
}
```

For your own Compose rendering of the assets, `rememberNativeAd` gives you the loaded handle. Clicks and impressions
are only recorded when the ad is rendered by the library (`AdNative`, the views helpers or `native.render`), so use
this for extra information, not instead of rendering:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/Native.kt#compose-remember -->
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
<io.github.thesobiaakbar.adroit.views.AdNativeView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:adroit_placement="feed_native"
    app:adroit_template="large_button_bottom" />
```

### A container from code

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/Native.kt#views-attach -->
```kotlin
class ArticleFragment : Fragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<FrameLayout>(R.id.banner_container)
            .attachNativeAd(viewLifecycleOwner, Placements.FeedNative, NativeTemplates.LargeCompact)
    }
}
```

### RecyclerView

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/Native.kt#views-holder -->
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
    viewModel.adHolder(slot).bindTo(container, viewLifecycleOwner, NativeTemplates.SmallButtonEnd)
}
```

## Templates and custom layouts

The library has one template per style:

| Style (`NativeStyle`, Firebase name) | Template (`NativeTemplates`) | Layout |
|---|---|---|
| `SMALL_BUTTON_END`, `small_button_end` | `SmallButtonEnd` | Icon, "Ad" badge, headline and body, call to action at the end of the row. No media. |
| `SMALL_BUTTON_BOTTOM`, `small_button_bottom` | `SmallButtonBottom` | Icon, badge, headline and body, full-width call to action below. No media. |
| `LARGE_BUTTON_BOTTOM`, `large_button_bottom` (default) | `LargeButtonBottom` | Icon, badge, headline and body, then full-width media and call to action. |
| `LARGE_COMPACT`, `large_compact` | `LargeCompact` | Like `large_button_bottom`, with a larger icon and shorter (120dp) media. |
| `MEDIA_START`, `media_start` | `MediaStart` | Media at the start; badge, headline, body and call to action beside it. No icon. |

They use framework widgets only, follow the light and dark themes, support RTL, include the required "Ad" badge,
and keep a top corner free for the AdChoices icon.

When the code passes no layout (`AdSlotView`, `AdNativeView` without `adroit_template`, `attachNativeAd`,
`NativeAdHolder.bindTo`, `AdSlot`, `AdNative`), an ad shows in the layout its `style` names now: a style's template,
or [your own registered layout](#your-own-layout-picked-by-name). The configuration, the
[ad profile](AD_PROFILES.md) or Firebase (`{"style": "media_start"}`) sets it, so it can change without a
release. `NativeTemplates.forPlacement(placement)` returns that layout, and `NativeTemplates.forStyle(style)` a style's
template.

### Styling

Every color, size and shape of the templates is a resource. To change one, declare a resource with the same name in
your app (for example in `res/values/ads_style.xml`); yours replaces the library's, in every template:

```xml
<resources>
    <color name="adroit_native_attribution">#FFC107</color>      <!-- the "Ad" badge -->
    <color name="adroit_native_attribution_text">#FFFFFF</color>
    <color name="adroit_native_cta">#FF6D00</color>              <!-- the button -->
    <color name="adroit_native_cta_text">#FFFFFF</color>
    <dimen name="adroit_native_cta_corner_radius">22dp</dimen>
</resources>
```

| Resource | Default | What |
|---|---|---|
| `color/adroit_native_background` | transparent | The card's fill |
| `color/adroit_native_stroke` | the text color at 12% | The card's border |
| `color/adroit_native_headline` | `?android:textColorPrimary` | Headline text |
| `color/adroit_native_body` | `?android:textColorSecondary` | Body, advertiser, store and price text |
| `color/adroit_native_cta`, `_cta_end` | `?android:colorPrimary`, the same | The call-to-action button: a left-to-right gradient from `_cta` to `_cta_end`. Set only `_cta` for a solid button. |
| `color/adroit_native_cta_text` | `?android:textColorPrimaryInverse` | Its text |
| `color/adroit_native_attribution` | `?android:colorPrimary` | The "Ad" badge |
| `color/adroit_native_attribution_text` | `?android:textColorPrimaryInverse` | Its text |
| `dimen/adroit_native_card_corner_radius`, `_card_stroke_width` | `_7sdp`, 1dp | The card's corners and border |
| `dimen/adroit_native_cta_corner_radius`, `_attribution_corner_radius` | `_5sdp`, `_2sdp` | The button's and the badge's corners |
| `dimen/adroit_native_headline_text_size`, `_body_text_size`, `_caption_text_size`, `_cta_text_size`, `_attribution_text_size` | `_12ssp`, `_11ssp`, `_10ssp`, `_12ssp`, `_8ssp` | Text sizes |
| `dimen/adroit_native_icon_size`, `_icon_size_large` | `_34sdp`, `_40sdp` | The icon |
| `dimen/adroit_native_cta_min_height` | `_33sdp` | The button's height |
| `dimen/adroit_native_media_height`, `_media_height_compact` | `_133sdp`, 120dp | Media in `large_button_bottom`; in `large_compact` and `media_start`. Keep it at least 120dp, Google's minimum for video. |
| `dimen/adroit_native_spacing`, `_spacing_small`, `_spacing_tiny`, `_spacing_large` | `_7sdp`, `_3sdp`, `_2sdp`, `_13sdp` | Padding and gaps |
| `color/adroit_shimmer_card`, `_shimmer_base`, `_shimmer_highlight` | light greys (dark in night mode) | The [shimmer](#the-shimmer): its card, its blocks, the sweep |
| `dimen/adroit_shimmer_corner_radius`, `_shimmer_padding` | `_3sdp`, `_7sdp` | The shimmer's block corners, and a banner shimmer's padding |
| `drawable/adroit_native_card` | a rounded card with a border | The whole background shape |
| `drawable/adroit_native_cta_background` | a rounded gradient button with a ripple | The whole button shape |
| `drawable/adroit_native_attribution_background` | a rounded rectangle | The whole badge shape |
| `string/adroit_ad_attribution` | `Ad` | The badge text: translate it in `values-xx` |

Sizes use [sdp and ssp](https://github.com/intuit/sdp) (`@dimen/_8sdp`, `@dimen/_12ssp`), which scale with the
screen's width; `adroit-views` brings both. Each default matches the dp size a 360dp-wide phone had, so the templates
look the same there and scale on smaller and larger screens. Your overrides can use sdp and ssp too, or plain dp and
sp. The few kept in dp are set by Google: the AdChoices corner, the badge's 16dp minimum, and the 120dp media minimum.

Replace a drawable to change a shape itself, for example a badge with a border. The colors
follow the light and dark themes until you override them; to override them per theme, put yours in `values` and
`values-night`. Overriding the `Adroit.ThemeOverlay.Native` style replaces its items all at once.

### Colors from Firebase

A native ad's button and badge colors can also come from Firebase, without a release. Add them to the ad's JSON
value (for one ad), or to `AdSettings` (for every native ad that doesn't set its own):

```json
{
  "ctaColors": ["#FF6D00", "#FFC107"],
  "ctaTextColor": "#FFFFFF",
  "attributionColor": "#FFC107",
  "attributionTextColor": "#FFFFFF"
}
```

| Field | Value | What |
|---|---|---|
| `ctaColors` | one color, or a list of two | The button's background: one color is solid, two a left-to-right gradient. |
| `ctaTextColor` | a color | The button's text |
| `attributionColor` | a color | The "Ad" badge's background |
| `attributionTextColor` | a color | The badge's text |

Colors are `"#RRGGBB"`, or `"#AARRGGBB"` with transparency. Each field left out keeps the ad's own look: the
`AdSettings` value, then your app's resources above. A wrong value is ignored with a warning in the log. The colors
apply to every template and to your own layouts, on the views with the standard call-to-action and badge IDs (or the
ones your `NativeAdLayout` maps): a background that's a shape keeps its shape and corners, with the new colors. A
`NativeAdRenderer` runs after them, so it can still change them.

### Your own layout, picked by name

Any layout can show native ads. Write it with ordinary views only: no `NativeAdView` and no `MediaView`, which the
library adds itself. Then either give its views the standard IDs, or map your own IDs with a `NativeAdLayout`:

| Standard ID | View | |
|---|---|---|
| `@id/adroit_native_headline` | `TextView` | Required |
| `@id/adroit_native_body` | `TextView` | |
| `@id/adroit_native_cta` | `Button` (write `<android.widget.Button>`: see below) | |
| `@id/adroit_native_icon` | `ImageView` | |
| `@id/adroit_native_media` | an empty `FrameLayout`, at least 120dp tall | The image or video goes inside |
| `@id/adroit_native_attribution` | `TextView` saying "Ad" | Required by Google's policy |
| `@id/adroit_native_advertiser`, `_stars` (a `RatingBar`), `_price`, `_store` | | |

Views whose asset an ad lacks are hidden. Keep a top corner free (about 24dp) for the AdChoices icon the SDK adds.
In an app with a Material theme, a plain `<Button>` becomes a `MaterialButton`, which paints the theme's color over
your background: write `<android.widget.Button>` to keep yours.

Register the layout under a name in `Application.onCreate`. An ad profile can then use it by default, and Firebase
can switch any native ad to it, or back to a library style, with `"style"`:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/Native.kt#native-register -->
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

In XML, `app:adroit_layout="@layout/my_native_ad"` on an `AdNativeView` shows that layout (standard IDs only)
whatever the style. A name Firebase gives that the app hasn't registered is logged once, and the ad shows its style's
template.

### The shimmer

While a native ad loads (with `showLoading`, the default), its place shows a shimmer made from the layout it will
show in: the library inflates that layout without drawing it, and draws the whole card, then a block over each view
inside it: one per line of text, one per icon, badge, button and media area, at their real positions and sizes. A
highlight sweeps across the card and the blocks together. So every template and every layout of your own gets a
shimmer of the ad's own shape and size, with nothing to write. A banner's shimmer is its whole area, with blocks
like a banner's content. To change the shimmer's colors, override them like the others, in `values` and, for dark mode, `values-night`:

```xml
<color name="adroit_shimmer_card">#FFF3F0FF</color>      <!-- the ad area -->
<color name="adroit_shimmer_base">#FFE0D8FF</color>      <!-- the blocks -->
<color name="adroit_shimmer_highlight">#FFFFFFFF</color> <!-- the sweep -->
```

### A layout from code

For your own layout, map its views with a `NativeAdLayout`, and style them with a `NativeAdRenderer`:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/Native.kt#custom-layout -->
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
  ViewModel is cleared, `AdNativeView` and `attachNativeAd` (into a container with an id) when their screen's ViewModel is cleared,
  `NativeAdHolder` on `clear()`. If you call `native.load` yourself, you own the handle: call `destroy()`.
- `AdNative` and `rememberNativeAd` keep every slot's ad until the screen's ViewModel is cleared, however many
  slots the screen has.
- Rotation: `AdNative`, `rememberNativeAd`, `AdNativeView`, `NativeAdHolder` and `attachNativeAd` (into a container
  with an id) re-render the same ad. A new one comes only when you ask: `refresh = true`, or a new `refreshKey`.
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

**Every rotation loads a new ad.** `attachNativeAd`'s container has no `android:id`: give it one. Or use `AdNative`,
`AdNativeView`, or a `NativeAdHolder` in a ViewModel.

**Logcat warns about retained native ads, or memory grows.** Something holds handles without destroying them: see
[Troubleshooting](TROUBLESHOOTING.md#memory-warnings-with-native-ads).

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-29
