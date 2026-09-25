package com.company.admob.internal.runtime

import android.os.Looper
import com.company.admob.AdError
import com.company.admob.AdsState
import com.company.admob.CompanyAdMob
import com.company.admob.config.AdEnvironment
import com.company.admob.config.AdMobConfig
import com.company.admob.events.AdEvent
import com.company.admob.internal.storage.SharedPrefsKeyValueStore
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
        CompanyAdMob.initialize(app, config) { _, _ -> deps() }

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    private fun warnings(): List<String> =
        CompanyAdMob.eventBus.recent().filterIsInstance<AdEvent.ConfigurationWarning>().map { it.message }

    @After
    fun tearDown() = CompanyAdMob.resetForTests()

    @Test
    fun `initialize reaches AwaitingStart without any Google SDK or consent call`() = runTest {
        val h = RuntimeHarness(this)
        h.initialize()
        h.settle()

        assertEquals(AdsState.AwaitingStart, CompanyAdMob.state.value)
        assertSame(CompanyAdMob.runtime?.client, CompanyAdMob.delegate)
        assertTrue(h.adsSdk.initializeCalls.isEmpty())
        assertTrue(h.adsSdk.requestConfigUpdates.isEmpty())
        assertTrue(h.consentSdk.updateRequests.isEmpty())
        assertEquals(0, h.consentSdk.formShowCount)
        assertEquals(1, h.registerCount)
    }

    @Test
    fun `a second initialize with an equal config does nothing`() = runTest {
        val h = RuntimeHarness(this)
        h.initialize()
        val runtime = CompanyAdMob.runtime
        h.initialize(testConfig(h.logs))
        h.settle()

        assertSame(runtime, CompanyAdMob.runtime)
        assertEquals(1, h.depsBuilt)
        assertEquals(1, h.registerCount)
        assertEquals(emptyList<String>(), h.logs.errors)
    }

    @Test
    fun `a different config is ignored, with an error and a configuration warning`() = runTest {
        val h = RuntimeHarness(this)
        h.initialize()
        val runtime = CompanyAdMob.runtime
        val before = warnings().size

        h.initialize(testConfig(h.logs) { environment = AdEnvironment.STAGING })
        h.settle()

        assertSame(runtime, CompanyAdMob.runtime)
        assertEquals(AdEnvironment.DEVELOPMENT, runtime?.config?.environment)
        assertEquals(1, h.logs.errors.count { it.contains("different configuration") })
        assertEquals(before + 1, warnings().size)
    }

    @Test
    fun `invalid config with failFast throws, listing every error, and stays uninitialized`() = runTest {
        val h = RuntimeHarness(this).apply { manifestAppId = null }
        val config = testConfig(h.logs) { placements { interstitial(com.company.admob.InterstitialPlacement("bad"), adUnitId = "nope") } }
        try {
            h.initialize(config)
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!, e.message!!.contains("APPLICATION_ID"))
            assertTrue(e.message!!, e.message!!.contains("nope"))
        }
        assertEquals(AdsState.NotInitialized, CompanyAdMob.state.value)
        assertEquals(null, CompanyAdMob.runtime)
        assertEquals(0, h.registerCount)

        // Fixing the problem and calling again works.
        h.manifestAppId = com.company.admob.config.TestIds.APP_ID
        h.initialize()
        h.settle()
        assertEquals(AdsState.AwaitingStart, CompanyAdMob.state.value)
    }

    @Test
    fun `invalid config without failFast sets Failed(InvalidConfiguration) and start reports it`() = runTest {
        val h = RuntimeHarness(this).apply { manifestAppId = null }
        h.initialize(testConfig(h.logs) { advanced { failFast = false } })
        h.settle()

        val state = CompanyAdMob.state.value
        assertTrue("$state", state is AdsState.Failed && state.error is AdError.InvalidConfiguration)
        assertEquals(0, h.registerCount)

        val results = mutableListOf<AdsState>()
        CompanyAdMob.start(android.app.Activity()) { results += it }
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
        assertEquals(5, CompanyAdMob.runtime?.launchCount)
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
        assertEquals(AdsState.AwaitingStart, CompanyAdMob.state.value)
    }

    @Test
    fun `the facade client is stable and the state flow is the same instance before and after`() = runTest {
        val stateBefore = CompanyAdMob.state
        val delegateBefore = CompanyAdMob.delegate
        val h = RuntimeHarness(this)
        h.initialize()
        h.settle()

        assertSame(CompanyAdMob, CompanyAdMob.client)
        assertSame(stateBefore, CompanyAdMob.state)
        assertNotSame(delegateBefore, CompanyAdMob.delegate)
    }
}
