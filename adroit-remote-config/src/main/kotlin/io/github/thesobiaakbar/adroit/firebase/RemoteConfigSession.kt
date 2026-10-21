package io.github.thesobiaakbar.adroit.firebase

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import io.github.thesobiaakbar.adroit.firebase.session.FirebaseSessionSource
import io.github.thesobiaakbar.adroit.firebase.session.SessionSource
import io.github.thesobiaakbar.adroit.firebase.session.TAG
import io.github.thesobiaakbar.adroit.firebase.session.isOnline
import io.github.thesobiaakbar.adroit.firebase.session.onNetworkAvailable
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Fetches Firebase Remote Config once per app session, without ever making the app wait (docs/REMOTE_CONFIG.md). The
 * one fetch shared by the ads (`adroit-firebase`), billing (`adroit-billing-firebase`) and update (`adroit-update`)
 * modules: whichever of them is used first starts it, so apps don't need to call [start].
 *
 * It fetches and activates at once. After the first successful fetch, [isFetched] turns true and it never fetches
 * again in this process. Until then, it tries again whenever an Activity is created and whenever the device comes
 * back online, one attempt at a time. It skips attempts while the device is offline.
 *
 * Nothing waits for it: code that needs a value reads the one active now ([string]: the last session's fetch, or the
 * in-app default), and code that must follow a change collects [activations].
 */
public class RemoteConfigSession internal constructor(
    private val source: SessionSource,
    private val isOnline: () -> Boolean,
) {
    private val fetched = MutableStateFlow(false)
    private val inFlight = AtomicBoolean(false)

    /** Set by [retryOn]: removes its triggers once fetched. */
    @Volatile
    private var stopRetrying: (() -> Unit)? = null

    /** True once this session fetched and activated successfully; it never turns back to false. Any thread. */
    public val isFetched: StateFlow<Boolean> = fetched.asStateFlow()

    /**
     * Emits once this session's fetch has activated new values, also to a collector that arrives after it, so the
     * active values can be read again. Any thread.
     */
    public val activations: Flow<Unit> = fetched.filter { it }.map { }

    /**
     * The active value of [key] (this or the last session's fetch, or an in-app default), or null when only Firebase's
     * static default (an empty string) sets it. Never waits; any thread.
     */
    public fun string(key: String): String? = source.string(key)

    /**
     * Fetches and activates, unless this session already did, an attempt is running, or the device is offline.
     * Returns at once; any thread. The session calls it on every trigger, so apps rarely need to.
     */
    public fun fetchIfNeeded() {
        if (fetched.value) return
        if (!isOnline()) {
            Log.d(TAG, "Remote Config fetch skipped: offline. Trying again when the device is online")
            return
        }
        if (!inFlight.compareAndSet(false, true)) return
        Log.d(TAG, "Fetching Remote Config")
        source.fetchAndActivate { success ->
            if (success) {
                fetched.value = true
                stopRetrying?.invoke()
                stopRetrying = null
                Log.i(TAG, "Remote Config fetched and activated; not fetching again this session")
            } else {
                Log.w(TAG, "Remote Config fetch failed; trying again on the next screen or network return")
            }
            inFlight.set(false)
        }
    }

    override fun toString(): String = "RemoteConfigSession(isFetched=${fetched.value})"

    /**
     * Tries again on every new Activity and each time [registerNetwork]'s callback runs (the device is back online),
     * until the first success. [registerNetwork] returns its removal function.
     */
    internal fun retryOn(application: Application, registerNetwork: (() -> Unit) -> () -> Unit) {
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
        val removeNetwork = registerNetwork(::fetchIfNeeded)
        stopRetrying = {
            application.unregisterActivityLifecycleCallbacks(callbacks)
            removeNetwork()
        }
        // A fetch that succeeded before the triggers were set never ran stopRetrying.
        if (fetched.value) stopRetrying?.invoke()
    }

    /** Starting the process-wide session. */
    public companion object {
        @Volatile
        private var instance: RemoteConfigSession? = null

        private val warnedNoFirebase = AtomicBoolean(false)

        /**
         * Starts the process-wide session and fetches at once. Later calls, and [startDefault], return the same session,
         * whatever [remoteConfig] they pass. Firebase must already be initialized (the google-services plugin does it).
         * Apps need it only to use a [remoteConfig] other than the default one, before any Adroit module starts the
         * session. Any thread.
         *
         * @param remoteConfig the instance to fetch, for apps with several Firebase apps.
         */
        @JvmStatic
        @JvmOverloads
        public fun start(
            application: Application,
            remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance(),
        ): RemoteConfigSession = instance ?: synchronized(this) {
            instance ?: create(application, remoteConfig).also { instance = it }
        }

        /**
         * The process-wide session, started from Firebase's own application context if no one started it yet. Returns
         * null (logging once) when Firebase isn't initialized, for example in a debug variant without
         * google-services. Never throws; any thread.
         *
         * @param remoteConfig the instance to fetch if this call starts the session; null for the default one.
         */
        @JvmStatic
        @JvmOverloads
        public fun startDefault(remoteConfig: FirebaseRemoteConfig? = null): RemoteConfigSession? = instance ?: try {
            val application = FirebaseApp.getInstance().applicationContext as Application
            start(application, remoteConfig ?: FirebaseRemoteConfig.getInstance())
        } catch (e: IllegalStateException) {
            if (warnedNoFirebase.compareAndSet(false, true)) {
                Log.w(TAG, "Firebase isn't initialized; Remote Config values are the in-app defaults. ${e.message}")
            }
            null
        }

        /** The session from [start] or [startDefault], or null before either. Any thread. */
        @JvmStatic
        public fun current(): RemoteConfigSession? = instance

        private fun create(application: Application, remoteConfig: FirebaseRemoteConfig): RemoteConfigSession {
            val session = RemoteConfigSession(FirebaseSessionSource(remoteConfig)) { application.isOnline() }
            session.retryOn(application) { application.onNetworkAvailable(it) }
            session.fetchIfNeeded()
            return session
        }
    }
}
