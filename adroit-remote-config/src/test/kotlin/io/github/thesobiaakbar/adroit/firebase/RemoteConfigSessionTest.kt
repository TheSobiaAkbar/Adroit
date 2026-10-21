package io.github.thesobiaakbar.adroit.firebase

import android.app.Activity
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.thesobiaakbar.adroit.firebase.session.SessionSource
import io.github.thesobiaakbar.adroit.firebase.session.orNull
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
class RemoteConfigSessionTest {
    /** Each fetch waits until the test completes it with [finish]. */
    private class FakeSource(val values: Map<String, String> = emptyMap()) : SessionSource {
        var fetches = 0
        private var pending: ((Boolean) -> Unit)? = null

        fun finish(success: Boolean) {
            val done = checkNotNull(pending) { "No fetch in flight" }
            pending = null
            done(success)
        }

        override fun string(key: String): String? = values[key]
        override fun fetchAndActivate(onDone: (Boolean) -> Unit) {
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
    private val source = FakeSource(mapOf("AdSettings" to """{"adsEnabled":false}"""))
    private val session = RemoteConfigSession(source) { online }

    @Test
    fun `remote and in-app default values are read, Firebase's static default is no value`() {
        assertEquals("{}", Value("{}", FirebaseRemoteConfig.VALUE_SOURCE_REMOTE).orNull())
        assertEquals("{}", Value("{}", FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT).orNull())
        assertNull(Value("", FirebaseRemoteConfig.VALUE_SOURCE_STATIC).orNull())
    }

    @Test
    fun `string answers at once from the active values, before any fetch`() {
        assertEquals("""{"adsEnabled":false}""", session.string("AdSettings"))
        assertNull(session.string("Onboarding"))
        assertEquals(0, source.fetches)
    }

    @Test
    fun `a success ends fetching for the session`() {
        session.fetchIfNeeded()
        source.finish(success = true)

        assertTrue(session.isFetched.value)
        repeat(3) { session.fetchIfNeeded() }
        assertEquals(1, source.fetches)
    }

    @Test
    fun `a failure leaves the next trigger free to fetch again`() {
        session.fetchIfNeeded()
        source.finish(success = false)

        assertFalse(session.isFetched.value)
        session.fetchIfNeeded()
        assertEquals(2, source.fetches)
    }

    @Test
    fun `only one fetch at a time, and the caller never waits`() {
        session.fetchIfNeeded()
        session.fetchIfNeeded()
        assertEquals(1, source.fetches)
    }

    @Test
    fun `no attempt while offline`() {
        online = false
        session.fetchIfNeeded()
        assertEquals(0, source.fetches)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `activations emit after a success, also to a collector that arrives late`() = runTest(UnconfinedTestDispatcher()) {
        val early = mutableListOf<Unit>()
        val job = launch { session.activations.toList(early) }
        session.fetchIfNeeded()
        source.finish(success = false)
        assertTrue(early.isEmpty())

        session.fetchIfNeeded()
        source.finish(success = true)

        assertEquals(1, early.size)
        session.activations.first() // A collector after the fetch still sees it.
        job.cancel()
    }

    @Test
    fun `retries when an Activity is created and when the device comes back online, until one succeeds`() {
        var networkReturn: (() -> Unit)? = null
        var networkRemoved = false
        online = false
        session.retryOn(ApplicationProvider.getApplicationContext<Application>()) { callback ->
            networkReturn = callback
            { networkRemoved = true }
        }
        assertEquals(0, source.fetches)

        online = true
        checkNotNull(networkReturn).invoke() // back online
        assertEquals(1, source.fetches)
        source.finish(success = false)

        Robolectric.buildActivity(Activity::class.java).create() // the next screen
        assertEquals(2, source.fetches)
        source.finish(success = true)

        // Fetched: the triggers are removed and neither fetches again.
        assertTrue(networkRemoved)
        Robolectric.buildActivity(Activity::class.java).create()
        assertEquals(2, source.fetches)
    }

    @Test
    fun `without Firebase initialized startDefault returns null and never throws`() {
        assertNull(RemoteConfigSession.startDefault())
        assertNull(RemoteConfigSession.current())
    }
}
