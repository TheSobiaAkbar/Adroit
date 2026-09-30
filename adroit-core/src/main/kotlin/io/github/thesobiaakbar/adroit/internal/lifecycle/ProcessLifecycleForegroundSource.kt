package io.github.thesobiaakbar.adroit.internal.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.thesobiaakbar.adroit.internal.port.AppForegroundSource
import io.github.thesobiaakbar.adroit.internal.port.ForegroundEvent
import io.github.thesobiaakbar.adroit.internal.threading.runOnMain
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * [AppForegroundSource] over the process lifecycle: every ON_START emits a [ForegroundEvent], and only the first
 * one in the process is a cold start. Register it from `initialize()` (in `Application.onCreate`), before the process
 * first starts; otherwise the observer is added late and its catch-up ON_START still counts as the cold start.
 */
internal class ProcessLifecycleForegroundSource(
    private val lifecycle: () -> Lifecycle = { ProcessLifecycleOwner.get().lifecycle },
) : AppForegroundSource, DefaultLifecycleObserver {
    private val events = MutableSharedFlow<ForegroundEvent>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val foregroundEvents: SharedFlow<ForegroundEvent> = events.asSharedFlow()

    @Volatile
    override var isInForeground: Boolean = false
        private set

    private var hasStarted = false

    /** Adds the observer on the main thread, as `Lifecycle.addObserver` requires. */
    fun register() = runOnMain { lifecycle().addObserver(this) }

    fun unregister() = runOnMain { lifecycle().removeObserver(this) }

    override fun onStart(owner: LifecycleOwner) {
        isInForeground = true
        val coldStart = !hasStarted
        hasStarted = true
        events.tryEmit(ForegroundEvent(isColdStart = coldStart))
    }

    override fun onStop(owner: LifecycleOwner) {
        isInForeground = false
    }
}
