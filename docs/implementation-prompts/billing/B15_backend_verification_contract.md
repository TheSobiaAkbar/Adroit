# B15 — Backend verification contract (docs + client adapter)

Read `docs/implementation-prompts/billing/B00_WORKING_RULES.md` first and follow it.

## Objective
Document the contract between `PurchaseVerifier` / `EntitlementProvider` and a company backend, and ship a small,
dependency-free `HttpPurchaseVerifier` that implements it over `HttpURLConnection` + `org.json`. Apps with a backend
then need only a URL and an auth-header lambda.

## Context
06 §7. Server credentials (the Play Developer API service account) live only on the server. The client sends:
- the token;
- product IDs;
- the type;
- the package name;
- the attribution;
- the account ID hash.

## Prerequisites
B07, B08.

## Files
- `docs/BILLING_BACKEND.md`: the contract.
  - `POST /verify`: request and response JSON, error codes, idempotency (the token is the key), timeouts.
  - `GET /entitlements?account=<hash>`.
  - The responsibilities:
    - call `purchases.subscriptionsv2.get` / `purchases.products.get` (or the current product v2 endpoint; verify on
      `developers.google.com/android-publisher`);
    - validate the package and product;
    - bind the token to the user;
    - reject a token already bound to another user;
    - acknowledge server-side, optionally;
    - process RTDN (Pub/Sub) for renewals, expirations, holds, grace and refunds (voided purchases);
    - store entitlements.

  Include a sequence diagram (text) and a security checklist.
- `adroit-billing/.../HttpPurchaseVerifier.kt` (public): `HttpPurchaseVerifier(url, headers: suspend () ->
  Map<String,String>, timeout)`. It also implements `EntitlementProvider` when an `entitlementsUrl` is given. It runs
  on `Dispatchers.IO`.
- Tests with `MockWebServer`? **No new dependency.** Use a tiny `com.sun.net.httpserver.HttpServer` in JVM tests, or
  Robolectric with a local `ServerSocket`. Choose whichever works on the repo's test setup and report it.

## Requirements
1. HTTPS only (reject `http://` except `localhost`/`10.0.2.2` in debug builds).
2. Map the server responses to `VerificationResult`:
   - 2xx with `{"status":"VERIFIED","acknowledged":bool,"entitlements":[...]}`;
   - `{"status":"REJECTED","reason":...}`;
   - 5xx, a timeout or an IO error → `Unreachable`.
3. The request body never includes the raw account ID, only the hash from `setAccount`.
4. The response body is at most 64 KB. Parse defensively.

## Edge cases
- The server says VERIFIED for a different product than requested: treat it as `Rejected("product mismatch")`.
- A redirect to `http://`: refused.

## Tests
- verified, rejected, 500, timeout, malformed JSON, product mismatch;
- headers are invoked per request (token refresh).

## Verify
```
./gradlew :adroit-billing:testDebugUnitTest :adroit-billing:apiCheck
```

## Acceptance criteria
- [ ] The contract doc is complete enough that a backend developer can implement it without reading the Kotlin.
- [ ] No server credential appears in any file of the Android project (grep for `private_key`,
      `service_account`, `client_email`).

## Report
B00 §D.
