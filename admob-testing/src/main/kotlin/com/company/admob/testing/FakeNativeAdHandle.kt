package com.company.admob.testing

import com.company.admob.NativeAdAssets
import com.company.admob.NativeAdHandle
import com.company.admob.NativeAdHandleState
import com.company.admob.NativePlacement
import com.company.admob.UnavailableReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A native ad from [FakeAdsClient] with the given [assets]. [destroy] is counted ([destroyCount]) so tests can check
 * that an ad is released exactly once; [block] simulates ads being blocked (ad-free, consent withdrawn).
 */
public class FakeNativeAdHandle(
    override val placement: NativePlacement,
    override val assets: NativeAdAssets = FakeAdsClient.DEFAULT_NATIVE_ASSETS,
) : NativeAdHandle {
    private val mutableState = MutableStateFlow<NativeAdHandleState>(NativeAdHandleState.Active)

    override val state: StateFlow<NativeAdHandleState> = mutableState.asStateFlow()

    override val isDestroyed: Boolean get() = mutableState.value != NativeAdHandleState.Active

    /** How many times [destroy] was called, including repeated calls. */
    public var destroyCount: Int = 0
        private set

    override fun destroy() {
        destroyCount++
        if (mutableState.value == NativeAdHandleState.Active) mutableState.value = NativeAdHandleState.Destroyed
    }

    /** Moves the handle to [NativeAdHandleState.Blocked], as the library does when ads get blocked. */
    public fun block(reason: UnavailableReason = UnavailableReason.AD_FREE_USER) {
        if (mutableState.value == NativeAdHandleState.Active) mutableState.value = NativeAdHandleState.Blocked(reason)
    }

    override fun toString(): String = "FakeNativeAdHandle(${placement.key}, state=${mutableState.value})"
}
