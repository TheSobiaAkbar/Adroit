package io.github.thesobiaakbar.adroit.firebase

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.github.thesobiaakbar.adroit.firebase.internal.ActivationSignal
import io.github.thesobiaakbar.adroit.firebase.internal.FirebaseRemoteConfigSource
import io.github.thesobiaakbar.adroit.firebase.internal.RemoteConfigSource
import io.github.thesobiaakbar.adroit.firebase.internal.log
import io.github.thesobiaakbar.adroit.logging.LogLevel
import io.github.thesobiaakbar.adroit.network.NetworkMonitor
import io.github.thesobiaakbar.adroit.network.NetworkState
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Fetches Firebase Remote Config once per app session, without ever making the app wait (docs/REMOTE_CONFIG.md).
 *
 * [start] it in `Application.onCreate`: it fetches and activates at once. After the first successful fetch,
 * [isFetched] turns true and it never fetches again in this process. Until then, it tries again whenever an Activity
 * is created and whenever the device comes back online, one attempt at a time. It skips attempts while the device is
 * offline.
 *
 * Each activation reaches `FirebaseRemoteConfigProvider` at once, so the next ad request uses the new values. Nothing
 * waits for it: code that needs a value reads the one active now (from the last session's fetch, or the default).
 */
public class RemoteConfigSession internal constructor(
    internal val source: RemoteConfigSource,
    private val isOnline: () -> Boolean,
) {
    private val fetched = MutableStateFlow(false)
    private val inFlight = AtomicBoolean(false)

    /** True once this session fetched and activated successfully; it never turns back to false. Any thread. */
    public val isFetched: StateFlow<Boolean> = fetched.asStateFlow()

    /**
     * Fetches and activates, unless this session already did, an attempt is running, or the device is offline.
     * Returns at once; any thread. [start] calls it on every trigger, so apps rarely need to.
     */
    public fun fetchIfNeeded() {
        if (fetched.value) return
        if (!isOnline()) {
            log(LogLevel.DEBUG, "Remote Config fetch skipped: offline. Trying again when the device is online")
            return
        }
        if (!inFlight.compareAndSet(false, true)) return
        log(LogLevel.DEBUG, "Fetching Remote Config")
        source.fetchAndActivate { success ->
            if (success) {
                fetched.value = true
                log(LogLevel.INFO, "Remote Config fetched and activated; not fetching again this session")
                ActivationSignal.notifyActivated()
            } else {
                log(LogLevel.WARN, "Remote Config fetch failed; trying again on the next screen or network change")
            }
            inFlight.set(false)
        }
    }

    override fun toString(): String = "RemoteConfigSession(isFetched=${fetched.value})"

    /** Starting the process-wide session. */
    public companion object {
        @Volatile
        private var instance: RemoteConfigSession? = null

        /**
         * Starts the process-wide session in `Application.onCreate` and fetches at once. Later calls return the same
         * session. Firebase must already be initialized (the google-services plugin does it). Main thread.
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

        /** The session from [start], or null before it. Any thread. */
        @JvmStatic
        public fun current(): RemoteConfigSession? = instance

        private fun create(application: Application, remoteConfig: FirebaseRemoteConfig): RemoteConfigSession {
            val network = NetworkMonitor.getInstance(application)
            val session = RemoteConfigSession(FirebaseRemoteConfigSource(remoteConfig), network::isOnline)
            session.retryOn(application, network.state)
            session.fetchIfNeeded()
            return session
        }
    }

    /** Tries again on every new Activity and on each return online, until the first success. */
    internal fun retryOn(application: Application, network: StateFlow<NetworkState>) {
        // Unconfined: fetchIfNeeded is thread-safe and returns at once, so no dispatcher is needed.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
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
        network.filter { it.isOnline }.onEach { fetchIfNeeded() }.launchIn(scope)
        // Stop listening once fetched: nothing more to do this session.
        fetched.filter { it }.onEach {
            application.unregisterActivityLifecycleCallbacks(callbacks)
            scope.cancel()
        }.launchIn(scope)
    }
}
