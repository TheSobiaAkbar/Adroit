# Remote configuration

Remote overrides change ads at runtime without a release: a kill switch, turning off a format or a placement,
changing intervals, or moving a placement to a new ad unit. They're applied live: the next load, show or banner uses
the new values.

## `AdConfigProvider`

The library collects your provider's `Flow<AdOverrides>` after initialization and applies each emission on the main
thread. **Each emission replaces the previous one entirely.** Register it with `adMobConfig { remoteConfig = … }`.

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/AnalyticsAndRemote.kt#remote-provider -->
```kotlin
class MyRemoteConfigProvider(private val values: Flow<RemoteAdValues>) : AdConfigProvider {
    override fun overrides(): Flow<AdOverrides> = values.map { remote ->
        AdOverrides(
            adsEnabled = remote.adsEnabled,
            disabledFormats = if (remote.nativeDisabled) setOf(AdFormat.NATIVE) else emptySet(),
            placements = remote.disabledPlacements.associateWith { PlacementOverride(enabled = false) },
            minIntervalBetweenFullScreenAds = remote.interstitialIntervalSeconds?.seconds,
        )
    }
}
```

The provider must not throw. The default, `StaticConfigProvider`, emits nothing.

## `AdOverrides`

| Field | Effect |
|---|---|
| `adsEnabled` | `false` turns every ad off: `AdsState.Unavailable(DISABLED_BY_REMOTE)`. `null` leaves ads as configured. |
| `disabledFormats` | Formats to turn off, for example `setOf(AdFormat.NATIVE)`. |
| `placements` | Per placement key, a `PlacementOverride(enabled, minInterval, maxPerSession, preload, adUnitId)`; `null` fields keep the local value. |
| `minIntervalBetweenFullScreenAds` | Replaces the global full-screen interval. |

When a placement or format is turned off, its cached ads are destroyed, its banners hide
(`Hidden(DISABLED_BY_REMOTE)`), and shows return `NotShown(Disabled(DISABLED_BY_REMOTE))`. When it's turned back on,
loading resumes.

## Precedence

From strongest to weakest:

1. An ad-free user (`CompanyAdMob.setAdFree(true)`): no ads, whatever the remote says.
2. A remote `adsEnabled = false`.
3. Remote per-placement overrides and disabled formats.
4. The local configuration.

## Validation

Overrides are validated like the local configuration, but an invalid value never stops ads: it's dropped with a log,
and the rest of the emission still applies. For example, an unknown placement key, a malformed ad unit ID, a Google
demo ad unit in PRODUCTION, or a negative interval or count. `CompanyAdMob.diagnostics.snapshot().activeOverrides`
shows what's currently applied.

## Firebase Remote Config

A Firebase-backed provider is planned as an optional module. Until then, write a provider like the one above: read
your Remote Config values, map them to `AdOverrides`, and emit again whenever Firebase activates new values.

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-26
