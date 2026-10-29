# Splash, consent, in-app update

## Contents
1. What the splash does
2. Compose splash (Activity + ViewModel)
3. XML splash
4. Variants: no splash ad, a launch interstitial after onboarding, cold-start app open, the system splash
5. Consent and privacy options
6. The in-app update (`adroit-update`)

## 1. What the splash does, in order, and why

1. **The Play update check** (`AdroitUpdate.check`) runs first. A forced update must not let consent or an ad start.
2. **Consent** (UMP), only where the user's region needs it. The splash waits for the answer, up to
   `consentTimeoutSeconds` for the form to appear.
3. **The timer and the splash ad.**
   - A progress bar runs for `splashSeconds`, and the splash ad shows if it loads in time.
   - Premium users, users offline, an ad turned off, and users with no consent get a short splash with no ad:
     `premiumSplashSeconds` or `offlineSplashSeconds` (3 s each).
4. **Routing:** to the launch paywall or onboarding, then to the main screen.

The splash never gets stuck, and a rotation never restarts it, because everything lives in the ViewModel. The timer
pauses while the splash is in the background. On Android 15+ a background app's network is blocked, so the ad is
requested only once the splash is in front.

`SplashFlow.create(application, splashAd: AdDefinition?, isPremium: Boolean = Adroit.isAdFree)`

`SplashState` is one of:
- `Preparing`: the update check and consent. The form may be on screen, so draw no progress.
- `Loading(progress 0..100)`
- `ShowingAd`
- `Finished(adShown)`

## 2. Compose

```kotlin
class SplashViewModel(application: Application) : AndroidViewModel(application) {
    // A premium user skips consent and the ad: SplashFlow reads Adroit.isAdFree, which the billing bridge has
    // already set synchronously in Application.onCreate.
    val flow = SplashFlow.create(application, AppAds.SplashAd)
    private var update: Job? = null

    /** Once per launch: a recreated Activity waits for the same check. */
    suspend fun checkForUpdate(activity: Activity) {
        val job = update ?: viewModelScope.launch { AdroitUpdate.check(activity) }.also { update = it }
        job.join()
    }

    override fun onCleared() = flow.cancel()
}

/** Its own Activity, never a navigation destination: the app open ad excludes it by class name. */
@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {
    private val viewModel: SplashViewModel by viewModels()
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Optional: preload what the launch run shows next (a language-screen native, a paywall banner).
        // AdSlots.load(AppAds.LanguageNative)
        lifecycleScope.launch {
            viewModel.checkForUpdate(this@SplashActivity)   // first: a forced update blocks everything
            viewModel.flow.start(this@SplashActivity)       // after a rotation, hands the new Activity to the running flow
        }
        setContent {
            AppTheme {
                val state by viewModel.flow.state.collectAsStateWithLifecycle()
                SplashScreen(progress = when (val s = state) {
                    SplashState.Preparing -> null                 // consent form may be up: no bar
                    is SplashState.Loading -> s.progress / 100f
                    SplashState.ShowingAd, is SplashState.Finished -> 1f
                })
            }
        }
        lifecycleScope.launch {
            // RESUMED, not STARTED: never navigate from the background (a paused Activity is still STARTED).
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.flow.state.collect { if (it is SplashState.Finished) goToMain() }
            }
        }
    }

    private fun goToMain() {
        if (navigated) return
        navigated = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
```
**A launch paywall over the splash**, with billing, is drawn inside this Activity before `goToMain`. See the
adroit-billing skill, `scenarios.md` → "Launch paywall".

## 3. XML

Same ViewModel. Without `adroit-update`, drop `checkForUpdate`, and call `viewModel.flow.start(this)` directly in
`onCreate`. `flow.start` is a plain (non-suspend) call, and `SplashState.Loading.progress` is an `Int` from 0 to 100.

The layout, `res/layout/activity_splash.xml`, holds the app's mark plus the progress group the code toggles:
```xml
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent">
    <ImageView android:layout_width="96dp" android:layout_height="96dp" android:layout_gravity="center"
        android:src="@mipmap/ic_launcher" android:contentDescription="@string/app_name" />
    <LinearLayout android:id="@+id/progress_group" android:layout_width="match_parent"
        android:layout_height="wrap_content" android:layout_gravity="bottom" android:layout_margin="48dp"
        android:orientation="vertical" android:visibility="gone">
        <ProgressBar android:id="@+id/progress" style="?android:attr/progressBarStyleHorizontal"
            android:layout_width="match_parent" android:layout_height="wrap_content" android:max="100" />
    </LinearLayout>
</FrameLayout>
```
The splash theme can be the app's normal theme (`Theme.MyApp`). Use a separate `Theme.MyApp.Splash` only for an
Android 12+ `Theme.SplashScreen` (§4).

The Activity sets the layout and maps each state to the views:
```kotlin
class SplashActivity : AppCompatActivity() {
    private val viewModel: SplashViewModel by viewModels()
    private var navigated = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        lifecycleScope.launch {
            viewModel.checkForUpdate(this@SplashActivity)
            viewModel.flow.start(this@SplashActivity)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.flow.state.collect { state ->
                    when (state) {
                        SplashState.Preparing -> showProgress(null)
                        is SplashState.Loading -> showProgress(state.progress)
                        SplashState.ShowingAd -> showProgress(100)
                        is SplashState.Finished -> goToMain()
                    }
                }
            }
        }
    }
    private fun showProgress(percent: Int?) {
        findViewById<View>(R.id.progress_group).isVisible = percent != null
        if (percent != null) findViewById<ProgressBar>(R.id.progress).progress = percent
    }
    private fun goToMain() { if (navigated) return; navigated = true; startActivity(Intent(this, MainActivity::class.java)); finish() }
}
```

## 4. Variants

**No splash ad, but keep the splash** (update, consent, timer). There are two ways:
- Firebase: set `SplashAd` to `{ "enabled": false }`.
- Code: `SplashFlow.create(app, null)`.

The splash then lasts `premiumSplashSeconds`.

**A launch interstitial after onboarding, instead of a splash ad.** This is PhotoRecovery's `LaunchFlow`. When the
first launch runs language selection, onboarding or a paywall, a splash ad before all that is wasted, or annoying.
Decide this once per launch, in the splash:
```kotlin
val adOnSplash = remoteFlow.ad == "splash" || !(steps.language || steps.paywall)   // a Firebase RemoteValue.custom
val flow = SplashFlow.create(app, AppAds.SplashAd.takeIf { adOnSplash })
```
- When `adOnSplash` is false:
  1. Load `AppAds.LaunchInterstitial` as the last onboarding screen opens.
  2. On the way to Home, call `AdSlots.showFullScreen(activity, AppAds.LaunchInterstitial) { goHome() }`.
- `LaunchInterstitial` can share the splash's ad unit. It is a plain interstitial (`splash = false`), so it counts
  toward the interstitial limits.
- Drive it from a JSON `RemoteValue`, read once per launch (`remote-config.md` §5). The marketer can then compare the
  two placements.

**Cold-start app open instead of an interstitial.** There are two ways:
- Firebase: swap `SplashAd` to `{ "format": "app_open", "adUnitIds": ["<app open unit>"] }`.
- Code: without `SplashFlow`, call `Adroit.start(this) { Adroit.appOpen.showOnColdStart(this, 4.seconds) { goToMain() } }`.

**Without `SplashFlow`, the lowest level:**
`Adroit.start(this) { AdSlots.showOnSplash(this, AppAds.SplashAd) { goToMain() } }`. In this mode you own the timer,
the rotation handling and the "navigate only when resumed" rule.

**The system splash (Android 12+ `SplashScreen`)**, if the app has one:
- Give `SplashActivity` a `Theme.SplashScreen` theme.
- Draw the same mark in the Compose splash, so the hand-off doesn't jump.

## 5. Consent and privacy options

What the library does:
- `start()` (inside `SplashFlow`) asks UMP for consent and shows the form when it is required.
- No ad is requested until `canRequestAds` is true.
- Consent is re-checked on every start.

What the app must do:
1. **Publish the messages in AdMob → Privacy & messaging:** a European regulations (GDPR) message, and a US state
   regulations message if the app serves those states. Without one, no form ever shows, and EEA users get no ads.
2. **Show "Privacy options"** in Settings while it is required:
```kotlin
// Compose
val required by Adroit.privacy.isPrivacyOptionsRequired.collectAsStateWithLifecycle()
if (required) TextButton(onClick = { activity?.let { Adroit.privacy.showPrivacyOptions(it) } }) {   // or the app's own row component
    Text(stringResource(R.string.privacy_options))
}
```
```kotlin
// Views
lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) {
    Adroit.privacy.isPrivacyOptionsRequired.collect { privacyButton.isVisible = it } } }
privacyButton.setOnClickListener { Adroit.privacy.showPrivacyOptions(this) }
```
   - **Without a Settings screen:** use an overflow menu item. Toggle its visibility from the same flow, in
     `onPrepareOptionsMenu` with `invalidateOptionsMenu()` on each change.
3. **Firebase Consent Mode:** Firebase must be initialized before `start()`. The google-services plugin does this
   automatically.

**Testing:**
- A debuggable build that isn't on PRODUCTION behaves as if it were in the EEA, so the form shows everywhere. Turn
  this off with `privacy { debugBuildGeography = DebugGeography.DISABLED }`.
- To force a region: `privacy { debugGeography = DebugGeography.REGULATED_US_STATE }`, or `EEA` or `OTHER`.
- Until it is answered, the form shows on every launch. To see it again after answering, clear the app's data or
  call `Adroit.privacy.resetConsentForTesting()`, in debug only.
- `Adroit.privacy.consentState` exposes `status`, `canRequestAds`, `privacyOptionsRequired` and `lastError`.

**Audience:**
- `privacy { ageTreatment = AgeTreatment.CHILD }` or `TEEN`.
- `maxAdContentRating = MaxAdContentRating.PG`.
- `restrictedDataProcessing = true`.

These are policy decisions: ask the user, never guess.

## 6. In-app update (`adroit-update`)

```kotlin
AdroitUpdate.check(activity, default = UpdateType.FLEXIBLE)   // suspend; returns when the app may continue
AdroitUpdate.readyToInstall: StateFlow<Boolean>               // a flexible update finished downloading
AdroitUpdate.install()                                        // "Restart": Play installs and restarts the app
```
- Firebase parameter `AppUpdate`: `{ "type": "flexible" | "force" | "off", "forceBelowVersionCode": 0 }`.
  - `flexible`: Play's prompt. The download happens in the background, then the app offers "Restart".
  - `force`: a full-screen update. Backing out closes the app, so `check` never returns.
  - `forceBelowVersionCode > 0`: every installed `versionCode` below it is forced, even when `type` is `flexible`.
  - **Every release must raise `versionCode`.**
- Run it in the splash ViewModel, before `flow.start` (section 2).
- Offer the restart from the main screen:
```kotlin
@Composable
fun UpdateSnackbar(snackbars: SnackbarHostState) {
    LaunchedEffect(snackbars) {
        AdroitUpdate.readyToInstall.first { it }
        if (snackbars.showSnackbar(updateReadyText, actionLabel = restartText, duration = SnackbarDuration.Indefinite)
            == SnackbarResult.ActionPerformed) AdroitUpdate.install()
    }
}
```
- Debug and sideloaded builds return from `check` at once. To test the real flows:
  1. Upload two builds to Internal App Sharing or the internal track, the second with a higher `versionCode`.
  2. Install the first one from Play.
  3. Open it.
- When the update opens Play, the app goes to the background. In apps with the app open skip pattern, nothing extra
  is needed: the splash is excluded anyway.
