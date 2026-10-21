# Adroit setups: implementation roadmap

This series gives apps one clean way to integrate Adroit, whatever they need: AdMob only, IAP only, or both (the
most common). It also adds:
- a Firebase-controlled in-app update (`flexible` by default, or `force`);
- a splash that can load its ad without showing it, or skip it, so the app shows it later (for example on the
  paywall's X).

The standing rules and the decisions already made are in `S00_WORKING_RULES.md`.

## How to run the prompts
1. Open a Claude Code session at the repo root and say "execute `docs/implementation-prompts/setup/S01_shared_remote_config.md`".
2. Claude reads S00, implements only that stage, and ends with the S00 §E report.
3. **Don't start the next prompt until every acceptance item is ✅.**
4. Commit after each stage: `feat(setup): S01 shared remote config`.

## Order, dependencies and gates
| # | Prompt | Depends on | Gate |
|---|---|---|---|
| S01 | `S01_shared_remote_config.md`: new `adroit-remote-config`; ads and billing Firebase modules use it | — | 🚦 `build`, `apiCheck`, the dependency guard; one fetch per process |
| S02 | `S02_single_premium_source.md`: `Adroit.isAdFree`; `SplashFlow` defaults to it | — | API dump |
| S03 | `S03_splash_ad_mode.md`: `AdSettings.splashAdMode` show/preload/later | S02 | SplashFlow tests green |
| S04 | `S04_app_update_module.md`: new `adroit-update` (Firebase `AppUpdate`) | S01 | 🚦 JVM tests green |
| S05 | `S05_publishing_r8_smoke.md`: publishing, R8 rules, consumer-smoke for the new modules | S01, S04 | Release build of both samples OK |
| S06 | `S06_sample_xml.md`: sample-xml as the AdMob + IAP reference app | S03, S05 | 🚦 Runs on the device |
| S07 | `S07_sample_compose.md`: sample-compose parity | S06 | Parity with XML |
| S08 | `S08_integration_guides.md`: 6 guides (3 setups × Compose/XML), chooser, reference docs | S07 | Snippets compile; no dead links |
| S-FINAL | `S_FINAL_audit.md`: duplication audit, guide walkthroughs, device checks | S08 | No FAIL |

Parallel options:
- S01 and S02 touch different modules, so they can run in separate sessions.
- So can S03 and S04, once their dependencies are done.

## External things only you can do
- **Firebase console:** add the `AppUpdate` key and `splashAdMode` in `AdSettings` (the JSON is in S06), then
  publish.
- **Real in-app update flows:** these only work on a build installed from Google Play. To test:
  1. Upload a build to Internal App Sharing or an internal track.
  2. Install it.
  3. Upload a build with a higher versionCode.
  4. Open the installed app.

  Unit tests cover the logic without Play.
