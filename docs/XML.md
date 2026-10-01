# XML and Views

`io.github.thesobiaakbar:adroit-views` adds Views, extensions and templates on top of `adroit-core`. It needs no
`androidx.fragment` dependency, and works in Activities and Fragments.

## `AdBannerView`

```xml
<io.github.thesobiaakbar.adroit.views.AdBannerView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:adroit_placement="home_banner"
    app:adroit_bannerSize="anchoredAdaptive" />
```

| Attribute | Values | Default |
|---|---|---|
| `adroit_placement` | a banner placement key | required |
| `adroit_bannerSize` | `anchoredAdaptive`, `inlineAdaptive`, `mediumRectangle`, `standard` | `anchoredAdaptive` |
| `adroit_inlineMaxHeight` | a dimension, for `inlineAdaptive` | `@dimen/adroit_banner_inline_max_height` |
| `adroit_collapseWhenEmpty` | boolean: `GONE` while there's no ad | true |
| `adroit_refresh` | boolean: ask for a new banner each time the view is created again (see below) | false |

From code: `setPlacement(placement)` or `setPlacement(placement, options)`. The view creates its banner when attached
to a window. An unknown key logs an error and the view stays `GONE`; it never crashes.

### How long an ad stays

The rule for `AdBannerView`, `AdNativeView` and `AdSlotView`, and for `AdBanner`, `AdNative` and `AdSlot` in Compose:
**once an ad is shown in a view, it stays there until its screen is closed for good, or you ask for a new one.**

- The ad is kept by its screen: a ViewModel of the view tree's `ViewModelStoreOwner` (the Fragment inside a
  Fragment, otherwise the Activity). Coming back to the screen (another tab, a Fragment's view recreated) and
  rotation show the same ad, with no new request. It's destroyed when the screen is cleared: the Activity finishes,
  or the Fragment is removed.
- A new ad only when asked: `load(refresh = true)` from code, or `app:adroit_refresh="true"`, which asks each time
  the view is created again. Either way the shown ad stays while the new one loads, the new one takes its place once
  it's loaded, and the old one is destroyed then. If the new one fails, the shown one stays.
- One ad per placement and slot on a screen. The slot is the view's `android:id`, or `slotKey` (set it for views made
  in code, or for several views of one placement without ids).

## `AdNativeView`

```xml
<io.github.thesobiaakbar.adroit.views.AdNativeView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:adroit_placement="feed_native"
    app:adroit_template="large_button_bottom" />
```

| Attribute | Values |
|---|---|
| `adroit_placement` | a native placement key (required) |
| `adroit_template` | A library style: `small_button_end`, `small_button_bottom`, `large_button_bottom`, `large_compact` or `media_start`. Without it, the placement's style decides ([Native](NATIVE.md#templates-and-custom-layouts)). |
| `adroit_layout` | your own layout, using the standard template IDs (`adroit_native_headline`, `_body`, `_cta`, `_icon`, `_media`, `_advertiser`, `_stars`, `_price`, `_store`, `_attribution`) |
| `adroit_refresh` | ask for a new ad each time the view is created again; default false ([How long an ad stays](#how-long-an-ad-stays)) |

From code: `setPlacement(placement)` or `setPlacement(placement, layout)`. It loads when attached, renders, and is
`GONE` if the load fails. Its ad stays as described in [How long an ad stays](#how-long-an-ad-stays).

## Extensions

```kotlin
fun ViewGroup.attachBanner(owner: LifecycleOwner, placement: BannerPlacement, options: BannerOptions = …): BannerAdHandle
fun ViewGroup.attachNativeAd(owner: LifecycleOwner, placement: NativePlacement, layout: NativeAdLayout = …,
    renderer: NativeAdRenderer? = null, onResult: (NativeAdResult) -> Unit = {})
```

Both add the ad to the container. `attachBanner` into a container with an `android:id` keeps its banner for the
screen like `AdBannerView`: call it again for the new container (in `onViewCreated`, or `onCreate` after a rotation)
and the same banner shows, with no new request. `attachNativeAd` destroys its ad when `owner` is destroyed; for a
native ad that stays, use `AdNativeView` or a `NativeAdHolder`. To replace a shown ad, use the views'
`load(refresh = true)`. In a Fragment, always pass `viewLifecycleOwner`:

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

## `NativeAdHolder`

A native ad that outlives configuration changes and list recycling. Keep one per ad position in the screen's
ViewModel, `bindTo(container, lifecycleOwner, layout, renderer)` on each bind, and `clear()` in `onCleared()`. Binding
again re-renders the same ad; it never loads a new one. See the [RecyclerView example](NATIVE.md#recyclerview).

## `NativeTemplates`

`NativeTemplates.Small`, `Medium` and `Large`, as `NativeAdLayout`s. Restyle them by overriding
`Adroit.ThemeOverlay.Native` or its `adroit_*` attributes in your theme.

## Full-screen ads from Views

Full-screen formats only need an Activity: `Adroit.showInterstitial(activity, placement) { … }`. From a
Fragment, pass `requireActivity()`. Show them from click handlers or ViewModel events, not from `onCreate`.

## Fragments and rotation

- Fragment views are destroyed and recreated more often than the Fragment: use `viewLifecycleOwner` for everything
  view-bound, and create views and collect flows in `onViewCreated`.
- `AdBannerView`, `AdNativeView` and `AdSlotView` keep their ads for the Fragment, not its view: a tab that keeps its
  Fragment (Navigation's bottom navigation, or `detach`/`attach`) shows the same ads when you come back. With
  `replace()`, the Fragment itself is destroyed, and its ads with it.
- `sample-xml` shows each pattern: a banner in XML and one attached from code, a native feed with holders in a
  ViewModel, the rewarded interstitial intro as a `DialogFragment`, and a settings screen with the privacy options
  button.

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-29
