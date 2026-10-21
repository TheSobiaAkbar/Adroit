package io.github.thesobiaakbar.adroit.update.internal

import android.app.Activity
import io.github.thesobiaakbar.adroit.update.UpdateType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

// Robolectric: org.json and Activity are part of Android, only stubbed in plain JVM tests.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class UpdateCoordinatorTest {
    private class FakePort : UpdatePort {
        var info: UpdateInfo = UpdateInfo(available = true, immediateAllowed = true, flexibleAllowed = true)
        var infoError: Exception? = null
        var infoHangs = false
        var infoCalls = 0
        var result = Activity.RESULT_OK
        val flows = mutableListOf<Boolean>() // immediate?
        var completes = 0
        var listener: ((InstallProgress) -> Unit)? = null
        var listens = 0
        var removed = 0

        override suspend fun info(): UpdateInfo {
            infoCalls++
            infoError?.let { throw it }
            if (infoHangs) awaitCancellation()
            return info
        }

        override suspend fun startFlow(info: UpdateInfo, activity: Activity, immediate: Boolean): Int {
            flows += immediate
            return result
        }

        override fun listen(onProgress: (InstallProgress) -> Unit): () -> Unit {
            listens++
            listener = onProgress
            return { removed++ }
        }

        override fun complete() {
            completes++
        }
    }

    private val port = FakePort()
    private val ready = MutableStateFlow(false)
    private val warnings = mutableListOf<String>()
    private var config: String? = null
    private val fetched = CompletableDeferred<Unit>().apply { complete(Unit) }
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private fun coordinator(installed: Long = 10) =
        UpdateCoordinator(port, { config }, { fetched.await() }, installed, ready, { warnings += it })

    /** Runs a check; true when it returned, false when it's still suspended (a declined forced update). */
    private fun TestScope.check(
        coordinator: UpdateCoordinator = coordinator(),
        default: UpdateType = UpdateType.FLEXIBLE,
    ): Boolean {
        var returned = false
        val job = launch {
            coordinator.check(activity, default)
            returned = true
        }
        advanceTimeBy(10_000)
        runCurrent()
        job.cancel()
        return returned
    }

    @Test
    fun `no key - flexible`() = runTest {
        assertTrue(check())
        assertEquals(listOf(false), port.flows)
        assertEquals(1, port.listens)
    }

    @Test
    fun `off - no update flow and no Play request kept`() = runTest {
        config = """{"type": "off"}"""
        assertTrue(check())
        assertTrue(port.flows.isEmpty())
        assertEquals(0, port.completes)
    }

    @Test
    fun `FORCE in any case - immediate, and an accepted update doesn't close the app`() = runTest {
        config = """{"type": "FORCE"}"""
        assertTrue(check())
        assertEquals(listOf(true), port.flows)
        assertFalse(activity.isFinishing)
    }

    @Test
    fun `a declined forced update closes the app and check never returns`() = runTest {
        config = """{"type": "force"}"""
        port.result = Activity.RESULT_CANCELED
        assertFalse(check())
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `a forced update in progress is resumed`() = runTest {
        config = """{"type": "force"}"""
        port.info = UpdateInfo(available = false, inProgress = true)
        assertTrue(check())
        assertEquals(listOf(true), port.flows)
    }

    @Test
    fun `forceBelowVersionCode forces only when the installed version is below it`() = runTest {
        config = """{"type": "flexible", "forceBelowVersionCode": 11}"""
        assertTrue(check(coordinator(installed = 10)))
        config = """{"type": "flexible", "forceBelowVersionCode": 10}"""
        assertTrue(check(coordinator(installed = 10)))
        config = """{"type": "flexible", "forceBelowVersionCode": 9}"""
        assertTrue(check(coordinator(installed = 10)))
        assertEquals("below, equal, above", listOf(true, false, false), port.flows)
    }

    @Test
    fun `force without IMMEDIATE allowed falls back to flexible`() = runTest {
        config = """{"type": "force"}"""
        port.info = UpdateInfo(available = true, immediateAllowed = false, flexibleAllowed = true)
        assertTrue(check())
        assertEquals(listOf(false), port.flows)
        assertFalse(activity.isFinishing)

        port.info = UpdateInfo(available = true)
        assertTrue("neither allowed: continue", check())
        assertEquals(1, port.flows.size)
    }

    @Test
    fun `flexible returns whatever the user chose, and the download makes it ready to install`() = runTest {
        val coordinator = coordinator()
        port.result = Activity.RESULT_CANCELED
        assertTrue(check(coordinator))
        port.result = Activity.RESULT_OK
        assertTrue(check(coordinator))
        assertEquals("one listener per process", 1, port.listens)

        coordinator.install()
        assertEquals("nothing downloaded yet", 0, port.completes)
        port.listener!!(InstallProgress.DOWNLOADED)
        assertTrue(ready.value)
        coordinator.install()
        assertEquals(1, port.completes)

        port.listener!!(InstallProgress.ENDED)
        assertFalse(ready.value)
        assertEquals(1, port.removed)
    }

    @Test
    fun `an update downloaded in an earlier session is installed at the check`() = runTest {
        port.info = UpdateInfo(available = false, downloaded = true)
        assertTrue(check())
        assertEquals(1, port.completes)
        assertTrue(port.flows.isEmpty())
    }

    @Test
    fun `no update, no Play, or no answer from Play - returns at once`() = runTest {
        port.info = UpdateInfo(available = false)
        assertTrue(check())

        port.infoError = IllegalStateException("Install Error(-10): The app is not owned by any user")
        assertTrue(check())

        port.infoError = null
        port.infoHangs = true
        assertTrue(check())
        assertTrue(port.flows.isEmpty())
        assertEquals(2, warnings.size)
    }

    @Test
    fun `an Activity gone during the check gets no update flow`() = runTest {
        activity.finish()
        assertTrue(check())
        assertTrue(port.flows.isEmpty())
    }

    @Test
    fun `Remote Config not fetched within 2 seconds - the active value is used`() = runTest {
        val never = CompletableDeferred<Unit>()
        config = """{"type": "force"}"""
        val coordinator = UpdateCoordinator(port, { config }, { never.await() }, 10, ready, { warnings += it })
        val start = currentTime
        var returnedAt: Long? = null
        val job = launch {
            coordinator.check(activity, UpdateType.FLEXIBLE)
            returnedAt = currentTime
        }
        advanceTimeBy(1_900)
        runCurrent()
        assertNull("still waiting for the fetch", returnedAt)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(2_000L, returnedAt!! - start)
        assertEquals(listOf(true), port.flows)
        job.cancel()
    }

    @Test
    fun `nothing to update on Play - returns at once, without waiting for Remote Config`() = runTest {
        val never = CompletableDeferred<Unit>()
        var configRead = false
        port.info = UpdateInfo(available = false)
        val coordinator = UpdateCoordinator(port, { configRead = true; null }, { never.await() }, 10, ready, { warnings += it })
        val start = currentTime
        coordinator.check(activity, UpdateType.FLEXIBLE)
        assertEquals("no wait for the fetch", 0L, currentTime - start)
        assertFalse(configRead)
        assertTrue(port.flows.isEmpty())
    }

    @Test
    fun `malformed values - the default, with a warning`() = runTest {
        config = "not json"
        assertTrue(check(default = UpdateType.OFF))
        assertTrue("the app's default (off) applies", port.flows.isEmpty())

        config = """{"type": "sometimes", "forceBelowVersionCode": -3}"""
        assertTrue(check())
        assertEquals(listOf(false), port.flows)
        assertEquals(3, warnings.size)
    }
}
