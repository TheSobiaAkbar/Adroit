package io.github.thesobiaakbar.adroit.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thesobiaakbar.adroit.AdAvailability
import io.github.thesobiaakbar.adroit.AdPlacement
import io.github.thesobiaakbar.adroit.AdsClient
import io.github.thesobiaakbar.adroit.AdsState
import io.github.thesobiaakbar.adroit.AppOpenPlacement
import io.github.thesobiaakbar.adroit.BannerPlacement
import io.github.thesobiaakbar.adroit.InterstitialPlacement
import io.github.thesobiaakbar.adroit.NativePlacement
import io.github.thesobiaakbar.adroit.RewardedInterstitialPlacement
import io.github.thesobiaakbar.adroit.RewardedPlacement
import io.github.thesobiaakbar.adroit.compose.internal.ComposeRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether an ad for [placement] can be shown now, for example to enable a "Watch ad" button. Collected only while
 * the lifecycle is at least STARTED. This only observes: call `preload` to start loading.
 *
 * Interstitial, rewarded, rewarded interstitial and app open placements report their cached ad. Banner and native
 * placements have no cached ad and are always [AdAvailability.Unavailable]; use [AdBanner]'s `onStateChange` or
 * [rememberNativeAd] for them. Safe to call before the library is initialized: it follows the placement once it is.
 */
@Composable
public fun rememberAdAvailability(placement: AdPlacement): State<AdAvailability> {
    val client = ComposeRuntime.client
    // Flows obtained before initialization stay inert, so fetch the placement's flow again once initialized.
    val adsState by client.state.collectAsStateWithLifecycle()
    val initialized = adsState != AdsState.NotInitialized
    val flow = remember(client, placement, initialized) { client.availability(placement) }
    return flow.collectAsStateWithLifecycle()
}

private fun AdsClient.availability(placement: AdPlacement): StateFlow<AdAvailability> = when (placement) {
    is InterstitialPlacement -> interstitial.availability(placement)
    is RewardedPlacement -> rewarded.availability(placement)
    is RewardedInterstitialPlacement -> rewardedInterstitial.availability(placement)
    is AppOpenPlacement -> appOpen.availability
    is BannerPlacement, is NativePlacement -> MutableStateFlow(AdAvailability.Unavailable)
}
