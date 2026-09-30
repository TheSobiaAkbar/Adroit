package io.github.thesobiaakbar.adroit.fakes

import io.github.thesobiaakbar.adroit.AdsState
import io.github.thesobiaakbar.adroit.internal.init.Readiness
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/** A [Readiness] whose state the test sets directly. */
internal class FakeReadiness(initial: AdsState = AdsState.AwaitingStart) : Readiness {
    override val state = MutableStateFlow(initial)

    override fun canLoad(): Boolean = state.value == AdsState.Ready

    override suspend fun awaitReady() {
        state.first { it == AdsState.Ready }
    }
}
