# Products, entitlements, purchases

## Contents
1. Products and entitlements
2. `BillingClientApi`: what the app calls
3. Entitlement state: "is premium"
4. Buying without a paywall: `PurchaseRequest`
5. Results, pending, grace and account hold
6. Consumables
7. Upgrades and downgrades
8. Server verification (optional)
9. Accounts

## 1. Products and entitlements

```kotlin
enum class AppProducts(override val defaults: ProductSpec) : ProductDefinition {
    PremiumSub(ProductSpec.subscription("premium_sub", entitlements = setOf("premium"))),
    PremiumLifetime(ProductSpec.nonConsumable("premium_lifetime", entitlements = setOf("premium"))),
    ProSub(ProductSpec.subscription("pro_sub", entitlements = setOf("premium", "pro"))),   // a higher tier
    Coins100(ProductSpec.consumable("coins_100")),                                          // no entitlement
}
```

| Factory | Play Console type | Grants |
|---|---|---|
| `ProductSpec.subscription(id, entitlements)` | Subscription. Its **base plans** (weekly, monthly, yearly, prepaid) and **offers** (trial, intro) are created in Play, not in code. | while active, in grace, or within offline grace |
| `ProductSpec.nonConsumable(id, entitlements)` | One-time product: lifetime, or "remove ads forever" | forever |
| `ProductSpec.consumable(id)` | One-time product. Play has no consumable flag; the code makes it consumable. | nothing; it is delivered as a `ConsumableGrant` |

**Base plans and offers are never declared in code.** Paywalls point at them with
`PlanSpec(basePlanId = "yearly", offer = OfferRule(...))`. The library reads what Play offers *this user*.

**Entitlements:**
- An entitlement is a plain string that several products can share.
- **Tiers** are separate entitlements: `"premium"` and `"pro"`. Check with `has("pro")`.
- Firebase can remap a declared product's entitlements (`remote-config.md`), but it can't add products.

## 2. `BillingClientApi` (implemented by `AdroitBilling`; inject the interface so tests can pass `FakeBilling`)

```kotlin
val entitlements: StateFlow<EntitlementSnapshot>
val connection: StateFlow<BillingConnectionState>   // Disconnected | Connecting | Connected | Unavailable(error) | Error(error, retryAtMillis)
val catalog: StateFlow<CatalogState>                // Loading | Ready(products, missing) | Failed(error)
fun product(definition: ProductDefinition): ProductInfo?
suspend fun awaitProduct(definition, timeout = 5.seconds): ProductInfo?
val purchaseUpdates: SharedFlow<PurchaseUpdate>     // Completed | Pending | Suspended(productId) | Failed
val consumables: Flow<ConsumableGrant>
@MainThread suspend fun purchase(activity: Activity, request: PurchaseRequest): PurchaseResult
suspend fun restore(): RestoreResult                // Restored(newlyActive, snapshot) | NothingToRestore | Failed(error)
suspend fun refresh()
@MainThread fun openManageSubscriptions(activity: Activity, product: ProductDefinition? = null): Boolean
@MainThread fun showInAppMessages(activity: Activity)   // Play's "payment declined" etc.; call in the main screen's onResume
fun setAccount(accountId: String?)                      // only a SHA-256 hash leaves the device
```
`AdroitBilling` also has `paywalls: Paywalls`, `addEventListener(…)` and `removeEventListener(…)`.

**`ProductInfo`, for custom price UI:**
- `ProductInfo(productId, type, title, displayName, description, oneTimeOptions, plans: List<SubscriptionPlan>)`
- `SubscriptionPlan(basePlanId, offers)`, with `baseOffer` and `basePhase`.
- `OfferInfo(basePlanId, offerId?, offerTags, phases)`, with `freeTrial: BillingPeriod?`, `introPhases`, `basePhase`,
  `isPrepaid` and `firstPayment`.
- `PricingPhase(price: Price(formatted, micros, currencyCode), billingPeriod, recurrence)`.

## 2b. A shop list priced from Play, without a paywall

Use this for a store screen that lists several products, such as gem packs plus a lifetime unlock. A paywall
controller ends at its first purchase, so a store reads the catalog and buys directly instead:

```kotlin
class StoreViewModel(private val billing: BillingClientApi = AdroitBilling) : ViewModel() {
    /** One row per product, priced by Play; null price while the catalog loads or the product is missing. */
    val rows: StateFlow<List<StoreRow>> = combine(billing.catalog, billing.entitlements) { catalog, ent ->
        val products = (catalog as? CatalogState.Ready)?.products.orEmpty()
        listOf(AppProducts.UnlockAll, AppProducts.Gems50, AppProducts.Gems200).map { def ->
            val info = products[def]
            StoreRow(def, price = info?.oneTimeOptions?.firstOrNull()?.price?.formatted,   // one-time products
                owned = def == AppProducts.UnlockAll && ent.has("unlock_all"))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun buy(activity: Activity, def: ProductDefinition) = viewModelScope.launch {
        when (val r = billing.purchase(activity, PurchaseRequest(def, placement = "store"))) {
            is PurchaseResult.Purchased, PurchaseResult.AlreadyOwned -> Unit   // entitlements / consumables flows update the UI
            is PurchaseResult.Pending -> showMessage(R.string.payment_pending)
            PurchaseResult.Cancelled -> Unit
            is PurchaseResult.Failed -> showMessage(R.string.store_unavailable)
        }
    }
}
```
**Prices from Play:**
- One-time product: `ProductInfo.oneTimeOptions: List<OneTimeOption>`, where each option is
  `(purchaseOptionId, offerId, offerTags, price: Price)`. Use the first option, unless Play Console defines several
  purchase options.
- Subscription: `info.plans.first { it.basePlanId == "monthly" }.basePhase?.price?.formatted`.
- The `Price` fields are `formatted`, `micros` and `currencyCode`.

**Copy marketing can change:** for a single product's copy from Firebase (a "lifetime" card's title), add a paywall
just for it, and read `controller.spec.texts`. That is the Firebase-merged `PaywallTexts`, fixed when the controller
opens, and every field is nullable. Fall back to string resources, and remember that `PaywallTexts` are
single-language.

**Analytics:** only paywalls report shown/closed events. Direct purchases are attributed by their `placement`.

## 3. Entitlement state: "is premium"

There is no `isPremium` API. Use the snapshot:
```kotlin
val snapshot = AdroitBilling.entitlements.value
snapshot.has("premium")        // the check, everywhere
snapshot.active                // Set<String> of active entitlements
snapshot.adFree                // for the ads bridge
snapshot.needsPaymentFix       // account hold / payment declined: send the user to Play
snapshot.status                // Fresh(atMillis) from Play | Cached(sinceMillis) offline | Unknown (new install, grants nothing)
```
- **Compose:** `val ent by AdroitBilling.entitlements.collectAsStateWithLifecycle()`.
- **ViewModel:** take `billing: BillingClientApi` and expose `billing.entitlements` or a derived flow.
- **Wait for the first purchase** (to end an onboarding paywall, for example):
  `billing.entitlements.first { it.active.isNotEmpty() }`.
- **A fresh install of an existing subscriber** reads `Unknown` until Play answers. A paywall shown in that gap must
  offer "Restore", or end when the entitlements arrive. PhotoRecovery's onboarding finishes as soon as
  `entitlements.first { it.active.isNotEmpty() }` returns.

## 4. Buying without a paywall: `PurchaseRequest`

Use this for a "Buy" button with your own UI, or "subscribe straight to Play's sheet" (no paywall page):
```kotlin
val result = billing.purchase(activity, PurchaseRequest(
    product = AppProducts.PremiumSub,
    placement = "unlock_sheet",               // [A-Za-z0-9_.-]{1,64}; for attribution
    basePlanId = "monthly",
    offer = OfferSelection.BaseOnly,          // Auto (default) | BaseOnly | Id("offer-id") | Tags(listOf("trial"))
    // replace = SubscriptionReplacement(...)  for upgrades (§7)
))
```
- **An offer Play doesn't give this user** returns `Failed(OfferNotFound)`. No other offer is substituted.
- **Usually a paywall controller is easier:** it has already picked the plan and offer.
  `controller.purchase(activity)` buys the selected plan. PhotoRecovery's "subscribe opens Play's sheet directly"
  uses a controller with no visible page (`scenarios.md` §6).
- **Call from a ViewModel scope.** If the caller is cancelled (rotation), the result still arrives on
  `purchaseUpdates`.

## 5. Results, pending, grace and account hold

**`PurchaseResult`:**
- `Purchased(grant: EntitlementGrant?, attribution)`. The grant is null for a consumable.
- `Pending(attribution)`: a slow payment method. **Grant nothing yet.** Show "Payment pending". The completion
  arrives later on `purchaseUpdates` (`Completed`), or on the next sync.
- `Cancelled`
- `AlreadyOwned`: triggers a sync. Treat it as success after refresh.
- `Failed(error, attribution?)`. Notable errors:
  - `PurchaseInProgress`: one purchase runs at a time.
  - `NoActivity`: the Activity is finishing.
  - `OfferNotFound`
  - `DeveloperError`, for example a second subscription without `replace`.

**What the library already does:**
- It grants and acknowledges only PURCHASED purchases.
- It processes each purchase token once (a ledger of hashed tokens).
- It turns on pending purchases for one-time products and prepaid plans, and reconnects automatically.

**Grace and account hold:**
- `GrantState` is one of `ACTIVE`, `GRACE_FROM_BACKEND`, `SUSPENDED` or `PENDING`.
- On the device, Play's grace period looks like `ACTIVE`, so the user keeps premium. Only a backend can report
  `GRACE_FROM_BACKEND`.
- **Account hold or pause** becomes `SUSPENDED`:
  - no entitlement;
  - `needsPaymentFix = true`;
  - `PurchaseUpdate.Suspended` is emitted.

  Show a "Fix payment" row that calls `openManageSubscriptions`, and call `showInAppMessages(activity)` in the main
  screen's `onResume`.

**Offline:**
- A saved subscription is trusted for `offlineGraceHours` (72).
- A non-consumable is trusted forever.
- `status` becomes `Cached`.

## 6. Consumables

```kotlin
/** Credited app-wide from Application.onCreate, so a purchase counts whichever screen is open. */
object Coins {
    private val mutableBalance = MutableStateFlow(0)
    val balance: StateFlow<Int> = mutableBalance.asStateFlow()

    fun start(context: Context, scope: CoroutineScope) {
        val prefs = context.getSharedPreferences("coins", Context.MODE_PRIVATE)
        mutableBalance.value = prefs.getInt("balance", 0)
        scope.launch {
            AdroitBilling.consumables.collect { grant ->
                val credited = prefs.getStringSet("credited", emptySet()).orEmpty()
                if (grant.orderIdHash !in credited) {                 // credit each order once, even when redelivered
                    mutableBalance.value += 100 * grant.quantity
                    prefs.edit(commit = true) {
                        putInt("balance", mutableBalance.value)
                        putStringSet("credited", credited + grant.orderIdHash)
                    }
                }
                grant.confirm()                                       // now Play can sell it again
            }
        }
    }
}
```
- `ConsumableGrant(productId, quantity, attribution, orderIdHash)`, with `suspend fun confirm()`. Here `productId` is
  the **Play** product ID (`"gems_50"`), not the enum name. Map it back with
  `AppProducts.entries.first { it.defaults.productId == grant.productId }`.
- **Delivery:**
  - Every unconfirmed grant is delivered again on each launch, after the purchase sync.
  - Collect from `Application.onCreate`, so the collector exists before the sync.
  - If the process dies between crediting and `confirm()`, the next launch delivers the grant again. The
    `orderIdHash` check then skips the credit, and `confirm()` runs.
- "Already owned" when buying coins again means a grant was never confirmed.
- For valuable currency, keep the balance on a server, and credit through server verification.
- A consumable paywall uses `template = PaywallTemplate.SINGLE`, `showRestore = false` and
  `successAction = SuccessAction.STAY_WITH_MESSAGE`. A purchase ends a controller, so "Buy more" opens a new one
  (`key(round)`).

## 7. Upgrades and downgrades

```kotlin
PurchaseRequest(AppProducts.ProSub, placement = "upgrade", basePlanId = "monthly",
    replace = SubscriptionReplacement(oldProduct = AppProducts.PremiumSub, mode = ReplacementMode.CHARGE_PRORATED_PRICE))
```
- `ReplacementMode`: `WITH_TIME_PRORATION`, `CHARGE_PRORATED_PRICE`, `CHARGE_FULL_PRICE`, `WITHOUT_PRORATION`,
  `DEFERRED` or `KEEP_EXISTING`.
- Buying a second subscription that shares an entitlement **without** `replace` fails with `DeveloperError`.
- An old product the user doesn't own fails with `ItemNotOwned`.
- Switching base plans of the **same** subscription (monthly to yearly) is better done in Play's manage page.
- `replace` is for subscriptions only.

## 8. Server verification (optional)

Without a backend, Play's on-device report is trusted, and a rooted device can fake it. For valuable content:
```kotlin
val verifier = HttpPurchaseVerifier(
    url = "https://api.example.com/billing/verify",
    headers = { mapOf("Authorization" to "Bearer ${auth.idToken()}") },
    entitlementsUrl = "https://api.example.com/billing/entitlements",
)
BillingSettings(..., verifier = verifier, entitlementProvider = verifier)
```
- **Restrictions:** HTTPS only (plain HTTP only to localhost or 10.0.2.2, with `allowLocalHttp`). No redirects, and a
  64 KB response cap.
- **Failures:** a non-2xx response or a timeout is `Unreachable`, and the purchase is retried on the next sync.
- **The contract:**
  - `POST /verify` takes `{packageName, purchaseToken, productIds, type, accountIdHash, attribution}`.
  - It answers `{"status":"VERIFIED","productIds":[…],"acknowledged":bool}`, or `{"status":"REJECTED","reason":"…"}`.
    It must be idempotent.
  - `GET /entitlements?account=<hash>` answers `{"grants":[{productId,type,entitlements,state,purchaseTimeMillis}]}`.
- **Reference backend:** the Adroit repo's `backend/billing-functions/` (Firebase Functions, Firestore, and Play RTDN
  for refunds). Edit `src/config.ts` before deploying. It is a starting point; its setup wasn't run against a live
  project.

## 9. Accounts

- Purchases belong to the **Google account** on the device.
- `setAccount(id)` ties attribution and backend checks to the app's own user, and sends only a hash. Calling it with a
  different account clears the cache.
- Premium tied to the app's own account (across devices or Google accounts) needs a backend and an
  `EntitlementProvider`.
