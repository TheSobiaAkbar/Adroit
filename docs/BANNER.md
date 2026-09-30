# Banner ads

## When to use them

A banner stays on screen with your content: the bottom of a screen (anchored adaptive), or inline in scrolling
content (inline adaptive, medium rectangle). In Compose, `AdBanner` works in a `LazyColumn` too: each item's banner
(its `slotKey`) is kept by the screen, so scrolling back shows the same ad ([Compose](COMPOSE.md#adbanner)). In a
`RecyclerView`, a recycled banner view loads a new ad: use [native ads](NATIVE.md) there.

| `BannerSize` | XML `adroit_bannerSize` | Use |
|---|---|---|
| `AnchoredAdaptive` (default) | `anchoredAdaptive` | Pinned to the top or bottom, full width |
| `InlineAdaptive(maxHeightDp)` | `inlineAdaptive` (+ `adroit_inlineMaxHeight`) | Inside scrolling content |
| `MediumRectangle` | `mediumRectangle` | 300×250 inside content |
| `Standard` | `standard` | 320×50, when you need a fixed size |
| `Collapsible(BOTTOM or TOP)` | `collapsibleBottom`, `collapsibleTop` | Anchored adaptive that opens expanded, then collapses; at that edge of the screen |

The size comes from the placement (`size` in `placements { }`, or `style` in the [ads file](AD_PROFILES.md)) unless the
code passes one, so the configuration or Firebase can change it for banners created afterwards. Collapsible banners
follow Google's rules: only the first load of each banner is collapsible (automatic refreshes aren't), and mediated
ads show as normal banners ([Google's guide](https://developers.google.com/admob/android/next-gen/banner/collapsible)).

## Integration

### XML

<!-- snippet: docs-snippets/src/main/res/layout/snippet_banner.xml#xml-banner -->
```xml
<io.github.thesobiaakbar.adroit.views.AdBannerView
    android:id="@+id/home_banner"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:adroit_placement="home_banner"
    app:adroit_bannerSize="anchoredAdaptive" />
```

`AdBannerView` finds its lifecycle owner itself (the Fragment's view lifecycle inside a Fragment), loads when ads
are ready, and destroys the banner with that owner. It's `GONE` while there's no ad
(`adroit_collapseWhenEmpty`, default true). You can also call `setPlacement(placement, options)` from code.

### A container from code

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/Banner.kt#fragment-attach -->
```kotlin
class HomeFragment : Fragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // viewLifecycleOwner: the banner is destroyed in onDestroyView, never leaked across view recreation.
        view.findViewById<FrameLayout>(R.id.banner_container)
            .attachBanner(viewLifecycleOwner, Placements.HomeBanner)
    }
}
```

### Compose

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/Banner.kt#compose-banner -->
```kotlin
@Composable
fun HomeBottomBar() {
    var state by remember { mutableStateOf<BannerState?>(null) }
    AdBanner(
        placement = Placements.HomeBanner,
        modifier = Modifier.fillMaxWidth(),
        options = BannerOptions(BannerSize.AnchoredAdaptive),
        placeholder = { Text("Advertisement", Modifier.padding(8.dp)) },
        onStateChange = { state = it },
    )
}
```

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/Banner.kt#compose-mrec -->
```kotlin
@Composable
fun ArticleRectangle() {
    AdBanner(
        placement = Placements.HomeBanner,
        modifier = Modifier.fillMaxWidth().height(250.dp),
        options = BannerOptions(BannerSize.MediumRectangle),
    )
}
```

### Plain View, with core only

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/Banner.kt#plain-view -->
```kotlin
/** Without the views module's helpers: create the handle yourself, add its view, and destroy it. */
fun showBanner(activity: Activity, container: FrameLayout, owner: LifecycleOwner) {
    val handle = Adroit.banner.create(
        activity,
        Placements.HomeBanner,
        BannerOptions(size = BannerSize.InlineAdaptive(maxHeightDp = 250), reserveSpace = true),
    )
    container.addView(handle.view)
    handle.bindTo(owner) // destroyed when owner is destroyed; or call handle.destroy() yourself
}
```

## Loading ahead of the screen

`AdSlots.load` loads a banner before its screen opens (an ad profile's banner, or any banner placement by key): the
next banner view created for it, `AdSlotView`, `AdBannerView`, `attachBanner` or `AdBanner`, shows it at once, or
waits for it if it's still loading instead of requesting another. It's loaded at the window's width, so it fits a
full-width banner; a banner of another width loads its own. See [Ad profiles](AD_PROFILES.md#loading).

## Lifecycle

- A banner belongs to an Activity, and is destroyed when its lifecycle owner is. In a Fragment, always use
  `viewLifecycleOwner`.
- In Compose, the banner is created once per placement, options and Activity, reused across recompositions, and
  destroyed when it leaves the composition. A configuration change recreates the Activity, so the old banner is
  destroyed and a new one loads; there's never a duplicate.
- With `reserveSpace = true` (the default), the banner's expected height is reserved while it loads, so the layout
  doesn't jump.

## State

`BannerAdHandle.state` (and `AdBanner`'s `onStateChange`): `Loading` → `Loaded`, or `Failed(error)`, or
`Hidden(reason)` when ads are blocked (ad-free, consent withdrawn, remote kill switch) or the banner was destroyed.

## Events

`LoadRequested`, `Loaded` (also on each automatic refresh), `LoadFailed(error, attempt, willRetry)`, `Impression`,
`Clicked`, `Paid` and `Destroyed`. See [Analytics](ANALYTICS.md).

## Errors, retry and refresh

- Refresh is set per ad unit in the AdMob UI; the SDK refreshes only while the banner is visible
  ([Google](https://developers.google.com/admob/android/next-gen/banner)). The library doesn't add its own refresh.
- A failed first load retries with backoff (15 s, doubling up to 120 s, 4 attempts), only while the view is attached
  and visible. A no-fill waits at least 30 s.

## Policy notes

- Video ads in banners need hardware acceleration, which is on by default. Never turn it off for a window that shows
  ads ([Google](https://developers.google.com/admob/android/next-gen/banner#hardware_acceleration_for_video_ads)).
  The library logs a warning when a banner's Activity isn't accelerated.
- Don't place banners where they can be clicked by accident, and don't overlap them with content.

## FAQ

**The banner never appears.** Check `Adroit.state` is `Ready`, the placement key matches the configuration,
and the state reported by `onStateChange`. See [Troubleshooting](TROUBLESHOOTING.md).

**Does rotation reload the banner?** Yes: a banner belongs to its Activity, and rotation recreates the Activity.

**Can I show the same placement twice on one screen?** Yes, each view is a separate banner.

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-29
