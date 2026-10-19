package io.github.thesobiaakbar.adroit.billing.firebase

import android.app.Activity
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FirebaseBillingConfigTest {
    /** Each fetch waits until the test completes it with [finish]. */
    private class FakeSource(val values: Map<String, String> = emptyMap()) : RemoteConfigSource {
        var fetches = 0
        private var pending: ((Boolean) -> Unit)? = null

        fun finish(success: Boolean) {
            val done = checkNotNull(pending) { "No fetch in flight" }
            pending = null
            done(success)
        }

        override fun string(key: String): String? = values[key]
        override fun fetchAndActivate(onDone: (success: Boolean) -> Unit) {
            fetches++
            pending = onDone
        }
    }

    private class Value(private val text: String, private val source: Int) : FirebaseRemoteConfigValue {
        override fun asString() = text
        override fun getSource() = source
        override fun asLong() = 0L
        override fun asDouble() = 0.0
        override fun asByteArray() = text.toByteArray()
        override fun asBoolean() = false
    }

    private var online = true
    private val source = FakeSource(mapOf("AdroitBilling" to """{"enabled":false}"""))
    private val fetcher = RemoteConfigFetcher(source) { online }

    @Test
    fun `remote and in-app default values are read, Firebase's static default is no value`() {
        assertEquals("{}", Value("{}", FirebaseRemoteConfig.VALUE_SOURCE_REMOTE).orNull())
        assertEquals("{}", Value("{}", FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT).orNull())
        assertNull(Value("", FirebaseRemoteConfig.VALUE_SOURCE_STATIC).orNull())
    }

    @Test
    fun `raw answers at once from the active values, before any fetch`() {
        assertEquals("""{"enabled":false}""", fetcher.raw("AdroitBilling"))
        assertNull(fetcher.raw("Onboarding"))
        assertEquals(0, source.fetches)
    }

    @Test
    fun `at most one fetchAndActivate per process, and the caller never waits`() {
        fetcher.fetchIfNeeded()
        fetcher.fetchIfNeeded() // In flight: returns at once without a second fetch.
        assertEquals(1, source.fetches)

        source.finish(success = true)
        repeat(3) { fetcher.fetchIfNeeded() }

        assertTrue(fetcher.isFetched)
        assertEquals(1, source.fetches)
    }

    @Test
    fun `a failure lets the next trigger fetch again, and offline skips`() {
        fetcher.fetchIfNeeded()
        source.finish(success = false)
        assertFalse(fetcher.isFetched)

        online = false
        fetcher.fetchIfNeeded()
        assertEquals(1, source.fetches)

        online = true
        fetcher.fetchIfNeeded()
        assertEquals(2, source.fetches)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `updates emit after an activation, also to a collector that arrives late`() = runTest(UnconfinedTestDispatcher()) {
        val early = mutableListOf<Unit>()
        val job = launch { fetcher.updates.toList(early) }
        fetcher.fetchIfNeeded()
        assertTrue(early.isEmpty())

        source.finish(success = true)

        assertEquals(1, early.size)
        fetcher.updates.first() // A collector after the fetch still sees it.
        job.cancel()
    }

    @Test
    fun `a new Activity retries until the first success, then nothing more`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        fetcher.retryOn(app)
        assertEquals(1, source.fetches)
        source.finish(success = false)

        Robolectric.buildActivity(Activity::class.java).create()
        assertEquals(2, source.fetches)
        source.finish(success = true)

        Robolectric.buildActivity(Activity::class.java).create()
        assertEquals(2, source.fetches)
    }

    @Test
    fun `without Firebase initialized the source is none and never throws`() {
        val config = FirebaseBillingConfig.source()

        assertNull(config.raw("AdroitBilling"))
        assertTrue(config === FirebaseBillingConfig.source())
    }
}
