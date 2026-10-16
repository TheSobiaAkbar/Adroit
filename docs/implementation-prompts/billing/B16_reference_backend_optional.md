# B16 (optional) — Reference backend: Firebase Cloud Functions + Firestore + RTDN

Read `docs/implementation-prompts/billing/B00_WORKING_RULES.md` first and follow it. Skip this prompt if the company
already has a backend that implements `docs/BILLING_BACKEND.md`.

## Objective
A small, deployable reference implementation of `docs/BILLING_BACKEND.md` in `backend/billing-functions/`
(TypeScript, Firebase Cloud Functions 2nd gen). It isn't part of the Gradle build or the published artifacts.

## Prerequisites
B15. Node LTS installed. A Firebase project on the Blaze plan for real deployment (the emulator works without it).

## Files
- `backend/billing-functions/package.json`, `tsconfig.json`, `src/index.ts`, `src/verify.ts`,
  `src/entitlements.ts`, `src/rtdn.ts`, `src/play.ts` (googleapis `androidpublisher` v3), `test/*.test.ts`
- `backend/billing-functions/README.md`: setup covering:
  - the service account with Play Console "View financial data" + "Manage orders and subscriptions";
  - linking Play Console to the Google Cloud project;
  - the RTDN topic and subscription;
  - deploying;
  - the emulator.
- `.gitignore`: `node_modules`, `lib`, any `*.json` key file.

## Requirements
1. `verify` (HTTPS, Firebase Auth ID token required):
   1. validate the package and product against an allow-list;
   2. call `purchases.subscriptionsv2.get` or the one-time product endpoint;
   3. reject anything not in `SUBSCRIPTION_STATE_ACTIVE` / `IN_GRACE_PERIOD` (for subscriptions) or `PURCHASED`
      (for one-time products);
   4. bind the token to the uid in Firestore inside a **transaction** (reject if it's bound to another uid);
   5. acknowledge if needed;
   6. return the contract JSON.
2. `entitlements`: return active grants for the uid, computed from stored subscription states and expiry times.
3. `rtdn` (Pub/Sub): on each notification, re-fetch the subscription from Play (never trust the payload alone),
   update Firestore, and handle voided purchases (refunds). It's idempotent by `messageId`.
4. The credentials come from the runtime service account (Application Default Credentials). **No key file in the
   repo.**
5. Tests use mocked `androidpublisher` responses: active, grace, hold, expired, revoked, a token bound to another
   user, a replayed message.

## Verify
```
cd backend/billing-functions && npm ci && npm test && npm run build
```

## Acceptance criteria
- [ ] All tests pass locally (report real output).
- [ ] There are no credentials in git: `git grep -n "private_key"` is empty.
- [ ] `HttpPurchaseVerifier` from B15 works against the emulator. This is a manual step: give the exact commands.

## Report
B00 §D.
