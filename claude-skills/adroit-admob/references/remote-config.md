# Remote Config: every ad controlled from Firebase

## Contents
1. How it works
2. The JSON per ad
3. `AdSettings`
4. Format swaps, precedence, validation
5. The app's own switches: `RemoteValue`
6. Generating the console values, and phase files
7. Quick-change table for the marketer

## 1. How it works

- Each ad has a **JSON** parameter named exactly like its enum entry (`HomeBanner`). There is one optional `AdSettings`
  parameter for what every ad shares.
- **A value carries only the fields it changes.** `{ "enabled": false }` turns one ad off; `{ "counter": 3 }` changes
  only the counter. Every other field keeps the code's value.
- **Fetching:**
  - The first Adroit Firebase module fetches and activates once per launch, in the background, for ads, billing and
    updates together. There is no fetch call in app code.
  - After a failure it retries on the next Activity, or when the network returns.
  - Nothing waits for the fetch: the splash uses whatever values are active when its timer starts, which is usually
    the previous launch's.
- **Debug:** set `minimumFetchIntervalInSeconds = 0` in `Application.onCreate` (`setup.md` §5), so a console change
  shows on the next launch.
- Logcat (`Adroit`) prints `Firebase parameter <Name>: {…}` for each ad in non-PRODUCTION builds, which you can copy
  into the console.

## 2. The JSON per ad

**Every format:**

| Field | Default | Meaning |
|---|---|---|
| `format` | from code | `interstitial`, `rewarded`, `rewarded_interstitial`, `app_open`, `banner`, `native` |
| `enabled` | `true` | `false` turns this ad off |
| `adUnitIds` | from code | A list used in turn, or a single string. An empty list keeps the code's IDs. In PRODUCTION, demo IDs are rejected. |
| `loadAgain` | `false` | Load the next ad right after each show (not for banners) |
| `loadTimeoutSeconds` | `AdSettings` (8) | How long a show waits for an unloaded ad; `0` doesn't wait |
| `showLoading` | `AdSettings` (true) | The "Loading ad…" screen, or the shimmer for inline ads |
| `loadingSeconds` | `AdSettings` (1) | The minimum "Loading ad…" time when the ad is ready |

**Interstitial and rewarded interstitial:**
- `counter`: a number, or a list such as `[3, 2]`.
- `counterStart`, `counterStep`: decimals are allowed.
- `counterResetOnFail`
- `loadAhead`
- `timerSeconds`: ignored while a counter is set.
- `maxPerSession`
- `splashTimeoutSeconds`

**Other formats:**
- **rewarded:** `maxPerSession`.
- **app_open:** `timerSeconds` (the cooldown) and `maxPerSession`.
- **banner:**
  - `style`: `adaptive`, `collapsible_bottom`, `collapsible_top`, `rectangle`, `standard` or `inline_adaptive`.
  - `closeButton`: `none`, `top_start`, `top_end`, `bottom_start` or `bottom_end`.
  - `closeIcon`.
- **native:**
  - `style`: `small_button_end`, `small_button_bottom`, `large_button_bottom`, `large_compact`, `media_start`, or the
    name of a registered layout.
  - Colours: `ctaColors` (`"#FF6D00"`, or `["#FF6D00", "#FFC107"]` for a gradient), `ctaTextColor`,
    `attributionColor` and `attributionTextColor`, each as `#RRGGBB` or `#AARRGGBB`.
  - `closeButton` and `closeIcon`.

**Code-only fields**, which Firebase ignores: `splash`, `excludedActivities` and `poolSize`.

## 3. `AdSettings`

```json
{
  "adsEnabled": true,
  "maxInterstitialsPerSession": 0,
  "minSecondsBetweenFullScreenAds": 30,
  "splashSeconds": 8,
  "premiumSplashSeconds": 3,
  "offlineSplashSeconds": 3,
  "consentTimeoutSeconds": 8,
  "loadTimeoutSeconds": 8,
  "showLoading": true,
  "loadingSeconds": 1,
  "closeButton": "top_end"
}
```
It also accepts the native colour fields as defaults for every native ad, and `closeIcon`. To use a parameter name
other than `AdSettings`, pass it to `FirebaseRemoteConfigProvider(remoteConfig, settingsKey = "…")`.

## 4. Format swaps, precedence, validation

**Allowed swaps:** interstitial ↔ app_open, and banner ↔ native. Rewarded formats never swap.
- **The value must include `adUnitIds` of the new format**, because an AdMob unit serves one format only. Without
  them, PRODUCTION ignores the swap and keeps the old format; the value's other fields still apply.
- `style` in that value is read for the new format.
- The ad keeps its counters and limits.
- `AdSlot`, `AdSlotView` and `AdSlots.showFullScreen` follow the swap at once. The typed APIs (`AdBanner`,
  `Adroit.interstitial`) don't.

**Precedence:** `setAdFree(true)` > `AdSettings.adsEnabled = false` > the per-ad remote value > code.

**Validation:**
- A value that isn't valid JSON is ignored, with a warning.
- An invalid field is dropped, and the rest of the value applies.
- Invalid IDs are skipped one by one; if none is valid, the code's IDs stay.
- Unknown fields are ignored, so an old app version accepts newer JSON.

## 5. The app's own switches: `RemoteValue`

Product decisions (A/B tests, kill switches, launch-run shapes) belong in Firebase too. They use the same fetch as the
ads, through `RemoteValue` in `adroit-firebase`:

```kotlin
val PremiumEnabled: RemoteValue<Boolean> = RemoteValue.boolean("PremiumEnabled", default = false)
// also RemoteValue.long / double / string / json(key, default = JSONObject())

@Serializable data class ScanUnlockConfig(val flow: UnlockFlow = UnlockFlow.REWARDED, val subscribe: SubscribeOpens = SubscribeOpens.PAYWALL)
object ScanUnlock {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }   // tolerant: bad fields fall back
    private val remote = RemoteValue.custom("ScanUnlock", ScanUnlockConfig(), ::parse)
    val current: ScanUnlockConfig get() = remote.value       // never waits; the default until fetched
    internal fun parse(raw: String): ScanUnlockConfig = json.decodeFromString(raw)
}
```
- `value` never blocks. `updates(): Flow<T>` emits whenever the value is activated again.
- **Decisions that shape a launch** (show language? paywall? where is the ad?) should be **read once per launch** and
  held. PhotoRecovery's `LaunchFlow.begin()` stores them in a `@Volatile` field. A fetch landing mid-run must not
  change the run.
- Keep the parser tolerant: `ignoreUnknownKeys` and `coerceInputValues`, with a fallback to the default on any
  exception. A typo in the console must never crash the app.
- Name these parameters the way the ads are named: PascalCase, and never renamed.

## 6. Generating the console values, and phase files

Don't hand-type JSON. Generate it from code:

```kotlin
AdDefinitions.firebaseTemplate(AppAds.entries, AppAds.settings)   // a whole Remote Config template (all ads + AdSettings)
AppAds.HomeBanner.defaults.toJson()                               // one ad's value
AppPaywalls.X.toJson()                                            // one paywall's value (adroit-billing)
```
**The quickest way to get the JSON in a fresh project:** run a debug build. Logcat (tag `Adroit`) prints
`Firebase parameter <Name>: {…}` for each ad. Copy each value into its own console parameter. Alternatively, print
`AdDefinitions.firebaseTemplate(...)` from a unit test, which needs Robolectric. That output is a whole Remote Config
template: `{"parameters": {"<Name>": {"defaultValue": {"value": "<json>"}, "valueType": "JSON"}, …}}`.

**When the Firebase project already has parameters:** add the ad parameters one by one, in **Add parameter**, and
don't import a file.

**Importing a template ("Publish from a file") replaces every parameter in the project.** So the file must contain
everything the app reads: the ads, the paywalls, `AppUpdate`, `AdroitBilling`, and the app's own `RemoteValue`s.

**The PhotoRecovery pattern: phase files generated by a test.** Put one file per rollout phase in `docs/firebase/`,
for example:
- `phase1-ads-only.json`
- `phase2-premium-for-testers.json`
- `phase3-premium-on.json`

A Robolectric test builds each file from code, compares it to the committed one, rewrites it if it differs, and fails
once, so that every default change ships with a reviewed config file. A second test parses each file's values back
with the app's own parsers. The skeleton:

```kotlin
@RunWith(RobolectricTestRunner::class) @Config(application = Application::class)
class RemoteConfigFilesTest {
    @Test fun `phase files hold every parameter at the app's values`() {
        val stale = listOfNotNull(check("phase1-ads-only.json", template(selling = "false")), /* … */)
        if (stale.isNotEmpty()) fail("Rewrote ${stale.joinToString()} in docs/firebase: review and commit them")
    }
    private fun template(selling: String): JsonObject {
        val ads = Json.parseToJsonElement(AdDefinitions.firebaseTemplate(AppAds.entries, AppAds.settings))
            .jsonObject.getValue("parameters").jsonObject
        return buildJsonObject { putJsonObject("parameters") {
            put("PremiumEnabled", parameter(selling, "BOOLEAN", "Sell premium"))
            put("AppUpdate", parameter("""{"type":"flexible","forceBelowVersionCode":0}""", description = "Play update"))
            AppPaywalls.entries.forEach { put(it.name, parameter(Json.parseToJsonElement(it.toJson()).toString(), description = "Paywall ${it.name}")) }
            ads.forEach { (name, value) -> put(name, value) }
        } }
    }
    // parameter(value, type = "JSON", description, testers = null) → {"defaultValue":{"value":…},"valueType":…,"description":…}
    // plus "conditionalValues" for a testers-only condition:
    // {"name":"Internal testers","expression":"app.firebaseInstallationId in ['…']"}
}
```
- **Always run that test after changing any default.** A stale phase file publishes old values. PhotoRecovery once
  shipped phase files whose paywall plans disagreed with `AppBilling.kt`.
- `RemoteConfigSession` (`adroit-remote-config`) is needed only for a non-default `FirebaseRemoteConfig` instance.

## 7. Quick-change table (give this to the marketer)

| To… | Parameter | Value |
|---|---|---|
| Turn one ad off | the ad's | `{ "enabled": false }` |
| Turn every ad off | `AdSettings` | `{ "adsEnabled": false }` |
| An interstitial every 3rd time | the ad's | `{ "counter": 3 }` |
| 60 s between shows instead | the ad's | `{ "counter": 0, "timerSeconds": 60 }` |
| Two ad units in turn | the ad's | `{ "adUnitIds": ["ca-app-pub-…/111", "ca-app-pub-…/222"] }` |
| A native ad instead of a banner | the banner's | `{ "format": "native", "style": "large_button_bottom", "adUnitIds": ["<native unit>"] }` |
| The splash without its ad | `SplashAd` | `{ "enabled": false }` |
| App open less often | `AppOpenAd` | `{ "timerSeconds": 240 }` |
| A gap between all full-screen ads | `AdSettings` | `{ "minSecondsBetweenFullScreenAds": 30 }` |
| Force everyone below version 42 to update | `AppUpdate` | `{ "type": "flexible", "forceBelowVersionCode": 42 }` |
