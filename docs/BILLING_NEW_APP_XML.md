# Adding billing to a Kotlin + XML app

This page takes an Android app with XML layouts from no billing to a first purchase:

- subscriptions, a lifetime product and coins;
- a paywall after the first screen, which Firebase can change without a release;
- an "Upgrade" button in settings, "Restore purchases" and "Manage subscription";
- premium users ad-free, if the app also uses Adroit ads;
- unit tests without Google Play.

Your app needs `adroit-billing-views` (the paywall screens) and `adroit-billing-firebase` (Firebase Remote Config and
Analytics). They bring `adroit-billing` and Google Play Billing. The Compose version of this page:
[Adding billing to a Jetpack Compose app](BILLING_NEW_APP_COMPOSE.md).

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
    implementation("io.github.thesobiaakbar:adroit-billing-views:1.0.0-rc5")
    implementation("io.github.thesobiaakbar:adroit-billing-firebase:1.0.0-rc5")
    testImplementation("io.github.thesobiaakbar:adroit-billing-testing:1.0.0-rc5")
}
```

`adroit-billing-firebase` needs Firebase in the app: the `com.google.gms.google-services` plugin and
`app/google-services.json`, as in [Adding ads to a new XML app, step 3](NEW_APP_XML.md#3-connect-firebase). Without
them the app still works: the paywalls use their in-code values, and a log line says Firebase isn't there.

Don't add `com.android.billingclient:billing` yourself: `adroit-billing` brings the version it's tested with.

**Check:** the project syncs and builds.

## 2. The one file: Billing.kt

Create `billing/Billing.kt` with everything the app sells and every paywall. This is the only file you edit when
products or paywalls change.

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
  unlocks; several products can unlock the same one, and your code asks only for the entitlement
  (`entitlements.value.has("premium")`). A consumable unlocks nothing: the app credits it (step 8).
- **Paywalls.** Each entry's name (`Onboarding`) is its Firebase parameter, so never rename one after release. Every
  field of `PaywallSpec` and its parts is explained, for Firebase, in [Paywalls in Firebase](PAYWALL_REMOTE_CONFIG.md);
  in code the names are the same.
- `basePlanId` is a base plan ID of the subscription in Play Console. `OfferRule(tags = …)` picks the offer with
  those tags, when Play gives this user one.

**Check:** the app builds. `initialize` (next step) checks the file and, in a debug build, throws with every problem
it finds (an unknown product in a paywall, duplicate IDs…).

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

- `initialize` is cheap and synchronous: it reads the saved entitlements (so a premium user is premium at once) and
  contacts Play in the background.
- **Demo mode** (`DemoBilling(true)`) replaces Play with made-up products, prices marked "(DEMO)", and a dialog where
  you pick "Buy", "Pending payment" or "Cancel". It works on any device and emulator, before Play Console has your
  products. It is ignored in a release build, whatever you pass. Turn it off (or remove the line) to test with Play.
- `Coins.start` is step 8. Remove it if you sell no consumable.

**Check:** logcat (tag `AdroitBilling`) shows `DEMO billing: made-up products…` in a debug build.

## 4. The ads bridge

If the app also uses Adroit ads, the `appScope.launch { … Adroit.setAdFree(it.adFree) }` line in step 3 is all it
takes: from the next ad request on, a premium user sees no ad. The saved entitlements are read in `initialize`, so on
the next launch the user is ad-free before the first ad is requested, splash ad included. `adFree` is
true while the user has any entitlement (the `AdroitBilling` Firebase parameter can narrow it to some, with
`adFreeEntitlements`). Without Adroit ads, leave the line out.

## 5. Show a paywall

Where the paywall should appear (here: the main screen, after the splash):

<!-- snippet: docs-snippets/src/billing/kotlin/io/github/thesobiaakbar/adroit/docs/billing/BillingXml.kt#billing-show-xml -->
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Opens the paywall when its show rules allow it (Firebase can change them), once per screen, not after a rotation.
    if (savedInstanceState == null) AdroitPaywall.show(this, AppPaywalls.Onboarding, "onboarding")
}

override fun onResume() {
    super.onResume()
    // Play's messages, for example "your payment was declined": shows nothing when there's none.
    AdroitBilling.showInAppMessages(this)
}
```

- `AdroitPaywall.show` checks the paywall's show rules (enabled, caps, counter, already premium…) and opens it in its
  own Activity, laid out by its template. It returns the decision: `ShowDecision.Skip` says why it didn't open.
- `"onboarding"` is the **placement**: where in the app it opened. It goes with the purchase to Analytics.
- The paywall handles back and its close button per its `close` settings, survives rotation, runs the purchase and
  closes itself. When the user closes it and it names an `exitPaywall`, that one opens next.

A button the user taps should open the paywall even when the rules would skip it: `force = true` (step 7). To know how
the paywall closed, use the Activity result contract:

<!-- snippet: docs-snippets/src/billing/kotlin/io/github/thesobiaakbar/adroit/docs/billing/BillingXml.kt#billing-contract-xml -->
```kotlin
private val upgrade = registerForActivityResult(AdroitPaywall.contract()) { reason ->
    if (reason == PaywallCloseReason.PURCHASED) Toast.makeText(this, "Welcome to Premium!", Toast.LENGTH_LONG).show()
}

private fun onUpgradeClicked() {
    // The user asked: no show rules, so launch it directly.
    upgrade.launch(PaywallLaunch(AppPaywalls.Settings, "settings_button"))
}
```

You don't need the result to unlock anything: `AdroitBilling.entitlements` already holds the purchase when the
paywall closes.

**Check (demo mode):** the paywall opens with "(DEMO)" prices; its X appears after 3 seconds with a countdown;
"Continue", then "Buy (success)" closes it; `AdroitBilling.entitlements.value.has("premium")` is now true, and the
next launch doesn't show it (`ENTITLED`).

## 6. A custom paywall

**In a layout.** `PaywallView` puts a paywall inside your own screen, for example a coins shop:

<!-- snippet: docs-snippets/src/billing/res/layout/snippet_paywall_view.xml#billing-paywall-view -->
```xml
<io.github.thesobiaakbar.adroit.billing.views.PaywallView
    android:id="@+id/coins_paywall"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:adroit_billing_paywall="Coins"
    app:adroit_billing_placement="coins" />
```

Give it an id (it keeps its state across rotations by id), and say what happens when it closes:

<!-- snippet: docs-snippets/src/billing/kotlin/io/github/thesobiaakbar/adroit/docs/billing/BillingXml.kt#billing-paywall-view-close -->
```kotlin
view.findViewById<PaywallView>(R.id.coins_paywall).onClose = { parentFragmentManager.popBackStack() }
```

**Your own views.** For a completely different look, drive your views from the paywall's controller. It still reads
Firebase, picks the offers, runs the purchase and reports the placement:

<!-- snippet: docs-snippets/src/billing/kotlin/io/github/thesobiaakbar/adroit/docs/billing/BillingXml.kt#billing-custom-xml -->
```kotlin
/** The coins paywall's controller, kept across rotations. */
class CoinsViewModel : ViewModel() {
    val store = AdroitBilling.paywalls.controller(AppPaywalls.Coins, "coins_screen", viewModelScope)
}

class CoinsFragment : Fragment(R.layout.snippet_coins) {
    private val viewModel: CoinsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val price = view.findViewById<TextView>(R.id.coins_price)
        val buy = view.findViewById<Button>(R.id.coins_buy)
        val store = viewModel.store
        buy.setOnClickListener { store.purchase(requireActivity()) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                store.state.collect { state ->
                    when (state) {
                        is PaywallUiState.Content -> {
                            price.text = PriceText.planPrice(resources, state.selectedPlan)
                            buy.text = PriceText.cta(resources, state.selectedPlan)
                            buy.isEnabled = state.busy == null
                        }
                        is PaywallUiState.Purchased -> price.text = "Thank you!"
                        is PaywallUiState.Loading, is PaywallUiState.Unavailable, is PaywallUiState.Closed -> buy.isEnabled = false
                    }
                }
            }
        }
        store.onShown() // on screen now: reported once per opening
    }
}
```

- `PriceText` formats Play's prices and the button text the way the built-in paywalls do. Show only what Play gave:
  never type a price or a trial into the app.
- A controller ends after a purchase (`Purchased`); to sell again, open a new one (a new ViewModel, or the next
  opening of the screen).
- `controller()` doesn't check the show rules: the app decided to show it. Call
  `AdroitBilling.paywalls.shouldShow(…)` first when it should.

## 7. Restore and manage

Settings usually has three rows:

<!-- snippet: docs-snippets/src/billing/kotlin/io/github/thesobiaakbar/adroit/docs/billing/BillingXml.kt#billing-restore-xml -->
```kotlin
private fun onRestoreClicked() {
    viewLifecycleOwner.lifecycleScope.launch {
        val message = when (val result = AdroitBilling.restore()) {
            is RestoreResult.Restored -> if (result.newlyActive.isEmpty()) "Your purchases are up to date" else "Restored"
            RestoreResult.NothingToRestore -> "Nothing to restore"
            is RestoreResult.Failed -> "Couldn't reach Google Play. Try again later."
        }
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }
}

private fun onManageClicked() {
    // Play's subscription page; false when no app can open it.
    AdroitBilling.openManageSubscriptions(requireActivity(), AppProducts.PremiumSub)
}

private fun onUpgradeClicked() {
    // force = true: the user asked, so the show rules don't apply.
    AdroitPaywall.show(requireActivity(), AppPaywalls.Settings, "settings", force = true)
}
```

- **Restore** asks Play again for what the user owns, on this Google account. Purchases are restored automatically at
  every launch too; the button is for users who expect one.
- **Manage** opens Play's subscription page, where users cancel or change payment. Only Play can cancel a
  subscription.
- To show the status, collect `AdroitBilling.entitlements` (a `StateFlow`): `has("premium")`, and `status`
  (`Fresh` from Play, `Cached` while offline, `Unknown` before the first answer of a new install).

## 8. Consumables

A consumable (coins, lives) is bought again and again. The app credits it, then **confirms** it, which lets Play
sell it again. Collect the purchases for the whole app, not in a screen, so a purchase that finishes after the screen
closed still counts:

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

- A grant not confirmed (the app was killed in between) is delivered again at the next launch. That's why the wallet
  remembers each `orderIdHash`: credit once, confirm every time.
- The `Coins` paywall of step 2 has `successAction = STAY_WITH_MESSAGE`: it stays open after a purchase, with a
  thank-you, for buying again.

**Check (demo mode):** buying coins adds 100, and buying again works.

## 9. Testing

Code that takes `BillingClientApi` (here a ViewModel; `AdroitBilling` is the default) can be tested with `FakeBilling`
from `adroit-billing-testing`, on the plain JVM:

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

More in [Testing](TESTING.md#billing-with-adroit-billing-testing). On a device:

1. **Demo mode** (debug build): every flow, including a pending payment that completes 15 seconds later, and
   cancelling.
2. **Real Play**: turn demo off, add yourself as a license tester, and install from an internal testing track
   ([Play Console setup](PLAY_CONSOLE_BILLING_SETUP.md)). Test cards approve, decline or delay payments without
   charging you.

Before release: the products are active in Play Console, the app is uploaded to a track, demo mode can't run in
release (the library ignores it there), and the Firebase parameters are published ([Paywalls in
Firebase](PAYWALL_REMOTE_CONFIG.md)).

Problems? See [Billing troubleshooting](BILLING_TROUBLESHOOTING.md). The `sample-xml` app does all of the above.

Last verified against: Play Billing Library 9.1.0, 2026-10-20
