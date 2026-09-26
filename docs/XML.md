# XML and Views

`com.company:admob-views` adds Views, extensions and templates on top of `admob-core`. It needs no
`androidx.fragment` dependency, and works in Activities and Fragments.

## `AdBannerView`

```xml
<com.company.admob.views.AdBannerView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:company_admob_placement="home_banner"
    app:company_admob_bannerSize="anchoredAdaptive" />
```

| Attribute | Values | Default |
|---|---|---|
| `company_admob_placement` | a banner placement key | required |
| `company_admob_bannerSize` | `anchoredAdaptive`, `inlineAdaptive`, `mediumRectangle`, `standard` | `anchoredAdaptive` |
| `company_admob_inlineMaxHeight` | a dimension, for `inlineAdaptive` | `@dimen/company_admob_banner_inline_max_height` |
| `company_admob_collapseWhenEmpty` | boolean: `GONE` while there's no ad | true |

From code: `setPlacement(placement)` or `setPlacement(placement, options)`. The view creates its banner when attached
to a window, for its view tree's `LifecycleOwner` (the Fragment's view lifecycle inside a Fragment), and destroys it
with that owner. An unknown key logs an error and the view stays `GONE`; it never crashes.

## `AdNativeView`

```xml
<com.company.admob.views.AdNativeView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:company_admob_placement="feed_native"
    app:company_admob_template="medium" />
```

| Attribute | Values |
|---|---|
| `company_admob_placement` | a native placement key (required) |
| `company_admob_template` | `small`, `medium` (default), `large` |
| `company_admob_layout` | your own layout, using the standard template IDs (`company_admob_native_headline`, `_body`, `_cta`, `_icon`, `_media`, `_advertiser`, `_stars`, `_price`, `_store`, `_attribution`) |

From code: `setPlacement(placement)` or `setPlacement(placement, layout)`. It loads when attached, renders, and is
`GONE` if the load fails. It loads a new ad after a configuration change: for ads that must survive rotation, use
`NativeAdHolder`.

## Extensions

```kotlin
fun ViewGroup.attachBanner(owner: LifecycleOwner, placement: BannerPlacement, options: BannerOptions = …): BannerAdHandle
fun ViewGroup.attachNativeAd(owner: LifecycleOwner, placement: NativePlacement, layout: NativeAdLayout = …,
    renderer: NativeAdRenderer? = null, onResult: (NativeAdResult) -> Unit = {})
```

Both add the ad to the container and destroy it when `owner` is destroyed. In a Fragment, always pass
`viewLifecycleOwner`:

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/Banner.kt#fragment-attach -->
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
`CompanyAdMob.ThemeOverlay.Native` or its `company_admob_*` attributes in your theme.

## Full-screen ads from Views

Full-screen formats only need an Activity: `CompanyAdMob.showInterstitial(activity, placement) { … }`. From a
Fragment, pass `requireActivity()`. Show them from click handlers or ViewModel events, not from `onCreate`.

## Fragments and rotation

- Fragment views are destroyed and recreated more often than the Fragment: use `viewLifecycleOwner` for everything
  view-bound, and create views and collect flows in `onViewCreated`.
- `sample-xml` shows each pattern: a banner in XML and one attached from code, a native feed with holders in a
  ViewModel, the rewarded interstitial intro as a `DialogFragment`, and a settings screen with the privacy options
  button.

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-26
