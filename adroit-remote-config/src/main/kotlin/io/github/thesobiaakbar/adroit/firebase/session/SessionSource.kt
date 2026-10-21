package io.github.thesobiaakbar.adroit.firebase.session

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue

/** The part of Firebase Remote Config the session uses, so it is testable without Firebase. */
internal interface SessionSource {
    /** The active value of [key], or null when only Firebase's static default sets it. */
    fun string(key: String): String?

    /** Fetches and activates, then calls [onDone] (any thread) with whether it succeeded. */
    fun fetchAndActivate(onDone: (success: Boolean) -> Unit)
}

internal class FirebaseSessionSource(private val remoteConfig: FirebaseRemoteConfig) : SessionSource {
    override fun string(key: String): String? = remoteConfig.getValue(key).orNull()

    override fun fetchAndActivate(onDone: (success: Boolean) -> Unit) {
        remoteConfig.fetchAndActivate().addOnCompleteListener { onDone(it.isSuccessful) }
    }
}

/** The value, or null when it is Firebase's static default (no remote value and no in-app default). */
internal fun FirebaseRemoteConfigValue.orNull(): String? =
    if (source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) null else asString()

/** Whether the default network has internet. Assumes online when it can't tell, rather than never fetching. */
@SuppressLint("MissingPermission") // ACCESS_NETWORK_STATE is in this module's manifest.
internal fun Context.isOnline(): Boolean = try {
    val connectivity = getSystemService(ConnectivityManager::class.java)
    connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: (connectivity == null)
} catch (e: RuntimeException) {
    true
}

/** Calls [onAvailable] (any thread) each time a default network becomes available. Returns the removal function. */
@SuppressLint("MissingPermission") // ACCESS_NETWORK_STATE is in this module's manifest.
internal fun Context.onNetworkAvailable(onAvailable: () -> Unit): () -> Unit {
    val connectivity = getSystemService(ConnectivityManager::class.java) ?: return {}
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = onAvailable()
    }
    try {
        connectivity.registerDefaultNetworkCallback(callback)
    } catch (e: RuntimeException) {
        // "Too many NetworkRequests" on some devices: the Activity trigger still retries.
        return {}
    }
    return {
        try {
            connectivity.unregisterNetworkCallback(callback)
        } catch (e: RuntimeException) {
            // Already gone.
        }
    }
}

internal const val TAG: String = "AdroitRemoteConfig"
