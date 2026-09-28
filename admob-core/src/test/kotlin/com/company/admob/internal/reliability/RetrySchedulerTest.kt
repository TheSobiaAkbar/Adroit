package com.company.admob.internal.reliability

import com.company.admob.AdError
import com.company.admob.fakes.FakeAppForegroundSource
import com.company.admob.fakes.TestClock
import com.company.admob.internal.config.FormatDefaults
import com.company.admob.internal.logging.AdLog
import com.company.admob.logging.LogLevel
import com.company.admob.network.NetworkState
import com.company.admob.network.NetworkTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RetrySchedulerTest {
    private val policy = FormatDefaults.FULL_SCREEN_RETRY.copy(jitterRatio = 0.0)
    private val networkError = AdError.NetworkError()
    private val foreground = FakeAppForegroundSource(isInForeground = true)
    private val fired = mutableListOf<String>()
    private val wifi = NetworkState.Connected(NetworkTransport.WIFI, isMetered = false)
    private val network = MutableStateFlow<NetworkState>(wifi)

    // VERBOSE so every log message is built, which also exercises it.
    private fun TestScope.scheduler() = RetryScheduler(
        backgroundScope,
        foreground,
        AdLog({ _, _, _, _ -> }, LogLevel.VERBOSE),
        Random(0),
        network = lazyOf(network),
    )

    private fun TestScope.advance(seconds: Int) {
        advanceTimeBy(seconds * 1_000L)
        runCurrent()
    }

    @Test
    fun `a retry fires after its delay under virtual time`() = runTest {
        val scheduler = scheduler()
        val clock = TestClock(testScheduler)
        assertEquals(5.seconds, scheduler.schedule("I:1", 1, policy, networkError, retryable = true) {
            fired += "a@${clock.elapsedRealtimeMillis()}"
        })
        assertTrue(scheduler.isScheduled("I:1"))

        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(emptyList<String>(), fired)
        advance(1)
        assertEquals(listOf("a@5000"), fired)
        assertFalse(scheduler.isScheduled("I:1"))
    }

    @Test
    fun `non-retryable errors schedule nothing`() = runTest {
        val scheduler = scheduler()
        assertNull(scheduler.schedule("I:1", 1, policy, AdError.InvalidConfiguration("x"), retryable = false) { fired += "a" })
        assertFalse(scheduler.isScheduled("I:1"))
        advance(600)
        assertEquals(emptyList<String>(), fired)
    }

    @Test
    fun `attempts beyond maxAttempts schedule nothing`() = runTest {
        val scheduler = scheduler()
        assertEquals(80.seconds, scheduler.schedule("I:1", 5, policy, networkError, retryable = true) {})
        assertNull(scheduler.schedule("I:1", 6, policy, networkError, retryable = true) { fired += "late" })
        assertFalse("the new request also replaced the pending one", scheduler.isScheduled("I:1"))
    }

    @Test
    fun `rescheduling a key cancels the pending retry`() = runTest {
        val scheduler = scheduler()
        scheduler.schedule("I:1", 1, policy, networkError, retryable = true) { fired += "first" }
        assertEquals(30.seconds, scheduler.schedule("I:1", 1, policy, AdError.NoFill(), retryable = true) { fired += "second" })

        advance(600)
        assertEquals(listOf("second"), fired)
    }

    @Test
    fun `a retry due in the background waits for the next foreground`() = runTest {
        val scheduler = scheduler()
        foreground.moveToBackground()
        scheduler.schedule("I:1", 1, policy, networkError, retryable = true) { fired += "a" }

        advance(600)
        assertEquals(emptyList<String>(), fired)
        assertTrue(scheduler.isScheduled("I:1"))

        foreground.moveToForeground()
        runCurrent()
        assertEquals(listOf("a"), fired)
    }

    @Test
    fun `cancel and cancelAll stop pending retries`() = runTest {
        val scheduler = scheduler()
        scheduler.schedule("I:1", 1, policy, networkError, retryable = true) { fired += "1" }
        scheduler.schedule("I:2", 1, policy, networkError, retryable = true) { fired += "2" }
        scheduler.schedule("I:3", 1, policy, networkError, retryable = true) { fired += "3" }

        scheduler.cancel("I:1")
        assertFalse(scheduler.isScheduled("I:1"))
        scheduler.cancelAll()
        assertFalse(scheduler.isScheduled("I:2"))

        advance(600)
        assertEquals(emptyList<String>(), fired)
    }

    @Test
    fun `a retry block can schedule the next attempt for the same key`() = runTest {
        val scheduler = scheduler()
        scheduler.schedule("I:1", 1, policy, networkError, retryable = true) {
            fired += "1"
            scheduler.schedule("I:1", 2, policy, networkError, retryable = true) { fired += "2" }
        }

        advance(5)
        assertTrue(scheduler.isScheduled("I:1"))
        advance(10)
        assertEquals(listOf("1", "2"), fired)
    }

    @Test
    fun `default parameters build a working scheduler`() = runTest {
        val scheduler = RetryScheduler(backgroundScope, foreground, AdLog({ _, _, _, _ -> }, LogLevel.NONE))
        val delay = scheduler.schedule("I:1", 1, policy, AdError.NoFill(), retryable = true) { fired += "a" }
        assertEquals("the default no-fill floor", 30.seconds, delay)
        advance(30)
        assertEquals(listOf("a"), fired)
    }

    // --- Network ---

    @Test
    fun `a retry scheduled offline waits for the network, then runs at once`() = runTest {
        val scheduler = scheduler()
        network.value = NetworkState.Unavailable
        scheduler.schedule("I:1", 1, policy, networkError, retryable = true) { fired += "a" }

        advance(600)
        assertEquals("nothing while offline, however long", emptyList<String>(), fired)
        assertTrue(scheduler.isScheduled("I:1"))

        network.value = wifi
        runCurrent()
        assertEquals("at once, without its 5 s delay", listOf("a"), fired)
    }

    @Test
    fun `a returning network gets a few seconds to validate its internet`() = runTest {
        val scheduler = scheduler()
        network.value = NetworkState.Unavailable
        scheduler.schedule("I:1", 1, policy, networkError, retryable = true) { fired += "a" }

        network.value = NetworkState.ConnectedNoInternet(NetworkTransport.WIFI)
        advance(2)
        assertEquals(emptyList<String>(), fired)
        network.value = wifi
        runCurrent()
        assertEquals(listOf("a"), fired)

        // A network that never validates (a sign-in page) still gets the retry after the grace.
        network.value = NetworkState.Unavailable
        scheduler.schedule("I:2", 1, policy, networkError, retryable = true) { fired += "b" }
        network.value = NetworkState.ConnectedNoInternet(NetworkTransport.WIFI)
        advance(4)
        assertEquals(listOf("a"), fired)
        advance(1)
        assertEquals(listOf("a", "b"), fired)
    }

    @Test
    fun `a retry that comes due offline waits for the network`() = runTest {
        val scheduler = scheduler()
        scheduler.schedule("I:1", 1, policy, networkError, retryable = true) { fired += "a" }
        advance(2)
        network.value = NetworkState.Unavailable
        advance(600)
        assertEquals(emptyList<String>(), fired)

        network.value = wifi
        runCurrent()
        assertEquals(listOf("a"), fired)
    }

    @Test
    fun `a network without validated internet doesn't pause retries`() = runTest {
        val scheduler = scheduler()
        network.value = NetworkState.ConnectedNoInternet(NetworkTransport.WIFI)
        scheduler.schedule("I:1", 1, policy, networkError, retryable = true) { fired += "a" }
        advance(5)
        assertEquals(listOf("a"), fired)
    }

    @Test
    fun `a paused retry can still be cancelled`() = runTest {
        val scheduler = scheduler()
        network.value = NetworkState.Unavailable
        scheduler.schedule("I:1", 1, policy, networkError, retryable = true) { fired += "a" }
        scheduler.cancel("I:1")
        network.value = wifi
        advance(600)
        assertEquals(emptyList<String>(), fired)
    }
}
