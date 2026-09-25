package com.company.admob.fakes

import com.company.admob.internal.port.AppForegroundSource
import com.company.admob.internal.port.ForegroundEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** An [AppForegroundSource] the test drives with [moveToForeground] and [moveToBackground]. */
internal class FakeAppForegroundSource(override var isInForeground: Boolean = false) : AppForegroundSource {
    private val events = MutableSharedFlow<ForegroundEvent>(extraBufferCapacity = 16)

    override val foregroundEvents: SharedFlow<ForegroundEvent> = events

    fun moveToForeground(isColdStart: Boolean = false) {
        isInForeground = true
        check(events.tryEmit(ForegroundEvent(isColdStart))) { "Foreground event buffer is full" }
    }

    fun moveToBackground() {
        isInForeground = false
    }
}
