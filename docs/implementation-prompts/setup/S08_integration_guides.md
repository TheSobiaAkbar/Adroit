# S08 — Integration guides: 3 setups × 2 UI stacks

Read `docs/implementation-prompts/setup/S00_WORKING_RULES.md` first and follow it.

## Objective
A developer starting a new app picks **one** guide and follows it from an empty Android Studio project to a
release-ready app, never reading another guide. There are six guides, in `docs/guides/`:

|  | Jetpack Compose + Kotlin | XML + Kotlin |
|---|---|---|
| AdMob only | `ADS_COMPOSE.md` | `ADS_XML.md` |
| IAP only (users must subscribe) | `IAP_COMPOSE.md` | `IAP_XML.md` |
| **AdMob + IAP (most apps)** | `ADS_IAP_COMPOSE.md` | `ADS_IAP_XML.md` |

## Sources (merge, then delete)
- **Merged in and then deleted** (no second copy may remain):
  - `docs/NEW_APP_XML.md` and `docs/NEW_APP_COMPOSE.md` (ads);
  - `docs/BILLING_NEW_APP_XML.md` and `docs/BILLING_NEW_APP_COMPOSE.md` (billing).
- **Code to copy:** sample-xml and sample-compose after S06/S07, which are the AdMob + IAP reference.
- **Reference docs, linked rather than copied:** `SPLASH.md`, `APP_UPDATE.md`, `AD_PROFILES.md`,
  `PAYWALL_REMOTE_CONFIG.md`, `REMOTE_CONFIG.md`, `CONSENT.md`, `PLAY_CONSOLE_BILLING_SETUP.md`, the format docs.

## Every guide has the same sections, in this order
1. **What you'll have at the end.** 3 bullets.
2. **Dependencies:**
   - the exact `libs.versions.toml` and `build.gradle.kts` lines, and only the artifacts this setup needs;
   - the Firebase/google-services plugin;
   - the AdMob App ID in the manifest (ads setups only).

   | Setup | Compose | XML |
   |---|---|---|
   | AdMob only | `adroit-compose`, `adroit-firebase`, `adroit-update` | `adroit-views`, `adroit-firebase`, `adroit-update` |
   | IAP only | `adroit-billing`, `adroit-billing-compose`, `adroit-billing-firebase`, `adroit-update` | `adroit-billing`, `adroit-billing-views`, `adroit-billing-firebase`, `adroit-update` |
   | AdMob + IAP | both rows | both rows |
3. **Firebase project setup:** the steps, `google-services.json` and debug variants.
4. **Enums:**
   - **Ads setups:** the `AdDefinition` enum (`SplashAd`, `Interstitial`, `Banner`, `Native`, …).
   - **IAP setups:** `ProductDefinition` / `PaywallDefinition`.
   - Explain "enum name = Firebase key".
5. **`Application.onCreate`:** the full file. AdMob + IAP adds the one bridge line, with a sentence on why. No
   `RemoteConfigSession.start`.
6. **Splash:** the full Activity + ViewModel (and the composable for Compose):
   - **Ads setups:** `AdroitUpdate.check`, then `SplashFlow`, then routing.
   - **IAP only:** `AdroitUpdate.check`, then entitled ? main : the hard paywall.
   - **AdMob + IAP:** the full `showAdLater` routing through the launch paywall (S06/S07), with a short table of the
     three `splashAdMode` values and what the user sees.
7. **Screens:**
   - **Ads setups:** each ad format hookup used by the sample (banner, native, interstitial on action, rewarded if
     the sample has it).
   - **IAP setups:** opening a paywall from Settings, "Restore purchases", "Manage subscription".
   - The update snackbar.
8. **Firebase Remote Config to paste:** one complete JSON block per key for this setup only:
   - **Ads:** the ad keys and `AdSettings` (incl. `splashAdMode`);
   - **IAP:** `AdroitBilling` and the paywall keys;
   - **all setups:** `AppUpdate`.

   **IAP only** uses the hard paywall: `close.delaySeconds: -1` and `back: "NEVER"` (see
   `PAYWALL_REMOTE_CONFIG.md`), with a note on Google Play's policy about hard paywalls (already in that doc).
9. **Test it:** test ads, consent debug, demo billing / license testers, the in-app update via Internal App
   Sharing.
10. **Release checklist:** real IDs, `adsEnabled`, Play Console products, R8, versionCode bump for `AppUpdate`.

Writing rules:
- Plain language for a junior developer. Every code block is complete, not a fragment with "…" for required lines.
- Snippets live in `docs-snippets` (`-PincludeDocsSnippets`) so they compile, and the guides copy them. Follow how
  the existing guides do this.

## Also
- **`docs/GETTING_STARTED.md`:** becomes a chooser. "1. Does your app show AdMob ads? 2. Does it sell
  subscriptions or purchases? 3. Compose or XML?" leads to a table with the six links. Keep anything else in it that
  isn't a guide step, or move it to the right reference doc.
- **`docs/INTEGRATION_GUIDE.md`** (the non-technical Firebase guide): add `AppUpdate` (what flexible and force mean
  for users; `forceBelowVersionCode`) and `splashAdMode` (what each value looks like to the user). Keep the existing
  tone.
- **Links:** fix every link to the deleted files (`README.md`, `docs/*.md`, KDoc, sample READMEs). Grep for
  `NEW_APP_` and `BILLING_NEW_APP_`.
- **`CHANGELOG.md`:** the new guides and the removed ones.

## Verify
```
./gradlew -PincludeDocsSnippets :docs-snippets:assembleDebug
```
- The grep for `NEW_APP_XML|NEW_APP_COMPOSE|BILLING_NEW_APP` finds nothing outside CHANGELOG.
- Every relative link in `docs/` and `README.md` resolves (a small script or manual check; report which).

## Acceptance criteria
- [ ] Six guides exist, each with all 10 sections and only its own setup's artifacts and keys.
- [ ] The four old guides are deleted, and no content was lost: list where each old section went.
- [ ] Snippets compile; there are no dead links.
- [ ] `GETTING_STARTED.md` routes to the right guide in ≤ 3 questions.

## Report
S00 §E, plus the old-section → new-location table.
