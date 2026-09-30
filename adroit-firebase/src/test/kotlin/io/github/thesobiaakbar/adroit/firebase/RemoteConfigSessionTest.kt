package io.github.thesobiaakbar.adroit.firebase

import android.app.Activity
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.thesobiaakbar.adroit.firebase.internal.ActivationSignal
import io.github.thesobiaakbar.adroit.firebase.internal.RemoteConfigSource
import io.github.thesobiaakbar.adroit.network.NetworkState
import io.github.thesobiaakbar.adroit.network.NetworkTransport
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RemoteConfigSessionTest {
    /** Each fetch waits until the test completes it with [finish]. */
    private class FakeSource : RemoteConfigSource {
        var fetches = 0
        private var pending: ((Boolean) -> Unit)? = null

        fun finish(success: Boolean) {
            val done = checkNotNull(pending) { "No fetch in flight" }
            pending = null
            done(success)
        }

        override fun string(key: String): String? = null
        override fun addUpdateListener(onUpdate: () -> Unit): () -> Unit = {}
        override fun activate(onDone: () -> Unit) = onDone()
        override fun fetchAndActivate(onDone: (Boolean) -> Unit) {
            fetches++
            pending = onDone
        }
    }

    private val online = NetworkState.Connected(NetworkTransport.WIFI, isMetered = false)
    private val source = FakeSource()
    private val network = MutableStateFlow<NetworkState>(online)
    private val session = RemoteConfigSession(source) { network.value.isOnline }

    @Test
    fun `a success ends fetching for the session`() {
        session.fetchIfNeeded()
        source.finish(success = true)

        assertTrue(session.isFetched.value)
        session.fetchIfNeeded()
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
    fun `only one fetch at a time`() {
        session.fetchIfNeeded()
        session.fetchIfNeeded()
        assertEquals(1, source.fetches)
    }

    @Test
    fun `no attempt while offline or on a network without internet`() {
        network.value = NetworkState.Unavailable
        session.fetchIfNeeded()
        network.value = NetworkState.ConnectedNoInternet(NetworkTransport.WIFI)
        session.fetchIfNeeded()
        assertEquals(0, source.fetches)
    }

    @Test
    fun `a success tells the providers to re-read`() {
        var notified = 0
        val remove = ActivationSignal.add { notified++ }
        try {
            session.fetchIfNeeded()
            source.finish(success = false)
            assertEquals(0, notified)
            session.fetchIfNeeded()
            source.finish(success = true)
            assertEquals(1, notified)
        } finally {
            remove()
        }
    }

    @Test
    fun `retries when an Activity is created and when the device comes back online, until one succeeds`() {
        network.value = NetworkState.Unavailable
        session.retryOn(ApplicationProvider.getApplicationContext<Application>(), network)
        assertEquals(0, source.fetches)

        network.value = online // back online
        assertEquals(1, source.fetches)
        source.finish(success = false)

        Robolectric.buildActivity(Activity::class.java).create() // the next screen
        assertEquals(2, source.fetches)
        source.finish(success = true)

        // Fetched: neither trigger fetches again.
        Robolectric.buildActivity(Activity::class.java).create()
        network.value = NetworkState.Unavailable
        network.value = online
        assertEquals(2, source.fetches)
    }
}
