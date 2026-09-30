# Rewarded ads

## When to use them

The user chooses to watch an ad in exchange for something: coins, an extra life, a hint
([Google](https://developers.google.com/admob/android/next-gen/rewarded)). Always tell the user what they'll get
before they choose.

## Integration

Grant rewards in a ViewModel or repository, never in the Activity, and at most once per `rewardId`:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/FullScreen.kt#rewarded-viewmodel -->
```kotlin
/** Grant rewards in a ViewModel, once per rewardId, including rewards that arrive after the screen is gone. */
class CoinsViewModel(private val ads: AdsClient = Adroit.client) : ViewModel() {
    private val granted = mutableSetOf<String>()
    private val mutableCoins = MutableStateFlow(0)
    val coins: StateFlow<Int> = mutableCoins.asStateFlow()

    init {
        // Earned while this screen was gone (rotation, process kept alive): delivered here instead.
        viewModelScope.launch {
            merge(ads.rewarded.unclaimedResults, ads.rewardedInterstitial.unclaimedResults).collect(::grant)
        }
    }

    fun watchAdForCoins(activity: Activity) {
        ads.rewarded.show(activity, Placements.ExtraLife, RewardedShowOptions()) { result ->
            when (result) {
                is RewardResult.Earned -> grant(result)
                RewardResult.NotEarned -> Unit // closed early: no reward
                is RewardResult.Failed -> Unit // not shown: offer the reward another way, or hide the button
            }
        }
    }

    private fun grant(reward: RewardResult.Earned) {
        if (granted.add(reward.rewardId)) mutableCoins.value += reward.reward.amount
    }
}
```

In Compose, enable the button only when an ad is ready:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/FullScreen.kt#rewarded-compose -->
```kotlin
@Composable
fun WatchAdButton(onReward: (RewardResult.Earned) -> Unit) {
    val availability by rememberAdAvailability(Placements.ExtraLife)
    val launcher = rememberFullScreenAdLauncher()
    Button(
        enabled = availability == AdAvailability.Ready,
        onClick = { launcher.showRewarded(Placements.ExtraLife) { if (it is RewardResult.Earned) onReward(it) } },
    ) { Text("Watch an ad for 50 coins") }
}
```

## Exactly-once rewards

- The result is `Earned(reward, rewardId)`, `NotEarned` (closed early) or `Failed(error)`, delivered exactly once.
- `rewardId` is unique per show: use it to make the grant idempotent (on your server for valuable rewards).
- If the screen is gone when the ad closes (a rotation cancelled `showAndAwait`), an earned reward is delivered to
  `rewarded.unclaimedResults` instead: collect it where you grant rewards, as above.
- If the process dies while the ad is showing, a client-side reward is lost. For valuable rewards, use server-side
  verification.

## Server-side verification

Set up SSV for the ad unit in AdMob ([Google's SSV guide](https://developers.google.com/admob/android/next-gen/ssv)),
then pass your data with the show; grant the reward when your server receives Google's callback:

<!-- snippet: docs-snippets/src/main/kotlin/io/github/thesobiaakbar/adroit/docs/FullScreen.kt#rewarded-ssv -->
```kotlin
fun showWithServerSideVerification(activity: Activity, userId: String) {
    Adroit.rewarded.show(
        activity,
        Placements.ExtraLife,
        RewardedShowOptions(serverSideVerificationCustomData = "user=$userId"),
    ) { /* grant on your server's SSV callback, not here */ }
}
```

## State, events and errors

Availability, lifecycle, errors, retry and expiry are the same as [interstitials](INTERSTITIAL.md). Rewarded ads also
emit `RewardEarned(reward, rewardId)`. By default rewarded ads don't count towards the global full-screen interval,
because the user asked for them (`frequency { includeRewardedInGlobalInterval = true }` changes that).

## FAQ

**The reward callback didn't fire after a rotation.** Collect `unclaimedResults` in the ViewModel, as shown above.

**Can the user earn a reward twice from one ad?** No: only the first reward of a show counts, and each show has one
`rewardId`.

Last verified against: ads-mobile-sdk 1.4.0, UMP 4.0.0, 2026-09-26
