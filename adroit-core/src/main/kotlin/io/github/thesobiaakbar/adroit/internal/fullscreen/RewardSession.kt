package io.github.thesobiaakbar.adroit.internal.fullscreen

import io.github.thesobiaakbar.adroit.AdFormat
import io.github.thesobiaakbar.adroit.FullScreenResult
import io.github.thesobiaakbar.adroit.Reward
import io.github.thesobiaakbar.adroit.RewardResult
import io.github.thesobiaakbar.adroit.events.AdEvent
import io.github.thesobiaakbar.adroit.internal.config.EffectivePlacement
import io.github.thesobiaakbar.adroit.internal.events.EventBus
import io.github.thesobiaakbar.adroit.internal.logging.AdLog
import io.github.thesobiaakbar.adroit.internal.time.Clock
import java.util.UUID

/**
 * The reward side of one rewarded show (architecture §14). Only the first reward counts; it emits
 * [AdEvent.RewardEarned] right away, carrying the same [rewardId] as the final [RewardResult.Earned], so hosts can
 * make grants idempotent. The show's [FullScreenResult] maps to the final result: dismissed → [RewardResult.Earned]
 * or [RewardResult.NotEarned]; not shown (failure or watchdog) → [RewardResult.Failed]. Main thread only.
 */
internal class RewardSession(
    private val placement: EffectivePlacement,
    /** The ad unit of the ad being shown, for events. */
    private val adUnitId: String,
    private val format: AdFormat,
    private val events: EventBus,
    private val clock: Clock,
    private val log: AdLog,
) {
    val rewardId: String = UUID.randomUUID().toString()

    private var earned: Reward? = null
    private var resolved = false

    fun onRewardEarned(reward: Reward) {
        when {
            resolved -> log.w { "$format ${placement.placement.key}: reward after the result was final; ignored" }
            earned != null -> log.d { "$format ${placement.placement.key}: repeated reward callback ignored" }
            else -> {
                earned = reward
                log.i { "$format ${placement.placement.key}: reward earned ($reward)" }
                events.emit(
                    AdEvent.RewardEarned(
                        placement.placement.key, format, adUnitId, clock.currentTimeMillis(), reward, rewardId,
                    ),
                )
            }
        }
    }

    /** The final result. After this, rewards are ignored. */
    fun resolve(result: FullScreenResult): RewardResult {
        resolved = true
        val reward = earned
        return when (result) {
            is FullScreenResult.Dismissed -> if (reward != null) RewardResult.Earned(reward, rewardId) else RewardResult.NotEarned
            is FullScreenResult.NotShown -> {
                if (reward != null) log.w { "$format ${placement.placement.key}: reward earned but the show failed" }
                RewardResult.Failed(result.error)
            }
        }
    }
}
