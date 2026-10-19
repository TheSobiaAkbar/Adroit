# Billing reference backend (Firebase Cloud Functions)

This is a deployable reference implementation of [docs/BILLING_BACKEND.md](../../docs/BILLING_BACKEND.md). It uses
Cloud Functions (2nd gen, Node 22, TypeScript) and Firestore, and receives Google Play real-time developer
notifications (RTDN) over Pub/Sub. It isn't part of the Gradle build or the published libraries.

Skip it if your company already has a backend that implements the contract.

| Function | Trigger | What it does |
|---|---|---|
| `verify` | HTTPS `POST`, Firebase Auth ID token required | Checks the purchase with Google Play and binds the token to the user in a Firestore transaction. A token another user already owns is rejected. It acknowledges the purchase and answers with the contract JSON. |
| `entitlements` | HTTPS `GET`, Firebase Auth ID token required | The user's active grants: subscriptions by stored state and expiry, plus non-consumables. |
| `rtdn` | Pub/Sub topic `play-rtdn` | Re-reads the purchase from Play for each notification and never trusts the payload alone. Refunds (voided purchases) revoke the grant. It is idempotent by message ID and retried by Pub/Sub on failure. |

Credentials come from the functions' runtime service account (Application Default Credentials). **There is no key
file, and none may be committed.** `.gitignore` refuses the usual key file names.

## Files

- `src/config.ts`: your package name, the product allow-list (type and entitlements), and the RTDN topic. **Edit it
  before deploying.**
- `src/verify.ts`, `src/entitlements.ts`, `src/rtdn.ts`: the logic. It is plain functions over two interfaces:
  - `Play` (`src/play.ts`, `@googleapis/androidpublisher` v3);
  - `Store` (`src/store.ts`, Firestore).
- `src/index.ts`: the Cloud Functions wiring and Firebase Auth.
- `test/`: Node's built-in test runner with a fake Play API (real response shapes) and an in-memory store.
- `firestore.rules`: deny all client access. Only the functions, through the Admin SDK, read and write.

Firestore data:

- `billingPurchases/{sha256(token)}` holds one document per purchase: `uid`, `type`, `productIds`, `entitlements`,
  `state`, `expiryMillis`, `voided`, `replacedBy`. The purchase token itself is never stored.
- `billingRtdnMessages/{messageId}` holds the notifications already processed.

## Setup

These steps were not run against a live project here; follow Google's pages for the current console wording.

1. **Firebase project on the Blaze plan.** Cloud Functions 2nd gen needs it; the emulator doesn't.
   ```bash
   npm install -g firebase-tools
   firebase login
   firebase use --add            # pick the project; writes .firebaserc
   ```
2. **Enable the Google Play Android Developer API** in that Google Cloud project (APIs & Services → Library).
3. **Give the functions' service account access in Play Console.** 2nd gen functions run as the Compute Engine default
   service account (`PROJECT_NUMBER-compute@developer.gserviceaccount.com`) unless you set another one.
   1. In Play Console, go to Users and permissions → Invite new users, and enter that e-mail address.
   2. Under App permissions, add your app with **View financial data** and **Manage orders and subscriptions**.
   3. Permissions can take up to 24 hours to apply. A `401`/`403` from Play in the logs usually means they haven't
      yet.
4. **Real-time developer notifications.**
   1. Create the topic:
      ```bash
      gcloud pubsub topics create play-rtdn
      gcloud pubsub topics add-iam-policy-binding play-rtdn \
        --member=serviceAccount:google-play-developer-notifications@system.gserviceaccount.com \
        --role=roles/pubsub.publisher
      ```
   2. In Play Console, open Monetize with Play → Monetization setup → Real-time developer notifications.
   3. Set the topic to `projects/PROJECT_ID/topics/play-rtdn` and turn on the notifications for one-time products
      (and voided purchases if offered).
   4. Press **Send test message**. The `rtdn` log shows "test notification".

   Deploying `rtdn` creates the topic's push subscription for you.
5. **Edit `src/config.ts`** (package name, products, entitlements), then deploy:
   ```bash
   npm ci
   npm test
   npm run deploy
   ```
6. **Point the app at it** (`adroit-billing`):
   ```kotlin
   val backend = HttpPurchaseVerifier(
       url = "https://us-central1-PROJECT_ID.cloudfunctions.net/verify",
       entitlementsUrl = "https://us-central1-PROJECT_ID.cloudfunctions.net/entitlements",
       headers = { mapOf("Authorization" to "Bearer ${Firebase.auth.currentUser!!.getIdToken(false).await().token}") },
   )
   AdroitBilling.setAccount(Firebase.auth.currentUser?.uid)
   ```
   The app must sign the user in with Firebase Auth. Anonymous auth works, but a reinstall gets a new user and
   restored purchases are rejected as bound to another account. Use a real sign-in if users must keep purchases
   across devices.

## Emulator (manual check with `HttpPurchaseVerifier`)

The emulator runs Functions, Firestore and Auth locally. **Play API calls are real.** Give the emulator credentials
that can call Play: your own account, impersonating the service account from step 3. To use them you need a real test
purchase token from a license-tester account.

```bash
cd backend/billing-functions
npm ci
gcloud auth application-default login \
  --impersonate-service-account=PROJECT_NUMBER-compute@developer.gserviceaccount.com \
  --scopes=https://www.googleapis.com/auth/androidpublisher,https://www.googleapis.com/auth/cloud-platform
npm run serve
```

In another terminal, create an emulator user and get an ID token:

```bash
curl -s -X POST "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake" \
  -H "Content-Type: application/json" -d '{"email":"tester@example.com","password":"secret123","returnSecureToken":true}'
```

Copy `idToken` from the answer, then call the functions:

```bash
curl -s -X POST "http://127.0.0.1:5001/PROJECT_ID/us-central1/verify" \
  -H "Authorization: Bearer ID_TOKEN" -H "Content-Type: application/json" \
  -d '{"packageName":"com.example.app","purchaseToken":"TEST_PURCHASE_TOKEN","productIds":["premium_subscription"],"type":"SUBSCRIPTION"}'
curl -s "http://127.0.0.1:5001/PROJECT_ID/us-central1/entitlements" -H "Authorization: Bearer ID_TOKEN"
```

From the Android emulator, `10.0.2.2` is the computer. The app must point Firebase Auth at the emulator
(`Firebase.auth.useEmulator("10.0.2.2", 9099)`) and allow local HTTP in debug builds only:

```kotlin
HttpPurchaseVerifier(
    url = "http://10.0.2.2:5001/PROJECT_ID/us-central1/verify",
    entitlementsUrl = "http://10.0.2.2:5001/PROJECT_ID/us-central1/entitlements",
    headers = { mapOf("Authorization" to "Bearer ${Firebase.auth.currentUser!!.getIdToken(false).await().token}") },
    allowLocalHttp = BuildConfig.DEBUG,
)
```

Android blocks cleartext HTTP by default. For this check only, add a debug-only `network_security_config` that
allows `10.0.2.2`.

To send an RTDN to the emulator, publish to the Pub/Sub emulator, or deploy and use Play Console's test message.

## Limits of this reference

- **Subscription expiry follows RTDN.** If a renewal notification is lost, the stored expiry passes and `/entitlements`
  drops the grant until the next notification or `/verify`. The app still merges Play's on-device data, so the user
  keeps access while Play reports it.
- **Consumables** are verified and bound, never credited here: credit them in your own code at the point marked by
  `entitlements: []`, once per token.
- **Voided purchases** are confirmed with `purchases.voidedpurchases.list` (a 1-day look-back, up to 5 pages). A
  notification for a token not yet listed is retried by Pub/Sub.
- **Accounts:** `/entitlements` identifies the user by the ID token and ignores the `account` parameter.
