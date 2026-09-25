package com.company.admob.internal.port

import kotlinx.coroutines.flow.SharedFlow

/** Process foreground transitions, used to trigger app open ads. Implemented in Prompt 06. */
internal interface AppForegroundSource {
    /** Emits on the main thread each time the app comes to the foreground. */
    val foregroundEvents: SharedFlow<ForegroundEvent>

    val isInForeground: Boolean
}

/** [isColdStart] is true only for the first foreground of the process. */
internal data class ForegroundEvent(val isColdStart: Boolean)
