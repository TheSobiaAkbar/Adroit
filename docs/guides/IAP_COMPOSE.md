# A subscription app (Jetpack Compose + Kotlin)

This guide takes a new Android Studio project (Jetpack Compose + Kotlin) to a release-ready app with **in-app purchases
only**. Follow it from top to bottom: every file is complete, and every step ends with a check. You don't need any other
guide.

Another kind of app? [AdMob only, Jetpack Compose + Kotlin](ADS_COMPOSE.md) · [AdMob only, XML + Kotlin](ADS_XML.md) ·
[in-app purchases only, XML + Kotlin](IAP_XML.md) · [AdMob + in-app purchases, Jetpack Compose +
Kotlin](ADS_IAP_COMPOSE.md) · [AdMob + in-app purchases, XML + Kotlin](ADS_IAP_XML.md). Not sure: [Getting
started](../GETTING_STARTED.md).

**Contents**

1. [What you'll have at the end](#1-what-youll-have-at-the-end)
2. [Dependencies](#2-dependencies)
3. [Firebase project setup](#3-firebase-project-setup)
4. [The enums](#4-the-enums)
5. [The Application class](#5-the-application-class)
6. [The splash screen](#6-the-splash-screen)
7. [The screens](#7-the-screens)
8. [Firebase Remote Config: what to paste](#8-firebase-remote-config-what-to-paste)
9. [Test it](#9-test-it)
10. [Release checklist](#10-release-checklist)

## 1. What you'll have at the end

- A splash screen that checks Google Play for an update, lets subscribers straight in, and shows everyone else a paywall
  they can't skip: subscribe, restore, or leave.
- Monthly and yearly subscriptions and a lifetime purchase, with "Upgrade", "Restore purchases" and "Manage
  subscription" in Settings.
- Every paywall and the app update controlled from Firebase, without a new release.

**Before you start**

| What | Where | Example |
|---|---|---|
| Your app's package name | `applicationId` in `app/build.gradle.kts` | `com.example.myapp` |
| Your products | Play Console → your app → Monetize ([Play Console setup](../PLAY_CONSOLE_BILLING_SETUP.md)) | `premium_sub`, `premium_lifetime` |
| A Firebase project | [console.firebase.google.com](https://console.firebase.google.com) | |

You can start without the IDs and products: debug builds use demo billing (made-up products) until Play Console has
yours.

## 2. Dependencies

Adroit is on Maven Central: no account and no token. Check that `settings.gradle.kts` lists `mavenCentral()`, as a new
Android Studio project already does.

This setup needs these Adroit artifacts, and only these:

| Artifact | What for |
|---|---|
| `adroit-billing` | products, purchases, entitlements and paywall rules; brings Google Play Billing |
| `adroit-billing-compose` | the `Paywall` composable |
| `adroit-billing-firebase` | every paywall controlled from Firebase, and purchase events in Analytics |
| `adroit-update` | Google Play's in-app update, controlled by the Firebase `AppUpdate` parameter |

Don't add `play-services-ads`, `ads-mobile-sdk` or `com.android.billingclient:billing` yourself: the Adroit artifacts
bring the versions they are tested with.

In `gradle/libs.versions.toml`, add to the existing sections:

```toml
[versions]
adroit = "1.0.0-rc6"
googleServices = "4.5.0"

[libraries]
adroit-billing = { module = "io.github.thesobiaakbar:adroit-billing", version.ref = "adroit" }
adroit-billing-compose = { module = "io.github.thesobiaakbar:adroit-billing-compose", version.ref = "adroit" }
adroit-billing-firebase = { module = "io.github.thesobiaakbar:adroit-billing-firebase", version.ref = "adroit" }
adroit-update = { module = "io.github.thesobiaakbar:adroit-update", version.ref = "adroit" }

[plugins]
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
```

In the root `build.gradle.kts`:

```kotlin
plugins {
    // ... the plugins Android Studio put here
    alias(libs.plugins.google.services) apply false
}
```

In `app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services) // reads app/google-services.json
}

android {
    // ... namespace, defaultConfig and the rest, as Android Studio wrote them
    compileSdk = 37 // Compose 1.12 (the Compose BOM below) needs it
    defaultConfig {
        minSdk = 24 // Adroit needs 24 or higher
    }
    buildFeatures {
        compose = true
        buildConfig = true // for BuildConfig.DEBUG
    }
}

dependencies {
    implementation(libs.adroit.billing)
    implementation(libs.adroit.billing.compose)
    implementation(libs.adroit.billing.firebase)
    implementation(libs.adroit.update)

    // Your usual Compose libraries (the "Empty Activity" template adds most of them):
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
}
```

(Your plugin aliases may have other names: keep the ones Android Studio wrote, and add `google-services`.)

In `app/src/main/AndroidManifest.xml`, register the Application class (step 5) and make the splash (step 6) the
launcher:

```xml
<application
    android:name=".IapApp"
    ...>

    <activity
        android:name=".SplashActivity"
        android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
    <activity android:name=".MainActivity" />
</application>
```

Remove the `intent-filter` Android Studio put on `MainActivity`. The libraries add the permissions they need (internet,
billing) themselves.

**Check:** *Sync Project with Gradle Files* succeeds. If an Adroit artifact isn't found, check `mavenCentral()` in
`settings.gradle.kts` and the version number.

## 3. Firebase project setup

1. In the [Firebase console](https://console.firebase.google.com), create a project (or open yours). Google Analytics:
   yes (purchase and paywall events go there).
2. **Add app** → Android, with your **exact** package name, then **Register app**.
3. Download `google-services.json` into your project's `app/` folder.
4. Skip the console's "Add Firebase SDK" step: the Adroit artifacts bring the Firebase libraries.

**Debug builds with another package name.** If your debug build type has an `applicationIdSuffix` (for example
`.debug`), register that package as a second Android app in the same Firebase project, and download
`google-services.json` again: one file holds both apps. Both read the same Remote Config parameters.

Remote Config needs nothing else in code: the first Adroit Firebase module the app uses fetches it once per launch, in
the background, for the paywalls and the app update. Nothing waits for it. Step 8 lists the parameters to create.

**Check:** the app builds and starts. Logcat (tag `AdroitRemoteConfig`) shows `Remote Config fetched and activated` once
per launch.

## 4. The enums

Everything the app shows or sells is listed once, in enums. **Each entry's name is also its Firebase Remote Config
parameter**: a JSON value under that name in Firebase changes that one entry, without a new release. So pick names that
say what the entry is, and never rename one after release.

**What you sell and the paywalls**, in `billing/Billing.kt`:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/guides/iap/IapBilling.kt#guide-iap-enum -->
```kotlin
/** Everything the app sells. The IDs are the product IDs in Play Console. */
enum class AppProducts(override val defaults: ProductSpec) : ProductDefinition {
    /** A subscription with base plans "monthly" and "yearly" in Play Console. */
    PremiumSub(ProductSpec.subscription("premium_sub", entitlements = setOf("premium"))),
    PremiumLifetime(ProductSpec.nonConsumable("premium_lifetime", entitlements = setOf("premium"))),
}

/** Every paywall. Each name is also its Firebase parameter: a JSON value there changes it without a release. */
enum class AppPaywalls(override val defaults: PaywallSpec) : PaywallDefinition {
    /** The hard paywall at launch: no close button, and back doesn't close it. Buy, restore, or leave the app. */
    Subscribe(
        PaywallSpec(
            plans = listOf(
                PlanSpec(AppProducts.PremiumSub, basePlanId = "yearly", badge = "BEST_VALUE", savingsVs = "monthly"),
                PlanSpec(AppProducts.PremiumSub, basePlanId = "monthly", offer = OfferRule(tags = listOf("trial"))),
                PlanSpec(AppProducts.PremiumLifetime),
            ),
            close = CloseBehavior(delaySeconds = -1, back = BackBehavior.NEVER),
            texts = PaywallTexts(title = "Subscribe to continue", features = listOf("Every feature", "Cancel anytime")),
        ),
    ),

    /** Opened from Settings, to change plans. */
    Settings(
        PaywallSpec(
            plans = listOf(
                PlanSpec(AppProducts.PremiumSub, basePlanId = "monthly"),
                PlanSpec(AppProducts.PremiumSub, basePlanId = "yearly", savingsVs = "monthly"),
                PlanSpec(AppProducts.PremiumLifetime),
            ),
            showManage = true,
        ),
    ),
}
```

- **Products.** The string is the product ID in Play Console. An **entitlement** (`"premium"`) is what a product
  unlocks; several products can unlock the same one, and your code asks only for the entitlement:
  `AdroitBilling.entitlements.value.has("premium")`.
- **Paywalls.** `basePlanId` is a base plan of the subscription in Play Console; `OfferRule(tags = …)` picks the offer
  with those tags when Play gives this user one. Every field is explained, for Firebase, in [Paywalls in
  Firebase](../PAYWALL_REMOTE_CONFIG.md); in code the names are the same.
- **`Subscribe` is a hard paywall:** `CloseBehavior(delaySeconds = -1, back = BackBehavior.NEVER)` means no close button
  and no back. The user subscribes, restores, or leaves the app. A paywall that can't load (no internet) always shows a
  close button, so nobody is stuck. Google Play's policies and users dislike hard paywalls, especially on a first
  launch: read [The close button](../PAYWALL_REMOTE_CONFIG.md#the-close-button) before you ship one.

**Check:** the app builds. In a debug build, `AdroitBilling.initialize` (next step) throws with every problem it finds
in these enums (an unknown product in a paywall, duplicate IDs…).

## 5. The Application class

`IapApp.kt`, next to your activities, registered in the manifest in step 2:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/guides/Apps.kt#guide-iap-app -->
```kotlin
class IapApp : Application() {
    // For app-wide work, such as crediting consumables (step 7). Remove it if you sell none.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // Firebase Remote Config: debug builds see a console change on the next launch. Adroit fetches it once per
        // launch, in the background: nothing waits for it.
        FirebaseRemoteConfig.getInstance().setConfigSettingsAsync(
            remoteConfigSettings {
                if (BuildConfig.DEBUG) minimumFetchIntervalInSeconds = 0
                fetchTimeoutInSeconds = 10
            },
        )
        // Billing: reads the saved purchases at once (a subscriber is a subscriber from the first screen) and asks
        // Google Play in the background.
        AdroitBilling.initialize(
            this,
            BillingSettings(
                products = IapProducts.entries,
                paywalls = IapPaywalls.entries,
                config = FirebaseBillingConfig.source(), // the paywalls' Firebase JSON
                events = listOf(FirebaseBillingEvents(this)), // purchases and paywalls in Analytics
                demo = DemoBilling(BuildConfig.DEBUG), // made-up products until Play Console has yours
            ),
        )
        // Consumables (step 7): Coins.start(this, appScope)
    }
}
```

- **Demo billing** (`DemoBilling(BuildConfig.DEBUG)`) replaces Google Play with made-up products, prices marked
  "(DEMO)", and a dialog where you pick "Buy", "Pending payment" or "Cancel". It works on any device and emulator before
  Play Console has your products. A release build ignores it.
- There's no Remote Config fetch call: the Adroit Firebase modules start one, once per launch.

**Check:** logcat (tag `AdroitBilling`) shows `DEMO billing: made-up products…` in a debug build.

## 6. The splash screen

The splash runs everything at app start, and always ends somewhere:

1. **The update check:** Google Play's update, as the Firebase `AppUpdate` parameter says (flexible by default). A user
   who refuses a forced update leaves the app.
2. **The routing:** a subscriber goes straight to the main screen; anyone else gets the `Subscribe` paywall. Subscribing
   (or restoring) opens the main screen; leaving the paywall leaves the app.

It never gets stuck, even offline, and a rotation never restarts it: everything is kept in its ViewModel.

The splash's look, `SplashScreen.kt` (change it as you like):

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/guides/SplashScreen.kt#guide-splash-screen-compose -->
```kotlin
/** The splash's look: the app's name, and the timer's progress (0..100) once there is one. */
@Composable
fun SplashScreen(percent: Int?) {
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Text("MyApp", Modifier.align(Alignment.Center), style = MaterialTheme.typography.headlineMedium)
            if (percent != null) {
                Column(
                    Modifier.align(Alignment.BottomCenter).padding(start = 48.dp, end = 48.dp, bottom = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("$percent%")
                    LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** The progress bar for a [SplashState]: none while the update check and consent run, then 0..100. */
fun SplashState.percent(): Int? = when (this) {
    SplashState.Preparing -> null
    is SplashState.Loading -> progress
    SplashState.ShowingAd, is SplashState.Finished -> 100
}
```

`SplashActivity.kt`, with its ViewModel:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/guides/iap/compose/IapSplashCompose.kt#guide-iap-splash-compose -->
```kotlin
/** Where the splash is. Compose state in the ViewModel: a rotation keeps it, the screen draws it. */
enum class SplashStep { Checking, Paywall, Main, Leave }

/** Keeps the splash across rotations: the update check and the paywall happen once per launch. */
class SplashViewModel(application: Application) : AndroidViewModel(application) {
    var step by mutableStateOf(SplashStep.Checking)

    private var update: Job? = null

    /** Google Play's update check, once per launch; then the hard paywall unless the user is subscribed. */
    fun start(activity: Activity) {
        if (update != null) return
        update = viewModelScope.launch {
            AdroitUpdate.check(activity) // a forced update closes the app if declined
            step = if (isSubscribed()) SplashStep.Main else SplashStep.Paywall
        }
    }

    /** Bought or restored: in. Otherwise the user left the hard paywall: leave the app. */
    fun onPaywallClosed() {
        step = if (isSubscribed()) SplashStep.Main else SplashStep.Leave
    }

    /** The saved purchases, read in Application.onCreate, then kept up to date with Google Play. */
    private fun isSubscribed() = AdroitBilling.entitlements.value.has("premium")
}

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {
    private val viewModel: SplashViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.start(this)
        setContent {
            MaterialTheme {
                SplashScreen(percent = null)
                // The hard paywall, drawn over the splash. It has no close button and back doesn't close it.
                if (viewModel.step == SplashStep.Paywall) {
                    Paywall(AppPaywalls.Subscribe, placement = "launch", onClose = { viewModel.onPaywallClosed() })
                }
            }
        }
        lifecycleScope.launch {
            // RESUMED, not STARTED: Android can take seconds to stop an Activity after Home, and the next screen
            // must not open unseen.
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                when (snapshotFlow { viewModel.step }.first { it == SplashStep.Main || it == SplashStep.Leave }) {
                    SplashStep.Main -> startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    else -> Unit
                }
                finish()
            }
        }
    }
}
```

- **Its own Activity, not a navigation destination:** the app open ad excludes screens by Activity, and the consent form
  and the splash ad need an Activity with nothing drawn over it.
- **The update check runs in the ViewModel,** once per launch: after a rotation, the new Activity waits for the same
  check instead of starting a second one.
- **A fresh install of an existing subscriber** may see the paywall before Google Play has answered: its "Restore
  purchases" link lets them in at once.

**Check:** launch a debug build: the splash, then the `Subscribe` paywall with "(DEMO)" prices. "Continue", then "Buy
(success)" opens the main screen; the next launch goes straight there.

## 7. The screens

Your main screen is your app's own: this step adds what billing and the update need in it. Put the Settings rows where
your app's Settings is, and the two messages below on the main screen.

### Settings: upgrade, restore, manage

Show `SettingsScreen()` (below) where your app's Settings is: a tab or a navigation destination.

A ViewModel that keeps no Activity (step 9 tests it):

<!-- snippet: docs-snippets/src/billing/kotlin/io/github/thesobiaakbar/adroit/docs/billing/PremiumViewModel.kt#billing-viewmodel -->
```kotlin
/** Settings' premium rows. It takes BillingClientApi, so a test passes FakeBilling. It keeps no Activity. */
class PremiumViewModel(private val billing: BillingClientApi = AdroitBilling) : ViewModel() {
    private val results = Channel<RestoreResult>(Channel.BUFFERED)

    val entitlements: StateFlow<EntitlementSnapshot> = billing.entitlements

    /** One result per restore(), delivered once, even across a rotation. */
    val restoreResults: Flow<RestoreResult> = results.receiveAsFlow()

    fun restore() {
        viewModelScope.launch { results.send(billing.restore()) }
    }

    fun manage(activity: Activity): Boolean = billing.openManageSubscriptions(activity, AppProducts.PremiumSub)
}
```

The settings rows:

<!-- snippet: docs-snippets/src/billing/kotlin/io/github/thesobiaakbar/adroit/docs/billing/BillingCompose.kt#billing-restore-compose -->
```kotlin
@Composable
fun PremiumRows(onUpgrade: () -> Unit, premium: PremiumViewModel = viewModel()) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val entitlements by premium.entitlements.collectAsStateWithLifecycle()
    LaunchedEffect(premium) {
        premium.restoreResults.collect { result ->
            val message = when (result) {
                is RestoreResult.Restored -> if (result.newlyActive.isEmpty()) "Your purchases are up to date" else "Restored"
                RestoreResult.NothingToRestore -> "Nothing to restore"
                is RestoreResult.Failed -> "Couldn't reach Google Play. Try again later."
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (entitlements.has("premium")) "Premium: active" else "Premium: not active")
        Button(onClick = onUpgrade, modifier = Modifier.fillMaxWidth()) { Text("Upgrade") }
        OutlinedButton(onClick = premium::restore, modifier = Modifier.fillMaxWidth()) { Text("Restore purchases") }
        if (activity != null) {
            OutlinedButton(onClick = { premium.manage(activity) }, modifier = Modifier.fillMaxWidth()) {
                Text("Manage subscription")
            }
        }
    }
}
```

And the screen that shows the `Settings` paywall when "Upgrade" is tapped:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/guides/iap/compose/IapSettingsCompose.kt#guide-settings-compose -->
```kotlin
@Composable
fun SettingsScreen() {
    var upgrade by rememberSaveable { mutableStateOf(false) }
    // "Upgrade", "Restore purchases" and "Manage subscription" (PremiumRows, below).
    PremiumRows(onUpgrade = { upgrade = true })
    // The user asked: show the paywall without its show rules.
    if (upgrade) Paywall(AppPaywalls.Settings, placement = "settings", onClose = { upgrade = false })
}
```

- **Restore** asks Play again for what the user owns, on this Google account. Purchases are restored at every launch
  too; the button is for users who expect one.
- **Manage** opens Play's subscription page, where users cancel or change payment.
- **The status:** collect `AdroitBilling.entitlements`: `has("premium")`, and `status` (`Fresh` from Play, `Cached`
  while offline, `Unknown` before the first answer of a new install).

**Play's messages** ("your payment was declined"), on the main screen:

<!-- snippet: docs-snippets/src/billing/kotlin/io/github/thesobiaakbar/adroit/docs/billing/BillingCompose.kt#billing-inapp-compose -->
```kotlin
@Composable
fun InAppMessages() {
    val activity = LocalActivity.current
    // Play's messages, for example "your payment was declined": shows nothing when there's none.
    LifecycleResumeEffect(activity) {
        activity?.let(AdroitBilling::showInAppMessages)
        onPauseOrDispose {}
    }
}
```

### A custom paywall (optional)

**Your layout, the library's blocks.** Inside `Paywall`'s content lambda, `Title`, `PlanList`, `CtaButton`,
`Disclosure`, `CloseButton` and the other blocks are already wired:

<!-- snippet: docs-snippets/src/billing/kotlin/io/github/thesobiaakbar/adroit/docs/billing/BillingCompose.kt#billing-custom-blocks-compose -->
```kotlin
@Composable
fun UpgradeSheet(onDone: () -> Unit) {
    // The library's blocks in your own order; the template still decides the window (a sheet, a dialog…).
    Paywall(AppPaywalls.Settings, placement = "settings", onClose = { onDone() }) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Title(Modifier.weight(1f))
                CloseButton()
            }
            PlanList()
            Message()
            CtaButton()
            Disclosure()
            RestoreButton(Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
```

Keep `Disclosure()` (the renewal terms) near the button: Google Play requires them before a purchase.

**A completely custom UI.** Drive your own composables from the paywall's controller:

<!-- snippet: docs-snippets/src/billing/kotlin/io/github/thesobiaakbar/adroit/docs/billing/BillingCompose.kt#billing-custom-ui-compose -->
```kotlin
@Composable
fun CoinsStore() {
    // A purchase ends a controller: each "Buy more" opens the paywall again with a new one.
    var round by rememberSaveable { mutableIntStateOf(0) }
    key(round) { CoinsOffer(onBuyMore = { round++ }) }
}

@Composable
private fun CoinsOffer(onBuyMore: () -> Unit) {
    val activity = LocalActivity.current
    val resources = LocalResources.current
    val controller = rememberPaywallController(AppPaywalls.Coins, "coins_screen")
    val state by controller.state.collectAsStateWithLifecycle()
    LaunchedEffect(controller) { controller.onShown() } // on screen now: reported once per opening
    when (val current = state) {
        PaywallUiState.Loading -> CircularProgressIndicator()
        is PaywallUiState.Content -> Column {
            Text(PriceText.planPrice(resources, current.selectedPlan).orEmpty())
            Button(onClick = { activity?.let(controller::purchase) }, enabled = current.busy == null) {
                Text(PriceText.cta(resources, current.selectedPlan))
            }
        }
        is PaywallUiState.Purchased -> Button(onClick = onBuyMore) { Text("Thank you! Buy more") }
        is PaywallUiState.Unavailable -> Text("The store isn't available right now.")
        is PaywallUiState.Closed -> Unit
    }
}
```

`PriceText` formats Play's prices and the button text the way the built-in paywalls do: never type a price or a trial
into the app.

### Consumables (optional)

Coins, lives, credits: add `Coins100(ProductSpec.consumable("coins_100"))` to `AppProducts` and a paywall that sells it.
The app credits each purchase, then **confirms** it, which lets Play sell it again. Collect them for the whole app: in
`IapApp.onCreate`, replace the consumables comment with `Coins.start(this, appScope)`, using the `appScope` already
there:

<!-- snippet: docs-snippets/src/billing/kotlin/io/github/thesobiaakbar/adroit/docs/billing/BillingApp.kt#billing-consumables -->
```kotlin
/** The coins bought with AppProducts.Coins100. A real app keeps the balance on its server. */
object Coins {
    private val mutableBalance = MutableStateFlow(0)
    val balance: StateFlow<Int> = mutableBalance.asStateFlow()

    /** Call once, from Application.onCreate, so a purchase is credited whichever screen is open. */
    fun start(context: Context, scope: CoroutineScope) {
        val prefs = context.getSharedPreferences("coins", Context.MODE_PRIVATE)
        mutableBalance.value = prefs.getInt("balance", 0)
        scope.launch {
            AdroitBilling.consumables.collect { grant ->
                val credited = prefs.getStringSet("credited", emptySet()).orEmpty()
                if (grant.orderIdHash !in credited) { // each order once, even when it is delivered again
                    mutableBalance.value += 100 * grant.quantity
                    prefs.edit(commit = true) {
                        putInt("balance", mutableBalance.value)
                        putStringSet("credited", credited + grant.orderIdHash)
                    }
                }
                grant.confirm() // now Play may sell it again
            }
        }
    }
}
```

A grant not confirmed (the app was killed in between) comes again at the next launch: that's why the wallet remembers
each `orderIdHash`. Credit once, confirm every time.

### The update message

When a flexible update has downloaded in the background, offer the restart that installs it. `UpdateSnackbar.kt`, placed
in the main screen with its `Scaffold`'s snackbar host:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/guides/UpdateSnackbar.kt#guide-update-compose -->
```kotlin
/** In the main screen, with the Scaffold's snackbar host: once a flexible update has downloaded, offers "Restart". */
@Composable
fun UpdateSnackbar(snackbars: SnackbarHostState) {
    LaunchedEffect(snackbars) {
        AdroitUpdate.readyToInstall.first { it }
        val result = snackbars.showSnackbar("An update is ready", actionLabel = "Restart", duration = SnackbarDuration.Indefinite)
        if (result == SnackbarResult.ActionPerformed) AdroitUpdate.install()
    }
}
```

"Restart" installs the update; Google Play restarts the app. More: [In-app update](../APP_UPDATE.md).

## 8. Firebase Remote Config: what to paste

In the Firebase console → **Remote Config**, create each parameter below: **Add parameter**, the name, data type
**JSON**, the JSON as the default value → **Save**. When all are added, **Publish changes**. Users get them on their
next launch.

A value needs only the fields you change: the rest keep the values from your enums. A value with a mistake is ignored
(with a warning in logcat), and the entry keeps its previous values.

### Paywalls

**`AdroitBilling`** (all paywalls at once)

```json
{ "enabled": true, "offlineGraceHours": 72, "configVersion": "2026-10-1" }
```

**`Subscribe`**

```json
{
  "enabled": true,
  "template": "plans",
  "plans": [
    { "product": "PremiumSub", "basePlan": "yearly", "badge": "BEST_VALUE", "savingsVs": "monthly" },
    { "product": "PremiumSub", "basePlan": "monthly", "offer": { "tags": ["trial"] } },
    { "product": "PremiumLifetime" }
  ],
  "close": { "delaySeconds": -1, "back": "NEVER" },
  "texts": { "title": "Subscribe to continue", "features": ["Every feature", "Cancel anytime"] },
  "showRestore": true
}
```

**`Settings`**

```json
{
  "enabled": true,
  "template": "plans",
  "plans": [
    { "product": "PremiumSub", "basePlan": "monthly" },
    { "product": "PremiumSub", "basePlan": "yearly", "savingsVs": "monthly" },
    { "product": "PremiumLifetime" }
  ],
  "showManage": true
}
```

`Subscribe`'s `"close": { "delaySeconds": -1, "back": "NEVER" }` makes it a hard paywall. To soften it later without a
release, publish `{ "close": { "delaySeconds": 5, "back": "AFTER_DELAY" } }`: the user can then close it, and the splash
leaves the app, so pair that with an app that has a free part.

Every paywall field, with examples, for the people who change them: [Paywalls in Firebase](../PAYWALL_REMOTE_CONFIG.md).

### App update

**`AppUpdate`**

```json
{ "type": "flexible", "forceBelowVersionCode": 0 }
```

| `type` | What a user with an older version sees |
|---|---|
| `flexible` (default) | Google Play's update prompt. They may decline and continue; an accepted update downloads while they use the app, then the "Restart" message of step 7. |
| `force` | Google Play's full-screen update. They can't continue without updating; backing out leaves the app. |
| `off` | Nothing. |

`forceBelowVersionCode`: above 0, every installed version below it is forced to update, even when `type` is `flexible`.
Use it to retire old versions. More: [In-app update](../APP_UPDATE.md).

### Your own values

Anything else you want to change from Firebase (a flag, a number, a text): [Your app's own
values](../REMOTE_CONFIG.md#your-apps-own-values).

## 9. Test it

A debug build on a phone or an emulator:

- [ ] **Demo billing:** the paywall shows "(DEMO)" prices. "Continue", then "Buy (success)" closes it, and the next
      launch knows the user is premium. Try "Pending payment" (it completes 15 seconds later) and "Cancel" too.
- [ ] **Real Google Play:** turn demo billing off, add yourself as a license tester, and install from the internal
      testing track. Test cards approve, decline or delay payments without charging you ([Play Console
      setup](../PLAY_CONSOLE_BILLING_SETUP.md)).
- [ ] **The update check** returns at once in a debug build (it isn't installed from Play). To see the real flows,
      upload two builds to Internal App Sharing or the internal testing track, the second with a higher `versionCode`,
      install the first from Play, then open it ([In-app update: Testing](../APP_UPDATE.md#testing)).

**Unit tests without Google Play.** Add
`testImplementation("io.github.thesobiaakbar:adroit-billing-testing:1.0.0-rc6")`. Code that takes `BillingClientApi`
(here `PremiumViewModel`, step 7) gets `FakeBilling` in tests:

<!-- snippet: docs-snippets/src/test/kotlin/io/github/thesobiaakbar/adroit/docs/billing/BillingTestingSnippets.kt#billing-fake-test -->
```kotlin
/** Plain JVM: no Play, no device, no Robolectric. */
@OptIn(ExperimentalCoroutinesApi::class)
class PremiumViewModelTest {
    private val billing = FakeBilling()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `premium follows what the user owns`() {
        val vm = PremiumViewModel(billing)
        assertFalse(vm.entitlements.value.has("premium"))

        billing.own(AppProducts.PremiumLifetime)

        assertTrue(vm.entitlements.value.has("premium"))
    }

    @Test
    fun `restore reports its result once`() = runTest {
        billing.restoreResult = RestoreResult.NothingToRestore
        val vm = PremiumViewModel(billing)

        vm.restore()

        assertEquals(RestoreResult.NothingToRestore, vm.restoreResults.first())
    }
}
```

More in [Testing](../TESTING.md#billing-with-adroit-billing-testing).

## 10. Release checklist

- [ ] Every product is created and **active** in Play Console, with the base plans and offers your paywalls name ([Play
      Console setup](../PLAY_CONSOLE_BILLING_SETUP.md)).
- [ ] The app is uploaded to a testing track, and a license tester bought, restored and cancelled with real Google Play.
      Demo billing can't run in release: the library ignores it there.
- [ ] The Firebase parameters are published, and your `google-services.json` is the release app's.
- [ ] A release build (R8 on, `isMinifyEnabled = true`) starts and works: Adroit ships its own R8 rules, you add none.
- [ ] **Every release raises `versionCode`.** `AppUpdate` compares versions by it: with `forceBelowVersionCode`, set it
      to the first version every user must have.

Problems? [Billing troubleshooting](../BILLING_TROUBLESHOOTING.md).

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, Play Billing Library 9.1.0, Play In-App Updates 2.1.0, 2026-10-21
