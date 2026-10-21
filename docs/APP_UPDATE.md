# In-app update

`AdroitUpdate` (artifact `adroit-update`) checks Google Play for a newer version when the app starts, and offers it
the way the Firebase `AppUpdate` parameter says. It works the same in AdMob-only, IAP-only and AdMob + IAP apps: it
depends on no ads or billing module.

## What the user sees

| `type` | When a newer version is on Google Play |
|---|---|
| `flexible` (default) | Google Play's update prompt. The user may decline and continue. If they accept, the update downloads in the background while they use the app, and the app offers "Restart to update". |
| `force` | Google Play's full-screen update. The user can't continue without updating. Backing out closes the app, and the next launch asks again. |
| `off` | Nothing: no check. |

When there's no newer version, or Google Play isn't there (a sideloaded or debug build, an emulator without Play),
or the device is offline, the app continues at once.

## Firebase parameter `AppUpdate`

```json
{ "type": "flexible", "forceBelowVersionCode": 0 }
```

| Field | Values | Default | Meaning |
|---|---|---|---|
| `type` | `flexible`, `force`, `off` (any case) | `flexible` | How updates are offered. |
| `forceBelowVersionCode` | whole number, 0 or more | `0` (unused) | If above 0 and the installed versionCode is below it, the update is forced even when `type` is `flexible`. Use it to retire old versions while newer ones still get the flexible prompt. |

Both fields are optional. An invalid value is ignored with a warning in Logcat (tag `AdroitUpdate`), and the default
applies. Without Firebase, or before the first fetch, the `default` passed to `check` applies (`flexible` unless you
pass another).

`check` waits up to 2 seconds for this session's Remote Config fetch, at the same time as it asks Google Play. After
that it uses the last values fetched (the previous launch's).

## Code

```kotlin
// SplashActivity.onCreate
lifecycleScope.launch {
    AdroitUpdate.check(this@SplashActivity)
    viewModel.flow.start(this@SplashActivity) // or, IAP-only: route to the paywall or main screen
}
```

`check` returns when the app may continue. It never returns when the user backs out of a forced update, because the
app closes. Without Firebase, pass the type yourself: `AdroitUpdate.check(this, UpdateType.FORCE)`.

A flexible update downloads in the background. Offer the restart when it's ready, on any screen:

```kotlin
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        AdroitUpdate.readyToInstall.collect { ready ->
            if (ready) {
                Snackbar.make(root, "Update ready", Snackbar.LENGTH_INDEFINITE)
                    .setAction("Restart") { AdroitUpdate.install() }
                    .show()
            }
        }
    }
}
```

A download finished in an earlier session is installed at the next launch's `check`.

## Testing

In-app updates only work for an app installed from Google Play. A debug build run from Android Studio always
continues at once. To see the real flows:

1. Upload a build to Internal App Sharing or the internal testing track, and install it from Play on a device.
2. Upload a second build with a **higher versionCode** the same way. Don't install it.
3. Open the first build: with `type` `flexible` you get the prompt; with `force`, the full-screen update.
4. To test `forceBelowVersionCode`, set it above the first build's versionCode with `type` `flexible`.

Play can take a while to offer the new version; opening the Play Store app's page for it first usually helps.

Last verified against: Play In-App Updates (`com.google.android.play:app-update`) 2.1.0, 2026-10-21
