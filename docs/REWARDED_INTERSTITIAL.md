# Rewarded interstitial ads

## When to use them

A reward offered at a natural transition, shown without the user asking
([Google](https://developers.google.com/admob/android/next-gen/rewarded-interstitial)). Because the user didn't opt in,
Google requires an **intro screen before the ad**
([rewarded interstitial guidance](https://support.google.com/admob/answer/9884467)). It must:

- clearly tell the user about the reward;
- give a clear, unobstructed way to skip the ad before it starts;
- leave enough time to understand the offer and opt out.

The library can't show that screen for you: `rewardedInterstitial.show` assumes you already did.

## Integration

<!-- snippet: docs-snippets/src/main/kotlin/com/company/admob/docs/FullScreen.kt#rewarded-interstitial-intro -->
```kotlin
/** Google requires an intro screen that tells the user about the reward and lets them opt out, before the ad. */
@Composable
fun BonusIntro(onDone: () -> Unit, onReward: (RewardResult.Earned) -> Unit) {
    val launcher = rememberFullScreenAdLauncher()
    var secondsLeft by rememberSaveable { mutableIntStateOf(5) }
    LaunchedEffect(secondsLeft) {
        if (secondsLeft == 0) {
            launcher.showRewardedInterstitial(BonusRound) { result ->
                if (result is RewardResult.Earned) onReward(result)
                onDone()
            }
        } else {
            delay(1.seconds)
            secondsLeft--
        }
    }
    Text("Watch a short ad for bonus coins? The ad starts in $secondsLeft s.")
    TextButton(onClick = onDone) { Text("No thanks") }
}
```

With Views, a `DialogFragment` with the same content works; `sample-xml`'s `BonusIntroDialogFragment` shows one.
Grant the reward exactly as for [rewarded ads](REWARDED.md): in a ViewModel, once per `rewardId`, and collect
`rewardedInterstitial.unclaimedResults`.

## Lifecycle, state, events, errors, frequency

The same as [rewarded ads](REWARDED.md), except that rewarded interstitials count towards the global full-screen
interval.

## FAQ

**Can I skip the intro screen?** No. It's Google's requirement for this format; without it, use an
[interstitial](INTERSTITIAL.md) or a [rewarded ad](REWARDED.md) the user chooses.

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-26
