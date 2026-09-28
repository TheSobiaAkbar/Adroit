package com.company.admob.internal.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.company.admob.network.NetworkState
import com.company.admob.network.NetworkTransport

/** Android's connectivity, behind a seam so [com.company.admob.network.NetworkMonitor] is testable. */
internal interface NetworkSource {
    /** The state now. */
    fun current(): NetworkState

    /** Calls [onChange] (any thread) whenever the state may have changed, for the life of the process. */
    fun register(onChange: (NetworkState) -> Unit)
}

internal class AndroidNetworkSource(context: Context) : NetworkSource {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    override fun current(): NetworkState = try {
        stateOf(connectivity?.getNetworkCapabilities(connectivity.activeNetwork))
    } catch (e: RuntimeException) {
        // A SecurityException without ACCESS_NETWORK_STATE, or an OEM bug: assume online rather than blocking work.
        NetworkState.Connected(NetworkTransport.OTHER, isMetered = false)
    }

    override fun register(onChange: (NetworkState) -> Unit) {
        val manager = connectivity ?: return
        try {
            manager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
                        onChange(stateOf(capabilities))

                    // Another default network may follow, with its own onCapabilitiesChanged.
                    override fun onLost(network: Network) = onChange(NetworkState.Unavailable)
                },
            )
        } catch (e: RuntimeException) {
            // "Too many NetworkRequests" on some devices: keep the state read at creation.
        }
    }

    companion object {
        fun stateOf(capabilities: NetworkCapabilities?): NetworkState {
            if (capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return NetworkState.Unavailable
            }
            val transport = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransport.ETHERNET
                else -> NetworkTransport.OTHER
            }
            return if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                NetworkState.Connected(
                    transport,
                    isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
                )
            } else {
                NetworkState.ConnectedNoInternet(transport)
            }
        }
    }
}
