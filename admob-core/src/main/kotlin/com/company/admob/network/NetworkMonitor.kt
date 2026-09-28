package com.company.admob.network

import android.content.Context
import com.company.admob.internal.network.AndroidNetworkSource
import com.company.admob.internal.network.NetworkSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The device's network connection, kept up to date for the whole process: whether it's online, over Wi-Fi or mobile
 * data, or connected without internet (for example hotel Wi-Fi that still needs a sign-in). Obtain it with
 * [getInstance]; it's cheap to call anywhere.
 *
 * "Online" means Android validated that the network reaches the internet (`NET_CAPABILITY_VALIDATED`). A network that
 * was just joined is [NetworkState.ConnectedNoInternet] for a moment, until Android finishes checking it.
 *
 * Use it to skip work that can't succeed offline (a splash waiting for an ad, a Remote Config fetch). It isn't a
 * guarantee: a request can still fail on a network reported online.
 */
public class NetworkMonitor internal constructor(source: NetworkSource) {
    private val mutableState = MutableStateFlow(source.current())

    init {
        source.register { mutableState.value = it }
    }

    /** The current connection. Updates on any thread; a collector sees each distinct state. */
    public val state: StateFlow<NetworkState> = mutableState.asStateFlow()

    /** Whether the device has a validated internet connection right now. Any thread. */
    public val isOnline: Boolean get() = state.value.isOnline

    /** Whether the device is online over Wi-Fi right now. Any thread. */
    public val isWifi: Boolean get() = (state.value as? NetworkState.Connected)?.transport == NetworkTransport.WIFI

    /** Whether the device is online over mobile data right now. Any thread. */
    public val isCellular: Boolean
        get() = (state.value as? NetworkState.Connected)?.transport == NetworkTransport.CELLULAR

    override fun toString(): String = "NetworkMonitor(state=${state.value})"

    /** The process-wide monitor. */
    public companion object {
        @Volatile
        private var instance: NetworkMonitor? = null

        /**
         * The process-wide monitor, created on the first call. It listens to Android's default network for the
         * life of the process, so there's nothing to release. Any thread.
         */
        @JvmStatic
        public fun getInstance(context: Context): NetworkMonitor = instance ?: synchronized(this) {
            instance ?: NetworkMonitor(AndroidNetworkSource(context.applicationContext)).also { instance = it }
        }
    }
}

/** How the device reaches the network. */
public enum class NetworkTransport {
    /** Wi-Fi. */
    WIFI,

    /** Mobile data. */
    CELLULAR,

    /** A wired connection. */
    ETHERNET,

    /** Anything else, for example Bluetooth tethering or a VPN whose underlying network isn't known. */
    OTHER,
}

/** A network connection state reported by [NetworkMonitor]. */
public sealed class NetworkState {
    /** Whether this state reaches the internet. */
    public abstract val isOnline: Boolean

    /** No network: airplane mode, or Wi-Fi and mobile data both off. */
    public object Unavailable : NetworkState() {
        override val isOnline: Boolean get() = false

        override fun toString(): String = "Unavailable"
    }

    /**
     * Connected over [transport], but Android hasn't validated internet access: a Wi-Fi sign-in page, a router
     * without internet, or a network still being checked.
     *
     * @property transport how the device is connected.
     */
    public class ConnectedNoInternet(public val transport: NetworkTransport) : NetworkState() {
        override val isOnline: Boolean get() = false

        override fun equals(other: Any?): Boolean = other is ConnectedNoInternet && other.transport == transport

        override fun hashCode(): Int = transport.hashCode()

        override fun toString(): String = "ConnectedNoInternet($transport)"
    }

    /**
     * Online over [transport].
     *
     * @property transport how the device is connected.
     * @property isMetered whether the user may pay for data (usually mobile data, or a Wi-Fi hotspot).
     */
    public class Connected(public val transport: NetworkTransport, public val isMetered: Boolean) : NetworkState() {
        override val isOnline: Boolean get() = true

        override fun equals(other: Any?): Boolean =
            other is Connected && other.transport == transport && other.isMetered == isMetered

        override fun hashCode(): Int = 31 * transport.hashCode() + isMetered.hashCode()

        override fun toString(): String = "Connected($transport, metered=$isMetered)"
    }
}
