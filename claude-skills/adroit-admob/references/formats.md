# Formats: every ad, in Compose and XML

## Contents
1. Inline ads: `AdSlot` / `AdSlotView` (banner or native)
2. Banner details
3. Native details: styles, custom layouts, lists
4. Shimmer, frame, theming
5. Close button: remove-ads
6. Interstitial
7. Rewarded
8. Rewarded interstitial
9. App open
10. Events and diagnostics

## 1. Inline ads: `AdSlot` / `AdSlotView`

Use the format-agnostic slot for every banner and native ad. It shows whichever format the profile says now, so
Firebase can swap a banner for a native ad without a release. It shows a shimmer while the ad loads, and collapses to
zero size when no ad comes.

```kotlin
// Compose
AdSlot(AppAds.HomeBanner, Modifier.fillMaxWidth())
AdSlot(definition: AdDefinition, modifier = Modifier, placeholder: (@Composable () -> Unit)? = null,
       slotKey: Any = Unit, refreshKey: Any? = null)
```
```xml
<!-- XML: give it an id, so a rotation keeps the same ad -->
<io.github.thesobiaakbar.adroit.views.AdSlotView
    android:id="@+id/home_banner"
    android:layout_width="match_parent" android:layout_height="wrap_content"
    app:adroit_placement="HomeBanner" />
```
```kotlin
// Views, from code: into any ViewGroup that has an android:id
val slot: AdSlotView = container.attachAd(AppAds.DetailsBanner)
```

**How long an ad stays:**
- An ad belongs to its screen. Recomposition, rotation, a tab switch, and scrolling out and back all reuse it, with
  no new request.
- It is destroyed with the screen: the Compose slot keeps it in the current `ViewModelStoreOwner`, which is the nav
  back-stack entry.
- **New ad on demand:** change `refreshKey` (Compose), or call `slot.load(refresh = true)` (Views). The shown ad stays
  until its replacement has loaded.
- **Lists:** in a `LazyColumn`, give each ad item its own stable key and `slotKey = "feed-ad-$index"`. In a
  `RecyclerView`, use native ads with `NativeAdHolder` (section 3), never banners: recycled banners reload.

**Bottom-pinned banner in XML:** put the `AdSlotView` last in a vertical `LinearLayout`, with the content above it
at `layout_height="0dp"` and `layout_weight="1"`. With edge-to-edge, pad the root by the navigation bar inset
(`ViewCompat.setOnApplyWindowInsetsListener`), or keep the theme's default non-edge-to-edge window.

**Bottom-pinned banner pattern (Compose):**
```kotlin
Column(Modifier.fillMaxSize()) {
    Box(Modifier.weight(1f)) { ScreenContent() }
    AdSlot(AppAds.HomeBanner, Modifier.fillMaxWidth().navigationBarsPadding())
}
```

**Typed composables**, when a format swap must not happen:
- `AdBanner(placement: BannerPlacement, modifier, options = BannerOptions(), placeholder, slotKey, refreshKey, onStateChange)`
- `AdNative(placement: NativePlacement, modifier, layout: NativeAdLayout? = null, slotKey, renderer, placeholder, refreshKey)`
- `rememberNativeAd(placement, slotKey): State<NativeAdResult?>`, for drawing the assets yourself
- `rememberAdAvailability(placement): State<AdAvailability>`

The XML equivalents are `AdBannerView` (`app:adroit_bannerSize="anchoredAdaptive|inlineAdaptive|mediumRectangle|standard|collapsibleBottom|collapsibleTop"`)
and `AdNativeView` (`app:adroit_template="large_button_bottom"` or `app:adroit_layout="@layout/x"`). Each has
`setPlacement(...)`, `load(refresh)`, `slotKey` and `refreshOnShow`.

The extension functions are:
- `ViewGroup.attachBanner(owner, placement, options, refresh)`
- `ViewGroup.attachNativeAd(owner, placement, layout, renderer, refresh, onResult)`

Both need an `android:id` on the container. In a Fragment, pass `viewLifecycleOwner`.

## 2. Banner

| Style | Code | Firebase |
|---|---|---|
| Anchored adaptive (full width, default) | `BannerSize.AnchoredAdaptive` | `adaptive` |
| Collapsible | `BannerSize.Collapsible(CollapsibleBannerPosition.BOTTOM)` / `TOP` | `collapsible_bottom` / `collapsible_top` |
| Medium rectangle 300×250 | `BannerSize.MediumRectangle` | `rectangle` |
| Standard 320×50 | `BannerSize.Standard` | `standard` |
| Inline adaptive | `BannerSize.InlineAdaptive(maxHeightDp)` | `inline_adaptive` |

- **Refresh rate** is an AdMob setting per ad unit. The library adds no refresh timer of its own.
- **Collapsible:**
  - Only the first load of a slot is collapsible.
  - The expanded panel is a popup that belongs to its screen. Leaving the screen hides it, and it doesn't follow the
    user. This was fixed in Adroit `af73065` (after rc6); older versions leaked the expanded banner over the next screen.
  - Place a collapsible bottom banner at the very bottom of the screen, and a top one at the top.
- **Failed first load:** retried with backoff (15 s, doubling to 120 s, 4 attempts), only while the view is visible.
- **Ad-free:** the banner hides immediately, with no shimmer, even before `start()`. This needs Adroit `b0df3c8` or
  later.

## 3. Native

**Library styles** (`NativeStyle` / Firebase `style` / `NativeTemplates.X`):

| Style | Look |
|---|---|
| `SMALL_BUTTON_END` / `small_button_end` | icon, headline and body, button at the row's end; no media. Good in lists. |
| `SMALL_BUTTON_BOTTOM` / `small_button_bottom` | icon, headline and body, full-width button below; no media |
| `LARGE_BUTTON_BOTTOM` / `large_button_bottom` (default) | header, full-width media, then the button |
| `LARGE_COMPACT` / `large_compact` | like large, with a bigger icon and 120dp media |
| `MEDIA_START` / `media_start` | media at the start, text and button beside it; no icon |

**Your own layout:**
- Use plain views only: no `NativeAdView` and no `MediaView`, because the library adds them.
- Standard IDs:
  - `@id/adroit_native_headline`: the TextView; required.
  - `_body`
  - `_cta`: write `<android.widget.Button>`. Under a Material theme, a plain `<Button>` becomes a `MaterialButton`
    and paints over your background.
  - `_icon`
  - `_media`: an empty `FrameLayout`, at least 120dp tall.
  - `_attribution`: the "Ad" badge, which Google's policy requires.
  - `_advertiser`, `_stars`, `_price`, `_store`.
- Keep a top corner free (about 24dp) for AdChoices.

```kotlin
// Application.onCreate: register it under a name; profiles and Firebase pick it with "style": "promo"
NativeTemplates.register("promo", R.layout.my_native_ad)        // standard IDs
NativeTemplates.register("promo", NativeAdLayout(R.layout.article_ad, headlineId = R.id.title, bodyId = R.id.text,
    callToActionId = R.id.button, iconId = R.id.logo, mediaContainerId = R.id.media, attributionId = R.id.badge))
// AppAds entry using it
PromoNative(AdProfile.native(layout = "promo", ids = unit("…")))
```
A style name Firebase sends that the app hasn't registered is logged, and the ad falls back to its own style.

**Custom binding:** `renderer = NativeAdRenderer { root, assets -> /* extra views */ }` on `AdNative` or
`attachNativeAd`.

**RecyclerView:** native ads only, never banners. Keep one `NativeAdHolder` per ad position in the ViewModel:
```kotlin
// The list model: notes with an ad row every 10 items. Stable ids, so DiffUtil keeps ad rows in place.
sealed interface Row { val id: Long
    data class Note(val note: NoteEntity) : Row { override val id = note.id }
    data class Ad(val slot: Int) : Row { override val id = -1L - slot }          // negative ids never clash with notes
}
fun withAds(notes: List<NoteEntity>, every: Int = 10): List<Row> = buildList {
    notes.forEachIndexed { i, n -> add(Row.Note(n)); if ((i + 1) % every == 0) add(Row.Ad(slot = (i + 1) / every - 1)) }
}

class NotesViewModel : ViewModel() {
    private val holders = mutableMapOf<Int, NativeAdHolder>()
    fun adHolder(slot: Int) = holders.getOrPut(slot) { NativeAdHolder(AppAds.ListNative.nativePlacement()) }
    override fun onCleared() = holders.values.forEach(NativeAdHolder::clear)          // destroys the ads
}

// Adapter: a second view type whose item layout is just a FrameLayout container (wrap_content height)
override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    when (val row = getItem(position)) {
        is Row.Ad -> viewModel.adHolder(row.slot).bindTo((holder as AdVH).container, lifecycleOwner) // layout from the style
        is Row.Note -> (holder as NoteVH).bind(row.note)
    }
}
```
How this behaves:
- **One request per holder:** the first `bindTo` loads, and later binds re-render the same ad without a new request.
  So a list with 50 notes asks for an ad only when ad row 0 … 4 is first bound (scrolled into view).
- **Recycling is safe:** `bindTo` renders the held ad into whichever container it gets, and drops the previous one.
  No unbind is needed in `onViewRecycled`.
- **No fill:** the container goes GONE, and the row takes no space.
- **`lifecycleOwner`:** the Activity (`this`) in an Activity, or `viewLifecycleOwner` in a Fragment. Pass it to the
  adapter.
- In a Compose `LazyColumn`, use the same model and `AdSlot(AppAds.ListNative, slotKey = "list-${row.slot}")`, with
  `key = row.id` on the item.

**Owning the ad yourself:** `Adroit.native.load(placement)` returns `NativeAdResult.Loaded(handle)`. You then own the
handle and must call `handle.destroy()`. Prefer the slots, which manage this for you.

## 4. Shimmer, frame, theming

- **Shimmer** is on by default (`showLoading`). It is shaped like the coming ad and sized from the style. A Compose
  `placeholder` replaces it. Its colours are `adroit_shimmer_card`, `adroit_shimmer_base` and
  `adroit_shimmer_highlight`, each with a night variant.
- **Frame:** every banner and native ad sits in an `adroit_ad_margin` frame (8dp). Override the dimen; `0dp` removes
  the frame.
- **Native colours**, in `res/values/ads_style.xml`:
  - `adroit_native_cta`, plus `adroit_native_cta_end` for a gradient
  - `adroit_native_cta_text`
  - `adroit_native_attribution` and `adroit_native_attribution_text`
  - Firebase can also set them per ad or in `AdSettings`: `ctaColors` (one colour, or two for a gradient),
    `ctaTextColor`, `attributionColor` and `attributionTextColor`.
- **Sizes** use `_sdp`/`_ssp` dimens, which you can override. Match the app's design system with these resources
  rather than writing a custom layout.
- **"Loading ad…" screen** (full-screen ads): override the layout and string named `adroit_loading_ad`.

## 5. Close button: remove-ads

```kotlin
HomeBanner(AdProfile.banner(style = ..., closeButton = AdCloseButton.TOP_END, closeIcon = "ic_ad_close")),
// or for every banner and native ad: AdSettings(closeButton = AdCloseButton.TOP_END)
```
- The button sits in its own row above or below the ad, inside the frame. It is 28dp (`adroit_ad_close_size`), and
  TalkBack reads "Close ad" (`adroit_ad_close`).
- **There is no `onClose`, and a tap does not remove the ad.** It only emits `AdEvent.CloseClicked(placementKey)`.
  Collect that event once, at the app level, and open the remove-ads offer. Gate it with the paywall's show rules,
  so the user isn't nagged:

```kotlin
// AppNavHost (Compose) or MainActivity: one collector for the whole app
LaunchedEffect(Unit) {
    Adroit.events.filterIsInstance<AdEvent.CloseClicked>().collect {
        if (AdroitBilling.paywalls.shouldShow(AppPaywalls.RemoveAds, "ad_close") == ShowDecision.Show) {
            navController.navigate(RemoveAdsRoute) { launchSingleTop = true }
        }
    }
}
```
- Without billing: hide the slot yourself, or don't add a close button at all.
- Google's policy: a button that looks like "close" must lead somewhere the user expects, for example "Remove ads".

## 6. Interstitial

```kotlin
// Load when the screen that leads to it comes to the front, or when the work behind it starts
LifecycleResumeEffect(Unit) { AdSlots.load(AppAds.ResultInterstitial); onPauseOrDispose {} }   // Compose
override fun onResume() { super.onResume(); AdSlots.load(AppAds.ResultInterstitial) }           // Views

// Show at the transition, AFTER the user's work is safe (save the note first, then the ad); continue in the callback
fun onDone(activity: Activity?) {
    if (activity == null) openResults() else AdSlots.showFullScreen(activity, AppAds.ResultInterstitial) { openResults() }
}
```
- Getting the Activity in Compose: `LocalActivity.current` (activity-compose 1.10+). If it is null, continue without
  an ad.
- Frequency comes from `counter`, `counters`, `timerSeconds`, `maxPerSession`, and `AdSettings`'s
  `minSecondsBetweenFullScreenAds` / `maxInterstitialsPerSession` (`ad-profiles.md` §4 and §7).
- **The event-tied pattern** (PhotoRecovery's scan ad): no `loadAgain`. Load it when the long task starts, and show it
  when the task ends, or when the user leaves an empty result. One ad per occurrence.
- **The counter pattern:** call `showFullScreen` on every occurrence (each "next level"), and let `counter` decide.
- **Launch interstitial instead of a splash ad:** see `splash-consent-update.md` §4.
- Lower-level calls: `Adroit.interstitial.show(activity, placement) {}` and `showAndAwait(...)`. These don't follow
  format swaps.
- Other APIs: `rememberFullScreenAdLauncher().showInterstitial(placement) {}` in Compose, and `Adroit.preload(...)`.
- Expiry is 55 min. A no-fill waits at least 30 s before the next request.

## 7. Rewarded

The reward is granted in a ViewModel, once per `rewardId`, including rewards that arrive after the screen was gone:

```kotlin
class UnlockViewModel(private val ads: AdsClient = Adroit.client) : ViewModel() {
    private val granted = mutableSetOf<String>()
    private val _state = MutableStateFlow(UnlockState())
    val state = _state.asStateFlow()

    init {
        // Earned while this screen was gone (rotation): delivered here instead.
        viewModelScope.launch { ads.rewarded.unclaimedResults.collect(::grant) }
    }

    fun watchAd(activity: Activity) {
        AdSlots.load(AppAds.UnlockRewarded) // in case the preload failed or was used; the show waits behind "Loading ad…"
        ads.rewarded.show(activity, AppAds.UnlockRewarded.rewardedPlacement()) { result ->
            when (result) {
                is RewardResult.Earned -> grant(result)
                RewardResult.NotEarned -> _state.update { it.copy(message = Msg.WatchWholeAd) }
                is RewardResult.Failed -> _state.update { it.copy(message = Msg.NoAdNow) } // offline, no fill: offer another way
            }
        }
    }

    private fun grant(reward: RewardResult.Earned) {
        if (granted.add(reward.rewardId)) _state.update { it.copy(unlocked = true) }
    }
}
```
- **What `unclaimedResults` is for:**
  - With the callback form `show(...) { }` held by a ViewModel, the callback always arrives. The ViewModel outlives
    the rotation.
  - `unclaimedResults` delivers an `Earned` whose **`showAndAwait`** caller was cancelled mid-ad, for example a
    coroutine tied to a screen that rotated.
  - Each reward goes to exactly one collector. Collect it in one long-lived place, whether or not you use
    `showAndAwait`.
- **Hide or disable the button when the ad is off** (`AdAvailability.Disabled` is an object):
  `rememberAdAvailability(placement)`, or `Adroit.rewarded.availability(placement).value`.
  - `Disabled` means it was turned off in code or in Firebase. Hide the button.
  - `Ready` means you can enable it at once.
  - `Loading` and `Unavailable` mean a tap waits behind "Loading ad…".

  PhotoRecovery shows the "watch ad" option only while availability is not `Disabled`.
- **Preload** at the moment the offer becomes likely. PhotoRecovery loads the rewarded ad when a scan finishes, before
  the unlock sheet appears.
- **Server-side verification**, for valuable rewards: `RewardedShowOptions(serverSideVerificationCustomData = "user=…")`.
  Grant on your server's callback.
- **Compose launcher:** `rememberFullScreenAdLauncher().showRewarded(placement) { result -> }`. Keep the grant in the
  ViewModel anyway.
- Rewarded ads can't be swapped to another format, and they skip the global gap by default.
- For multi-module apps, put the rewarded ad behind a feature-owned `fun interface` (`architecture.md`).

## 8. Rewarded interstitial

Google requires your own intro screen first. It states the reward, gives a clear "No thanks", and leaves time to opt
out (a countdown):

```kotlin
@Composable
fun BonusIntro(onDone: () -> Unit, onReward: (RewardResult.Earned) -> Unit) {
    val launcher = rememberFullScreenAdLauncher()
    var secondsLeft by rememberSaveable { mutableIntStateOf(5) }
    LaunchedEffect(secondsLeft) {
        if (secondsLeft == 0) launcher.showRewardedInterstitial(AppAds.BonusRI.rewardedInterstitialPlacement()) {
            if (it is RewardResult.Earned) onReward(it); onDone()
        } else { delay(1.seconds); secondsLeft-- }
    }
    Text("Watch a short ad for bonus coins? Starts in $secondsLeft s.")
    TextButton(onClick = onDone) { Text("No thanks") }
}
```
Grant the reward as for rewarded ads (ViewModel, `rewardId`, `Adroit.rewardedInterstitial.unclaimedResults`).
Rewarded interstitials count toward the global full-screen gap.

## 9. App open

```kotlin
AppOpenAd(AdProfile.appOpen(excludedActivities = listOf(SplashActivity::class.java.name), loadAgain = true)),
// MainActivity.onCreate: load it once; loadAgain keeps one ready after each show
AdSlots.load(AppAds.AppOpenAd)
```
- **It shows by itself** when the app returns to the foreground. It never shows on the process's first start, never
  waits for a load, and never shows over an excluded Activity or another full-screen ad (or within 5 s of one).
- **Cooldown:** set `timerSeconds` (for example 240) in the profile or in Firebase; the default is 0. A cooldown
  stops the ad from showing on every quick return.
- **Skip returns from places the app itself sent the user** (share sheet, settings, mail, Play, pickers, camera,
  sign-in, purchases). The robust pattern, from PhotoRecovery's fix `632f184`, is below. It requests the skip only once
  the Activity has really stopped behind another app's screen. A share sheet that is cancelled never stops the
  Activity, so requesting the skip in `startActivity` swallowed the next real return's ad.

```kotlin
class MainActivity : AppCompatActivity() {
    private var leftForOtherApp = false

    override fun startActivity(intent: Intent, options: Bundle?) { skipAppOpenFor(intent); super.startActivity(intent, options) }
    @Deprecated("Deprecated in Java")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        skipAppOpenFor(intent)
        @Suppress("DEPRECATION") super.startActivityForResult(intent, requestCode, options)
    }
    private fun skipAppOpenFor(intent: Intent) { if (intent.component?.packageName != packageName) leftForOtherApp = true }

    override fun onStop() { super.onStop(); if (leftForOtherApp) Adroit.appOpen.skipNextForeground() }
    override fun onResume() { super.onResume(); leftForOtherApp = false }
}
```
  This catches `Context.startActivity` and the Activity Result launchers (permission settings, pickers), which all
  route through these two methods. For anything that leaves the app without going through them (a third-party SDK's
  own Activity, for example), call `Adroit.appOpen.skipNextForeground()` just before it, and check on a device that
  coming back shows no ad.
- **Play Billing's purchase sheet** starts an Activity in the app's own package, so the skip pattern doesn't flag it.
  Nothing in Adroit links the purchase sheet to app open. After adding IAP, check on a device: buy (or cancel) from a
  paywall, then confirm that no app open ad appears on return. If one does, call
  `Adroit.appOpen.suppressWhile(owner)` with the paywall screen's lifecycle owner.
- **Several Activities:** put the skip pattern in a `BaseActivity` that every Activity except the splash extends. Any
  of them can be the one that opens another app.
- **A failed first load** isn't retried by itself. Call `AdSlots.load(AppAds.AppOpenAd)` in the main Activity's
  `onResume` rather than only in `onCreate`. It does nothing while an ad is loaded or loading.
- `Adroit.appOpen.suppressWhile(lifecycleOwner)` blocks the ad while a screen (checkout, camera) is at least STARTED.
- `Adroit.appOpen.setEnabled(false)` turns it off; `Adroit.setAdFree(true)` stops every format.
- **Cold-start app open** (instead of a splash interstitial): `Adroit.appOpen.showOnColdStart(activity, maxWait = 4.seconds) { goToMain() }`,
  called from the splash after `start`. Alternatively, swap `SplashAd` to `app_open` in Firebase, which needs app open
  unit IDs.

## 10. Events and diagnostics

- `Adroit.events: SharedFlow<AdEvent>` reports loads, shows, clicks, failures, `CloseClicked` and `RewardEarned`.
  Match it non-exhaustively (with an `else` branch): sealed types gain values between releases.
- With Firebase, analytics events are `eventListeners += FirebaseAnalyticsEventListener(context)` in `adMobConfig`.
  `FirebaseAnalyticsOptions(logAdImpressionRevenue = true)` logs `ad_impression` revenue. Don't also log it from
  AdMob-linked Firebase.
- `Adroit.diagnostics` gives a snapshot for a debug screen.
- Logcat tag `Adroit`.
