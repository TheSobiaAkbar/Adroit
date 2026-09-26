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

`NativeTemplates.Small` (icon, headline, call to action), `Medium` (adds body, rating, store and 16:9 media) and
`Large` (full-width media first). They use framework widgets only, follow light and dark themes, and can be
restyled by overriding the `CompanyAdMob.ThemeOverlay.Native` style and its `company_admob_*` attributes.

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

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-26
