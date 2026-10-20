# Billing backend contract

This is what an app backend must implement so the Android billing library (`adroit-billing`) can verify purchases
and read entitlements from it. You don't need to read any Kotlin to implement it.

The backend is optional. Without it, the app trusts what Google Play reports on the device, and a tampered device can
fake that. With it, nothing is granted until your server confirms the purchase with Google.

The app talks to the backend through `HttpPurchaseVerifier`, which ships with the library. It needs:

- the URL of each endpoint;
- a function that returns the request headers (for example your user's auth token).

<!-- snippet: docs-snippets/src/billing/kotlin/io/github/thesobiaakbar/adroit/docs/billing/BillingBackend.kt#billing-http-verifier -->
```kotlin
val backend = HttpPurchaseVerifier(
    url = "https://api.example.com/billing/verify",
    entitlementsUrl = "https://api.example.com/billing/entitlements",
    headers = { mapOf("Authorization" to "Bearer ${auth.freshIdToken()}") },
)
AdroitBilling.initialize(
    this,
    BillingSettings(AppProducts.entries, AppPaywalls.entries, verifier = backend, entitlementProvider = backend),
)
```

An app can also write its own `PurchaseVerifier` and `EntitlementProvider` against a different API. The rules in
[Responsibilities](#responsibilities) apply either way.

## Credentials

The Play Developer API service account belongs **only on the server**. That covers its JSON key (`private_key`,
`client_email`) and any OAuth client secret. Never put it in:

- the APK;
- Remote Config;
- resources;
- a build config field.

The app sends Google's purchase token to your server. The server then asks Google about that token with its own
credentials.

## Sequence

```
User        App (adroit-billing)            Your backend                      Google Play Developer API
 |  buy  -->  |                                  |                                       |
 |            |-- Play purchase sheet ---------> (Google Play)                           |
 |            |<- purchase (token, products) --- (Google Play)                           |
 |            |   state: PENDING_VERIFICATION    |                                       |
 |            |-- POST /verify ----------------->|                                       |
 |            |   {purchaseToken, productIds,    |-- subscriptionsv2.get / productsv2 -->|
 |            |    type, packageName, ...}       |<- purchase resource ------------------|
 |            |                                  |   check package, products, state,     |
 |            |                                  |   token not bound to another user     |
 |            |                                  |   bind token -> user, store grant     |
 |            |                                  |-- acknowledge (optional) ------------>|
 |            |<- {"status":"VERIFIED", ...} ----|                                       |
 |            |   grant entitlements;            |                                       |
 |            |   acknowledge unless the backend did                                     |
 |            |                                  |                                       |
 |            |                                  |<-- RTDN (Pub/Sub): renewed, on hold, -|
 |            |                                  |    grace, expired, revoked, voided    |
 |            |                                  |   update stored grants                |
 |            |-- GET /entitlements?account=… -->|                                       |
 |            |<- {"grants":[...]} --------------|                                       |
```

## `POST /verify`

The app calls this once per new purchase, before granting anything. It calls it again on later syncs until it gets a
`VERIFIED` or `REJECTED` answer. Calls can therefore repeat for the same token, and the endpoint **must be
idempotent**: the purchase token is the idempotency key. A repeat call for a token already verified for the same user
returns the same `VERIFIED` answer.

### Request

The request headers are the ones the app's `headers` function returns, plus:

- `Content-Type: application/json; charset=utf-8`
- `Accept: application/json`

The body:

```json
{
  "packageName": "com.example.app",
  "purchaseToken": "opaque-token-from-google-play",
  "productIds": ["premium_sub"],
  "type": "SUBSCRIPTION",
  "accountIdHash": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
  "attribution": {
    "placement": "onboarding",
    "paywallId": "Onboarding",
    "campaignId": "spring",
    "productId": "premium_sub",
    "basePlanId": "monthly",
    "offerId": "trial",
    "attemptId": "4b8c…",
    "recovered": false
  }
}
```

| Field | Meaning |
|---|---|
| `packageName` | The app's package name. Check it is yours. |
| `purchaseToken` | Google Play's token for the purchase. Treat it as a secret: don't log it in full. |
| `productIds` | The Play product IDs in the purchase (usually one). |
| `type` | `SUBSCRIPTION`, `NON_CONSUMABLE` or `CONSUMABLE`. It decides which Google endpoint to call. |
| `accountIdHash` | The SHA-256 (lowercase hex) of the account ID the app set with `AdroitBilling.setAccount`, or `null`. The raw account ID is never sent. |
| `attribution` | Where in the app the purchase started, for your analytics. Every field except `placement`, `productId` and `recovered` may be `null`. Don't use it for security decisions. |

New fields may be added later; ignore the ones you don't know.

### Responses

Answer HTTP **200** with one of these two bodies.

**Verified:**

```json
{ "status": "VERIFIED", "acknowledged": true, "productIds": ["premium_sub"], "entitlements": ["premium"] }
```

| Field | Required | Meaning |
|---|---|---|
| `status` | yes | `"VERIFIED"` |
| `productIds` | yes | The product IDs Google reports for this token. If they differ from the request's, the app treats the purchase as rejected ("product mismatch"). |
| `acknowledged` | no (default `false`) | `true` if your server acknowledged the purchase with Google; the app then doesn't. |
| `entitlements` | no | Informational. The app grants the entitlements its own product definitions list. Use `GET /entitlements` to make the backend authoritative. |

**Rejected:**

```json
{ "status": "REJECTED", "reason": "token bound to another account" }
```

`reason` is a short text, at most 200 characters, for logs and analytics; don't put the token in it. The app then
grants nothing and doesn't acknowledge the purchase, so Google refunds it automatically after 3 days.

Reject when:

- the token is unknown to Google;
- the package or product doesn't match;
- the purchase is cancelled, refunded or voided;
- a one-time product isn't in the `PURCHASED` state;
- the token is already bound to another user.

**Anything else means "try later".** The app grants nothing yet and asks again on its next sync. This covers:

- any non-2xx status (5xx, 401, 403, 429…);
- a redirect (redirects are never followed);
- a timeout (15 seconds by default, for both connecting and reading);
- a body that isn't this JSON, or that is larger than 64 KB;
- an unknown `status` value.

Use this case for temporary failures, for example Google's API being unavailable. Don't answer "try later" forever for
a purchase you know is bad: answer `REJECTED`.

Pending purchases (cash payments and the like) never reach `/verify`. The app waits until Google reports them as
purchased.

## `GET /entitlements?account=<hash>`

This endpoint is optional; it's used when the app passes `entitlementsUrl`. It makes your backend a source of
entitlements. The app merges the backend's grants with what Play reports on the device. This is how the app learns
about states only the server sees through real-time notifications: a grace period, an account hold, a refund.

- `account` is the same `accountIdHash` as above, URL-encoded.
- The parameter is left out when the app set no account.
- The app's `headers` are sent too, so you can also identify the user from your auth token.

The response, with HTTP 200:

```json
{
  "grants": [
    {
      "productId": "premium_sub",
      "type": "SUBSCRIPTION",
      "entitlements": ["premium"],
      "state": "GRACE_FROM_BACKEND",
      "purchaseTimeMillis": 1760000000000
    }
  ]
}
```

| Field | Values |
|---|---|
| `productId` | The Play product ID. |
| `type` | `SUBSCRIPTION`, `NON_CONSUMABLE`, `CONSUMABLE` |
| `entitlements` | What the product unlocks (your entitlement names, for example `"premium"`). |
| `state` | `ACTIVE` and `GRACE_FROM_BACKEND` grant access. `SUSPENDED` (account hold or paused) doesn't, and the app asks the user to fix their payment. `PENDING` doesn't. |
| `purchaseTimeMillis` | When it was bought, in epoch milliseconds. |

Rules:

- Leave out products that no longer grant anything (expired, revoked, refunded).
- An entry the app can't read is skipped.
- A non-2xx status, a timeout or an unreadable body means "unknown"; the app then uses Play's data alone.
- The same 64 KB limit applies.

## Responsibilities

What the backend must do on `/verify`:

1. **Ask Google.** Call the [Google Play Developer API](https://developers.google.com/android-publisher/api-ref/rest)
   with the server's service account. These endpoints were checked on 2026-10-19:
   - subscriptions: `purchases.subscriptionsv2.get`, `GET /androidpublisher/v3/applications/{packageName}/purchases/subscriptionsv2/tokens/{token}`;
   - one-time products: `purchases.productsv2.getproductpurchasev2`, `GET …/purchases/productsv2/tokens/{token}`. The
     older `purchases.products.get` also still works, and it needs the product ID.
2. **Validate:**
   - the package name is yours;
   - Google's product IDs equal the request's;
   - a subscription's state grants access (`SUBSCRIPTION_STATE_ACTIVE` or `SUBSCRIPTION_STATE_IN_GRACE_PERIOD`);
   - a one-time product is purchased, not pending or cancelled;
   - for a test purchase, decide whether your environment accepts it.
3. **Bind the token to the user.** Store `purchaseToken → user`. A token already bound to a **different** user is
   `REJECTED`, so one purchase can't unlock two accounts. For a subscription upgrade or downgrade, Google returns
   `linkedPurchaseToken`: move the binding from the old token to the new one.
4. **Be idempotent.** The same token from the same user returns the same answer and never duplicates the grant. For a
   consumable this is critical: credit coins once per token.
5. **Acknowledge (optional).** Either the server acknowledges the purchase and answers `"acknowledged": true`, or the
   app does it. Either way, an unacknowledged purchase is refunded by Google after 3 days.
   - subscriptions: `purchases.subscriptions.acknowledge`;
   - one-time products: `purchases.products.acknowledge`.

   Consumables are consumed by the app; don't consume them on the server unless you also tell the app.
6. **Store the entitlement** the purchase grants, with its expiry for subscriptions.

What the backend must do outside `/verify`:

7. **Process real-time developer notifications (RTDN).** Google publishes them to your Cloud Pub/Sub topic (Play
   Console → Monetization setup). Each notification carries only a token, so re-read the purchase with step 1 and
   update the stored grant. Handle:
   - renewed, recovered, restarted → `ACTIVE`;
   - in grace period → `GRACE_FROM_BACKEND`;
   - on hold, paused → `SUSPENDED`;
   - expired, revoked → remove the grant;
   - a **voided purchase** (refund or chargeback) → remove the grant. One-time products are covered too, through
     `purchases.voidedpurchases.list` or the voided-purchase notification.
8. **Serve `/entitlements`** from what steps 6 and 7 store.

## Security checklist

- [ ] The service account key lives only on the server (a secret manager, not the repository). No `private_key`,
      `client_email` or `service_account` in the Android project.
- [ ] The service account has only the Play Console permissions it needs (view financial data, manage orders).
- [ ] Endpoints are HTTPS only. The app refuses `http://` except to `localhost`, `127.0.0.1` and `10.0.2.2`, and only
      when the app passes `allowLocalHttp = BuildConfig.DEBUG`.
- [ ] Endpoints require your user authentication (the `headers` function) and rate-limit per user and IP.
- [ ] Every purchase is checked with Google; nothing the client sends is trusted except the token, which is the
      lookup key.
- [ ] A token is bound to one user; reuse by another user is rejected.
- [ ] Grants are idempotent per token; consumables are credited once.
- [ ] Purchase tokens are not logged in full (log a hash).
- [ ] RTDN messages are authenticated: a Pub/Sub push subscription with OIDC auth, or pull. Each one is re-checked
      with Google before acting.
- [ ] Refunds (voided purchases) revoke grants.
- [ ] Responses stay under 64 KB and contain no tokens.

Related: [Billing](BILLING.md), [Play Console setup](PLAY_CONSOLE_BILLING_SETUP.md).

Last verified against: Play Billing Library 9.1.0, 2026-10-20
