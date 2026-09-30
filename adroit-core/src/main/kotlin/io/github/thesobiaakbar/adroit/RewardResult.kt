package io.github.thesobiaakbar.adroit

/**
 * The single, final outcome of a rewarded (or rewarded interstitial) show request.
 *
 * Delivered exactly once, on the main thread, after the ad is dismissed (or immediately if it could not be shown).
 * Grant the reward only for [Earned], and use [Earned.rewardId] to make the grant idempotent.
 *
 * Stability: the three outcomes won't change before the next major version, so an exhaustive `when` is safe.
 */
public sealed interface RewardResult {
    /** The user earned [reward]. [rewardId] is a random ID unique to this show, for idempotent granting. */
    public data class Earned(public val reward: Reward, public val rewardId: String) : RewardResult

    /** The ad was shown but dismissed before the reward was earned. */
    public data object NotEarned : RewardResult

    /** The ad was not shown, because of [error]. No reward. */
    public data class Failed(public val error: AdError) : RewardResult
}

/** A reward as configured for the ad unit in the AdMob UI. Immutable. */
public data class Reward(public val type: String, public val amount: Int)
