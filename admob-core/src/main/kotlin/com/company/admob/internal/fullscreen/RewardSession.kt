package com.company.admob.internal.fullscreen

import com.company.admob.AdFormat
import com.company.admob.FullScreenResult
import com.company.admob.Reward
import com.company.admob.RewardResult
import com.company.admob.events.AdEvent
import com.company.admob.internal.config.EffectivePlacement
import com.company.admob.internal.events.EventBus
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.time.Clock
import java.util.UUID

/**
 * The reward side of one rewarded show (architecture §14). Only the first reward counts; it emits
 * [AdEvent.RewardEarned] right away, carrying the same [rewardId] as the final [RewardResult.Earned], so hosts can
 * make grants idempotent. The show's [FullScreenResult] maps to the final result: dismissed → [RewardResult.Earned]
 * or [RewardResult.NotEarned]; not shown (failure or watchdog) → [RewardResult.Failed]. Main thread only.
 */
internal class RewardSession(
    private val placement: EffectivePlacement,
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
                        placement.placement.key, format, placement.adUnitId, clock.currentTimeMillis(), reward, rewardId,
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
