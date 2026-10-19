package io.github.thesobiaakbar.adroit.billing.firebase

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import io.github.thesobiaakbar.adroit.billing.BillingConfigSource
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Billing and paywall settings from Firebase Remote Config:
 *
 * ```
 * AdroitBilling.initialize(app, BillingSettings(…, config = FirebaseBillingConfig.source()))
 * ```
 *
 * The source answers at once from the values Remote Config has active now: the last session's fetch, or none (the
 * enum defaults apply). It fetches and activates once per process, never waiting: at once, then again on each new
 * Activity and each return online until a fetch succeeds, one attempt at a time, none while offline. Each successful
 * activation reloads the billing configuration; a paywall already open keeps its values, the next one uses the new
 * ones. An app that also runs `adroit-firebase`'s `RemoteConfigSession` hits the network once: Firebase answers the
 * second fetch from its cache.
 *
 * Remote Config keys: `AdroitBilling`, and one per [io.github.thesobiaakbar.adroit.billing.PaywallDefinition] name.
 * In-app defaults (`setDefaultsAsync`) are read too; Firebase's static default (an empty string) is "no value".
 */
public object FirebaseBillingConfig {
    @Volatile
    private var shared: BillingConfigSource? = null

    /**
     * The process-wide source (later calls return the first one, whatever [remoteConfig] they pass). When Firebase
     * isn't initialized (for example a debug variant without google-services), it logs once and behaves as
     * [BillingConfigSource.none]. Never throws; never blocks. Main thread.
     *
     * @param remoteConfig the instance to read, for apps with several Firebase apps; null for the default one.
     */
    @JvmStatic
    @JvmOverloads
    public fun source(remoteConfig: FirebaseRemoteConfig? = null): BillingConfigSource = shared ?: synchronized(this) {
        shared ?: create(remoteConfig).also { shared = it }
    }

    private fun create(remoteConfig: FirebaseRemoteConfig?): BillingConfigSource = try {
        val config = remoteConfig ?: FirebaseRemoteConfig.getInstance()
        val app = FirebaseApp.getInstance().applicationContext as Application
        RemoteConfigFetcher(FirebaseRemoteConfigSource(config)) { app.isOnline() }.also { it.retryOn(app) }
    } catch (e: IllegalStateException) {
        Log.w(TAG, "Firebase isn't initialized; paywalls use their in-code defaults. ${e.message}")
        BillingConfigSource.none()
    }
}

/** The part of Firebase Remote Config the source uses, so it is testable without Firebase. */
internal interface RemoteConfigSource {
    /** The active value of [key], or null when only Firebase's static default sets it. */
    fun string(key: String): String?

    /** Fetches and activates, then calls [onDone] (any thread) with whether it succeeded. */
    fun fetchAndActivate(onDone: (success: Boolean) -> Unit)
}

internal class FirebaseRemoteConfigSource(private val remoteConfig: FirebaseRemoteConfig) : RemoteConfigSource {
    override fun string(key: String): String? = remoteConfig.getValue(key).orNull()

    override fun fetchAndActivate(onDone: (success: Boolean) -> Unit) {
        remoteConfig.fetchAndActivate().addOnCompleteListener { onDone(it.isSuccessful) }
    }
}

/** The value, or null when it is Firebase's static default (no remote value and no in-app default). */
internal fun FirebaseRemoteConfigValue.orNull(): String? =
    if (source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) null else asString()

/** [source] as a [BillingConfigSource] that fetches until the first success (see [FirebaseBillingConfig]). */
internal class RemoteConfigFetcher(
    private val source: RemoteConfigSource,
    private val isOnline: () -> Boolean,
) : BillingConfigSource {
    private val activations = MutableStateFlow(0)
    private val inFlight = AtomicBoolean(false)

    /** Set by [retryOn]: removes its triggers once fetched. */
    @Volatile
    private var stopRetrying: (() -> Unit)? = null

    val isFetched: Boolean
        get() = activations.value > 0

    override fun raw(key: String): String? = source.string(key)

    // Emits on collection when an activation already happened, so a fetch that lands before the runtime starts
    // collecting isn't lost; at worst that is one extra reload.
    override val updates: Flow<Unit> = activations.filter { it > 0 }.map { }

    /** Fetches and activates unless already fetched, an attempt is running, or offline. Returns at once; any thread. */
    fun fetchIfNeeded() {
        if (isFetched) return
        if (!isOnline()) {
            Log.d(TAG, "Billing Remote Config fetch skipped: offline")
            return
        }
        if (!inFlight.compareAndSet(false, true)) return
        source.fetchAndActivate { success ->
            if (success) {
                activations.value++
                stopRetrying?.invoke()
                stopRetrying = null
                Log.i(TAG, "Billing Remote Config fetched and activated; not fetching again this session")
            } else {
                Log.w(TAG, "Billing Remote Config fetch failed; trying again on the next screen or network return")
            }
            inFlight.set(false)
        }
    }

    /** Fetches now, then on every new Activity and each return online, until the first success. */
    @SuppressLint("MissingPermission") // ACCESS_NETWORK_STATE is in this module's manifest.
    fun retryOn(application: Application) {
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = fetchIfNeeded()
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        application.registerActivityLifecycleCallbacks(callbacks)
        val connectivity = application.getSystemService(ConnectivityManager::class.java)
        val network = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = fetchIfNeeded()
        }
        try {
            connectivity?.registerDefaultNetworkCallback(network)
        } catch (e: RuntimeException) {
            // "Too many NetworkRequests" on some devices: the Activity trigger still retries.
        }
        stopRetrying = {
            application.unregisterActivityLifecycleCallbacks(callbacks)
            try {
                connectivity?.unregisterNetworkCallback(network)
            } catch (e: RuntimeException) {
                // Never registered.
            }
        }
        fetchIfNeeded()
    }
}

/** Whether the default network has internet. Assumes online when it can't tell, rather than never fetching. */
@SuppressLint("MissingPermission") // ACCESS_NETWORK_STATE is in this module's manifest.
private fun Context.isOnline(): Boolean = try {
    val connectivity = getSystemService(ConnectivityManager::class.java)
    connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: (connectivity == null)
} catch (e: RuntimeException) {
    true
}

internal const val TAG: String = "AdroitBilling"
