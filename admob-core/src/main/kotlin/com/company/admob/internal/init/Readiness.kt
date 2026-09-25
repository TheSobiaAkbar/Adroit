package com.company.admob.internal.init

import com.company.admob.AdsState
import kotlinx.coroutines.flow.StateFlow

/** The readiness gate the ad engines depend on. Nothing may be requested unless [canLoad] is true. */
internal interface Readiness {
    val state: StateFlow<AdsState>

    /** True only in [AdsState.Ready]. */
    fun canLoad(): Boolean

    /** Suspends until [AdsState.Ready]. Cancellable; never returns in any other state. */
    suspend fun awaitReady()
}
