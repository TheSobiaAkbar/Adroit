package io.github.thesobiaakbar.adroit.internal.runtime

import android.os.Looper
import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.AdsState
import io.github.thesobiaakbar.adroit.Adroit
import io.github.thesobiaakbar.adroit.AdroitInternal
import io.github.thesobiaakbar.adroit.InternalAdroitApi
import io.github.thesobiaakbar.adroit.config.AdEnvironment
import io.github.thesobiaakbar.adroit.config.AdMobConfig
import io.github.thesobiaakbar.adroit.events.AdEvent
import io.github.thesobiaakbar.adroit.internal.storage.SharedPrefsKeyValueStore
import io.github.thesobiaakbar.adroit.logging.AdLogger
import io.github.thesobiaakbar.adroit.logging.LogLevel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class InitializeTest {
    private fun RuntimeHarness.initialize(config: AdMobConfig = testConfig(logs)) =
        Adroit.initialize(app, config) { _, _ -> deps() }

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    private fun warnings(): List<String> =
        Adroit.eventBus.recent().filterIsInstance<AdEvent.ConfigurationWarning>().map { it.message }

    @After
    fun tearDown() = Adroit.resetForTests()

    @Test
    fun `initialize reaches AwaitingStart without any Google SDK or consent call`() = runTest {
        val h = RuntimeHarness(this)
        h.initialize()
        h.settle()

        assertEquals(AdsState.AwaitingStart, Adroit.state.value)
        assertSame(Adroit.runtime?.client, Adroit.delegate)
        assertTrue(h.adsSdk.initializeCalls.isEmpty())
        assertTrue(h.adsSdk.requestConfigUpdates.isEmpty())
        assertTrue(h.consentSdk.updateRequests.isEmpty())
        assertEquals(0, h.consentSdk.formShowCount)
        assertEquals(1, h.registerCount)
    }

    @OptIn(InternalAdroitApi::class)
    @Test
    fun `the internal log hook writes through the host's logger and level`() = runTest {
        val lines = mutableListOf<String>()
        val h = RuntimeHarness(this)
        h.initialize(
            testConfig {
                logging {
                    level = LogLevel.WARN
                    logger = AdLogger { level, _, message, _ -> lines += "$level $message" }
                }
            },
        )
        AdroitInternal.log(LogLevel.DEBUG, "below the configured level")
        AdroitInternal.log(LogLevel.ERROR, "from the views module")
        assertEquals(listOf("ERROR from the views module"), lines)
    }

    @Test
    fun `a second initialize with an equal config does nothing`() = runTest {
        val h = RuntimeHarness(this)
        h.initialize()
        val runtime = Adroit.runtime
        h.initialize(testConfig(h.logs))
        h.settle()

        assertSame(runtime, Adroit.runtime)
        assertEquals(1, h.depsBuilt)
        assertEquals(1, h.registerCount)
        assertEquals(emptyList<String>(), h.logs.errors)
    }

    @Test
    fun `a different config is ignored, with an error and a configuration warning`() = runTest {
        val h = RuntimeHarness(this)
        h.initialize()
        val runtime = Adroit.runtime
        val before = warnings().size

        h.initialize(testConfig(h.logs) { environment = AdEnvironment.STAGING })
        h.settle()

        assertSame(runtime, Adroit.runtime)
        assertEquals(AdEnvironment.DEVELOPMENT, runtime?.config?.environment)
        assertEquals(1, h.logs.errors.count { it.contains("different configuration") })
        assertEquals(before + 1, warnings().size)
    }

    @Test
    fun `invalid config with failFast throws, listing every error, and stays uninitialized`() = runTest {
        val h = RuntimeHarness(this).apply { manifestAppId = null }
        val config = testConfig(h.logs) { placements { interstitial(io.github.thesobiaakbar.adroit.InterstitialPlacement("bad"), adUnitId = "nope") } }
        try {
            h.initialize(config)
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!, e.message!!.contains("APPLICATION_ID"))
            assertTrue(e.message!!, e.message!!.contains("nope"))
        }
        assertEquals(AdsState.NotInitialized, Adroit.state.value)
        assertEquals(null, Adroit.runtime)
        assertEquals(0, h.registerCount)

        // Fixing the problem and calling again works.
        h.manifestAppId = io.github.thesobiaakbar.adroit.config.TestIds.APP_ID
        h.initialize()
        h.settle()
        assertEquals(AdsState.AwaitingStart, Adroit.state.value)
    }

    @Test
    fun `invalid config without failFast sets Failed(InvalidConfiguration) and start reports it`() = runTest {
        val h = RuntimeHarness(this).apply { manifestAppId = null }
        h.initialize(testConfig(h.logs) { advanced { failFast = false } })
        h.settle()

        val state = Adroit.state.value
        assertTrue("$state", state is AdsState.Failed && state.error is AdError.InvalidConfiguration)
        assertEquals(0, h.registerCount)

        val results = mutableListOf<AdsState>()
        Adroit.start(android.app.Activity()) { results += it }
        h.settle()
        assertEquals(listOf(state), results)
        assertTrue(h.consentSdk.updateRequests.isEmpty())
    }

    @Test
    fun `the launch count increments once per process`() = runTest {
        val h = RuntimeHarness(this)
        h.store.values[SharedPrefsKeyValueStore.KEY_LAUNCH_COUNT] = 4
        h.initialize()
        h.initialize()
        h.settle()

        assertEquals(5, h.store.values[SharedPrefsKeyValueStore.KEY_LAUNCH_COUNT])
        assertEquals(5, Adroit.runtime?.launchCount)
    }

    @Test
    fun `the launch count and the consent warm-up run on the IO dispatcher, not during initialize`() = runTest {
        val h = RuntimeHarness(this)
        h.ioDispatcher = StandardTestDispatcher(testScheduler)
        h.store.values[SharedPrefsKeyValueStore.KEY_LAUNCH_COUNT] = 4
        h.initialize()

        assertEquals(4, h.store.values[SharedPrefsKeyValueStore.KEY_LAUNCH_COUNT])
        assertEquals(0, Adroit.runtime?.launchCount)
        assertEquals(0, h.consentSdk.warmUps)
        h.settle()
        assertEquals(5, h.store.values[SharedPrefsKeyValueStore.KEY_LAUNCH_COUNT])
        assertEquals(5, Adroit.runtime?.launchCount)
        assertEquals(1, h.consentSdk.warmUps)
    }

    @Test
    fun `concurrent initialize from 8 threads sets up once`() = runTest {
        val h = RuntimeHarness(this)
        val config = testConfig(h.logs)
        val go = CountDownLatch(1)
        val done = CountDownLatch(8)
        val failures = mutableListOf<Throwable>()
        repeat(8) {
            Thread {
                try {
                    go.await()
                    h.initialize(config)
                } catch (t: Throwable) {
                    synchronized(failures) { failures += t }
                } finally {
                    done.countDown()
                }
            }.start()
        }
        go.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        idleMain()

        assertEquals(emptyList<Throwable>(), failures)
        assertEquals(1, h.depsBuilt)
        assertEquals(1, h.registerCount)
        assertEquals(1, h.store.values[SharedPrefsKeyValueStore.KEY_LAUNCH_COUNT])
        assertEquals(AdsState.AwaitingStart, Adroit.state.value)
    }

    @Test
    fun `the facade client is stable and the state flow is the same instance before and after`() = runTest {
        val stateBefore = Adroit.state
        val delegateBefore = Adroit.delegate
        val h = RuntimeHarness(this)
        h.initialize()
        h.settle()

        assertSame(Adroit, Adroit.client)
        assertSame(stateBefore, Adroit.state)
        assertNotSame(delegateBefore, Adroit.delegate)
    }
}
