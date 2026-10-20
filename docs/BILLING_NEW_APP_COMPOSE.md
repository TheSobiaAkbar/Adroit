# Adding billing to a Jetpack Compose app

This page takes a Compose app from no billing to a first purchase:

- subscriptions, a lifetime product and coins;
- a paywall on the first screen, which Firebase can change without a release;
- a paywall built from your own layout, and a store screen with a completely custom UI;
- an "Upgrade" row in settings, "Restore purchases" and "Manage subscription";
- premium users ad-free, if the app also uses Adroit ads;
- unit tests without Google Play.

Your app needs `adroit-billing-compose` (the `Paywall` composable) and `adroit-billing-firebase` (Firebase Remote
Config and Analytics). They bring `adroit-billing` and Google Play Billing. The XML version of this page:
[Adding billing to a Kotlin + XML app](BILLING_NEW_APP_XML.md).

**Contents**

1. [Gradle](#1-gradle)
2. [The one file: Billing.kt](#2-the-one-file-billingkt)
3. [Initialize](#3-initialize)
4. [The ads bridge](#4-the-ads-bridge)
5. [Show a paywall](#5-show-a-paywall)
6. [A custom paywall](#6-a-custom-paywall)
7. [Restore and manage](#7-restore-and-manage)
8. [Consumables](#8-consumables)
9. [Testing](#9-testing)

Before you start, create the products in Play Console, or start in demo mode and create them later:
[Play Console setup](PLAY_CONSOLE_BILLING_SETUP.md).

## 1. Gradle

Check that `settings.gradle.kts` has `mavenCentral()` in `dependencyResolutionManagement.repositories`, then:

```kotlin
// app/build.gradle.kts
android {
    buildFeatures { buildConfig = true } // for BuildConfig.DEBUG in step 3; off by default since AGP 8
}
dependencies {
    implementation("io.github.thesobiaakbar:adroit-billing-compose:1.0.0-rc5")
    implementation("io.github.thesobiaakbar:adroit-billing-firebase:1.0.0-rc5")
    testImplementation("io.github.thesobiaakbar:adroit-billing-testing:1.0.0-rc5")
}
```

`adroit-billing-firebase` needs Firebase in the app: the `com.google.gms.google-services` plugin and
`app/google-services.json`, as in [Adding ads to a new Compose app](NEW_APP_COMPOSE.md). Without them the app still
works: the paywalls use their in-code values, and a log line says Firebase isn't there.

`adroit-billing-compose` uses the Compose BOM 2026.09 and asks for compileSdk 37. Don't add
`com.android.billingclient:billing` yourself: `adroit-billing` brings the version it's tested with.

**Check:** the project syncs and builds.

## 2. The one file: Billing.kt

Create `billing/Billing.kt` with everything the app sells and every paywall. It's the same file as in an XML app.

<!-- snippet: docs-snippets/src/billing/kotlin/io/github/thesobiaakbar/adroit/docs/billing/Billing.kt#billing-enum -->
```kotlin
/** Everything the app sells. The IDs are the product IDs in Play Console. */
enum class AppProducts(override val defaults: ProductSpec) : ProductDefinition {
    /** A subscription with base plans "monthly" and "yearly" in Play Console. */
    PremiumSub(ProductSpec.subscription("premium_sub", entitlements = setOf("premium"))),
    PremiumLifetime(ProductSpec.nonConsumable("premium_lifetime", entitlements = setOf("premium"))),
    Coins100(ProductSpec.consumable("coins_100")),
}

/** Every paywall. Each name is also its Firebase parameter: a JSON value there changes it without a release. */
enum class AppPaywalls(override val defaults: PaywallSpec) : PaywallDefinition {
    Onboarding(
        PaywallSpec(
            plans = listOf(
                PlanSpec(AppProducts.PremiumSub, basePlanId = "yearly", badge = "BEST_VALUE", savingsVs = "monthly"),
                PlanSpec(AppProducts.PremiumSub, basePlanId = "monthly", offer = OfferRule(tags = listOf("trial"))),
                PlanSpec(AppProducts.PremiumLifetime),
            ),
            close = CloseBehavior(delaySeconds = 3, showCountdown = true, back = BackBehavior.AFTER_DELAY),
            show = ShowRules(maxPerSession = 1),
            texts = PaywallTexts(title = "Go Premium", features = listOf("No ads", "Every level unlocked")),
        ),
    ),
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
    Coins(
        PaywallSpec(
            plans = listOf(PlanSpec(AppProducts.Coins100)),
            template = PaywallTemplate.SINGLE,
            showRestore = false,
            successAction = SuccessAction.STAY_WITH_MESSAGE,
        ),
    ),
}
```

- **Products.** The string is the product ID in Play Console. An **entitlement** (`"premium"`) is what a product
  unlocks; your code asks only for the entitlement (`has("premium")`). A consumable unlocks nothing: the app credits
  it (step 8).
- **Paywalls.** Each entry's name is its Firebase parameter, so never rename one after release. Every field is
  explained in [Paywalls in Firebase](PAYWALL_REMOTE_CONFIG.md); in code the names are the same.

**Check:** the app builds. In a debug build, `initialize` (next step) throws with every problem it finds in the file.

## 3. Initialize

In your `Application` class (registered with `android:name` in the manifest):

<!-- snippet: docs-snippets/src/billing/kotlin/io/github/thesobiaakbar/adroit/docs/billing/BillingApp.kt#billing-app -->
```kotlin
class MyApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        AdroitBilling.initialize(
            this,
            BillingSettings(
                products = AppProducts.entries,
                paywalls = AppPaywalls.entries,
                config = FirebaseBillingConfig.source(), // the paywalls' Firebase JSON
                events = listOf(FirebaseBillingEvents(this)), // purchases and paywalls in Analytics
                demo = DemoBilling(BuildConfig.DEBUG), // made-up products until Play Console has yours
            ),
        )
        // Premium means no ads: the one line between billing and ads.
        appScope.launch { AdroitBilling.entitlements.collect { Adroit.setAdFree(it.adFree) } }
        Coins.start(this, appScope)
    }
}
```

- `initialize` is cheap and synchronous: it reads the saved entitlements and contacts Play in the background.
- **Demo mode** (`DemoBilling(true)`) replaces Play with made-up products, prices marked "(DEMO)", and a dialog where
  you pick "Buy", "Pending payment" or "Cancel", on any device or emulator. A release build ignores it.
- `Coins.start` is step 8. Remove it if you sell no consumable.

Show Play's in-app messages ("your payment was declined") on the main Activity's screen:

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

**Check:** logcat (tag `AdroitBilling`) shows `DEMO billing: made-up products…` in a debug build.

## 4. The ads bridge

If the app also uses Adroit ads, the `appScope.launch { … Adroit.setAdFree(it.adFree) }` line in step 3 is all it
takes: from the next ad request on, a premium user sees no ad. The saved entitlements are read in `initialize`, so on
the next launch the user is ad-free before the first ad is requested, splash ad included. Without Adroit ads, leave
the line out.

## 5. Show a paywall

A paywall is a composable you show while a state says so:

<!-- snippet: docs-snippets/src/billing/kotlin/io/github/thesobiaakbar/adroit/docs/billing/BillingCompose.kt#billing-show-compose -->
```kotlin
@Composable
fun HomeScreen() {
    // Asked once per screen: true when the show rules (which Firebase can change) allow it now.
    var showPaywall by rememberSaveable {
        mutableStateOf(AdroitBilling.paywalls.shouldShow(AppPaywalls.Onboarding, "onboarding") == ShowDecision.Show)
    }
    // … the screen's content …
    if (showPaywall) {
        Paywall(AppPaywalls.Onboarding, placement = "onboarding", onClose = { showPaywall = false })
    }
}
```

- `shouldShow` checks the show rules (enabled, caps, counter, already premium…). A button the user taps skips it and
  just shows the paywall.
- `"onboarding"` is the **placement**: where in the app it opened. It goes with the purchase to Analytics.
- `Paywall` lays itself out by the template: `plans` and `single` fill the screen, `bottom_sheet` and `dialog` open
  their own window. It handles back and the close button per its `close` settings, keeps its countdown and selection
  across rotation, runs the purchase and calls `onClose` once.
- `onExitPaywall` (another parameter of `Paywall`) gives you the `exitPaywall` to show next, after `onClose`.
- With Navigation Compose, a paywall can also be a destination: `sample-compose` has a `paywall/{name}/{placement}`
  route that pops itself on `onClose` and navigates to the exit paywall.

**Check (demo mode):** the paywall shows "(DEMO)" prices; its X appears after 3 seconds; "Continue", then "Buy
(success)" closes it; `AdroitBilling.entitlements.value.has("premium")` is true, and the next launch doesn't show it.

## 6. A custom paywall

**Your own layout, the library's blocks.** Pass a content lambda: inside it, `Title`, `PlanList`, `CtaButton`,
`Disclosure`, `CloseButton` and the other blocks of `PaywallScope` are already wired to the paywall:

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

Keep `Disclosure()` (the renewal terms) near the button: Google Play requires them to be clear before a purchase.

**A completely custom UI.** Drive your own composables from the paywall's controller. It still reads Firebase, picks
the offers, runs the purchase and reports the placement:

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

- `PriceText` formats Play's prices and the button text the way the built-in paywalls do. Never type a price or a
  trial into the app.
- `rememberPaywallController` keeps the controller in a ViewModel, so rotation keeps it. It doesn't check the show
  rules: call `shouldShow` first when it should.

## 7. Restore and manage

A ViewModel that takes `BillingClientApi` (so step 9 can test it) and keeps no Activity:

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

And the settings rows:

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

- `onUpgrade` shows the `Settings` paywall (step 5's pattern, without `shouldShow`: the user asked).
- **Restore** asks Play again for what the user owns. Purchases are restored automatically at every launch too.
- **Manage** opens Play's subscription page, where users cancel or change payment.
- `entitlements.value.status` is `Fresh` (from Play), `Cached` (offline) or `Unknown` (before the first answer of a
  new install).

## 8. Consumables

A consumable (coins, lives) is bought again and again. The app credits it, then **confirms** it, which lets Play
sell it again. Collect the purchases for the whole app, so a purchase that finishes after its screen closed still
counts:

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

A grant not confirmed (the app was killed in between) is delivered again at the next launch, which is why the wallet
remembers each `orderIdHash`. Show the balance with `Coins.balance.collectAsStateWithLifecycle()`.

**Check (demo mode):** buying coins adds 100, and "Buy more" buys again.

## 9. Testing

`PremiumViewModel` (step 7) takes `BillingClientApi`, so a test passes `FakeBilling` from `adroit-billing-testing`,
on the plain JVM:

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

More in [Testing](TESTING.md#billing-with-adroit-billing-testing). On a device: demo mode first (every flow,
including a pending payment that completes 15 seconds later), then real Play with a license tester on an internal
testing track ([Play Console setup](PLAY_CONSOLE_BILLING_SETUP.md)).

Problems? See [Billing troubleshooting](BILLING_TROUBLESHOOTING.md). The `sample-compose` app does all of the above.

Last verified against: Play Billing Library 9.1.0, 2026-10-20
