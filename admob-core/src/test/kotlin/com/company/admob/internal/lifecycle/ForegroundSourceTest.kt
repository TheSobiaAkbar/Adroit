package com.company.admob.internal.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.company.admob.internal.port.ForegroundEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ForegroundSourceTest {
    /** Stands in for `ProcessLifecycleOwner`. */
    private class ProcessOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun `only the first start of the process is a cold start`() = runTest {
        val owner = ProcessOwner().apply { registry.currentState = Lifecycle.State.CREATED }
        val source = ProcessLifecycleForegroundSource { owner.lifecycle }
        source.register()
        val events = mutableListOf<ForegroundEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { source.foregroundEvents.toList(events) }

        owner.registry.currentState = Lifecycle.State.STARTED
        assertTrue(source.isInForeground)
        owner.registry.currentState = Lifecycle.State.CREATED
        assertFalse(source.isInForeground)
        owner.registry.currentState = Lifecycle.State.RESUMED
        owner.registry.currentState = Lifecycle.State.CREATED
        owner.registry.currentState = Lifecycle.State.STARTED

        assertEquals(listOf(true, false, false), events.map { it.isColdStart })
        collector.cancel()
    }

    @Test
    fun `unregister stops events`() = runTest {
        val owner = ProcessOwner().apply { registry.currentState = Lifecycle.State.CREATED }
        val source = ProcessLifecycleForegroundSource { owner.lifecycle }
        source.register()
        source.unregister()
        owner.registry.currentState = Lifecycle.State.STARTED
        assertFalse(source.isInForeground)
    }
}
