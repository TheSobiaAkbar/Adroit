package com.company.admob.network

import android.net.NetworkCapabilities
import com.company.admob.internal.network.AndroidNetworkSource
import com.company.admob.internal.network.NetworkSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
class NetworkMonitorTest {
    private class FakeSource(var initial: NetworkState) : NetworkSource {
        var onChange: ((NetworkState) -> Unit)? = null
        override fun current(): NetworkState = initial
        override fun register(onChange: (NetworkState) -> Unit) {
            this.onChange = onChange
        }
    }

    private fun capabilities(transport: Int, vararg capabilities: Int): NetworkCapabilities =
        ShadowNetworkCapabilities.newInstance().also { caps ->
            val shadow = org.robolectric.Shadows.shadowOf(caps)
            shadow.addTransportType(transport)
            capabilities.forEach(shadow::addCapability)
        }

    @Test
    fun `validated wifi is online and unmetered`() {
        val state = AndroidNetworkSource.stateOf(
            capabilities(
                NetworkCapabilities.TRANSPORT_WIFI,
                NetworkCapabilities.NET_CAPABILITY_INTERNET,
                NetworkCapabilities.NET_CAPABILITY_VALIDATED,
                NetworkCapabilities.NET_CAPABILITY_NOT_METERED,
            ),
        )
        assertEquals(NetworkState.Connected(NetworkTransport.WIFI, isMetered = false), state)
    }

    @Test
    fun `validated mobile data is online and metered`() {
        val state = AndroidNetworkSource.stateOf(
            capabilities(
                NetworkCapabilities.TRANSPORT_CELLULAR,
                NetworkCapabilities.NET_CAPABILITY_INTERNET,
                NetworkCapabilities.NET_CAPABILITY_VALIDATED,
            ),
        )
        assertEquals(NetworkState.Connected(NetworkTransport.CELLULAR, isMetered = true), state)
    }

    @Test
    fun `wifi without validated internet is connected but offline`() {
        val state = AndroidNetworkSource.stateOf(
            capabilities(NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.NET_CAPABILITY_INTERNET),
        )
        assertEquals(NetworkState.ConnectedNoInternet(NetworkTransport.WIFI), state)
        assertFalse(state.isOnline)
    }

    @Test
    fun `no network, or one without internet capability, is unavailable`() {
        assertEquals(NetworkState.Unavailable, AndroidNetworkSource.stateOf(null))
        assertEquals(NetworkState.Unavailable, AndroidNetworkSource.stateOf(capabilities(NetworkCapabilities.TRANSPORT_WIFI)))
    }

    @Test
    fun `follows the source's changes`() {
        val source = FakeSource(NetworkState.Unavailable)
        val monitor = NetworkMonitor(source)
        assertFalse(monitor.isOnline)

        source.onChange!!(NetworkState.Connected(NetworkTransport.CELLULAR, isMetered = true))
        assertTrue(monitor.isOnline)
        assertTrue(monitor.isCellular)
        assertFalse(monitor.isWifi)

        source.onChange!!(NetworkState.ConnectedNoInternet(NetworkTransport.WIFI))
        assertFalse(monitor.isOnline)
        assertFalse(monitor.isWifi)
    }
}
