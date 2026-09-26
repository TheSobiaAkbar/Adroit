package com.company.admob.sample.compose

import com.company.admob.AdFormat
import com.company.admob.AdsState
import com.company.admob.events.AdEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Everything the library reported since the app started, collected once for the whole process so that screens
 * visited later (Events, Initialization, Native) still see what happened before them.
 */
class EventLog(scope: CoroutineScope, events: Flow<AdEvent>) {
    private val mutableEvents = MutableStateFlow<List<AdEvent>>(emptyList())
    private val mutableStateTimeline = MutableStateFlow<List<AdEvent.StateChanged>>(emptyList())
    private val mutableNativeLoads = MutableStateFlow(NativeLoadCount(0, 0))

    /** The last [CAPACITY] events, newest first. */
    val events: StateFlow<List<AdEvent>> = mutableEvents.asStateFlow()

    /** Every [AdsState] change, oldest first. Kept separately so it never scrolls out of [events]. */
    val stateTimeline: StateFlow<List<AdEvent.StateChanged>> = mutableStateTimeline.asStateFlow()

    /** Native ad requests and successful loads, all placements. A reload shows up here. */
    val nativeLoads: StateFlow<NativeLoadCount> = mutableNativeLoads.asStateFlow()

    init {
        scope.launch {
            events.collect { event ->
                mutableEvents.value = (listOf(event) + mutableEvents.value).take(CAPACITY)
                if (event is AdEvent.StateChanged) mutableStateTimeline.value += event
                if (event.format == AdFormat.NATIVE) {
                    val count = mutableNativeLoads.value
                    when (event) {
                        is AdEvent.LoadRequested -> mutableNativeLoads.value = count.copy(requests = count.requests + 1)
                        is AdEvent.Loaded -> mutableNativeLoads.value = count.copy(loaded = count.loaded + 1)
                        else -> Unit
                    }
                }
            }
        }
    }

    fun clear() {
        mutableEvents.value = emptyList()
    }

    private companion object {
        const val CAPACITY = 200
    }
}

data class NativeLoadCount(val requests: Int, val loaded: Int)
