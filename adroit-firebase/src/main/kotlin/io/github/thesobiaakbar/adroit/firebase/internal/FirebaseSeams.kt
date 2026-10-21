package io.github.thesobiaakbar.adroit.firebase.internal

import android.os.Bundle
import io.github.thesobiaakbar.adroit.AdroitInternal
import io.github.thesobiaakbar.adroit.InternalAdroitApi
import io.github.thesobiaakbar.adroit.logging.LogLevel
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The part of Firebase Remote Config the provider reads and its real-time updates, so the mapping is testable without
 * Firebase. Fetching belongs to `RemoteConfigSession` (adroit-remote-config).
 */
internal interface RemoteConfigSource {
    /** The activated value of [key], or null when neither Remote Config nor an in-app default sets it. */
    fun string(key: String): String?

    /** Calls [onUpdate] (any thread) when Remote Config has new values to activate. Returns the removal function. */
    fun addUpdateListener(onUpdate: () -> Unit): () -> Unit

    /** Activates fetched values, then calls [onDone] (any thread). */
    fun activate(onDone: () -> Unit)
}

/** The part of Google Analytics for Firebase the listener uses. */
internal fun interface AnalyticsSink {
    fun logEvent(name: String, params: Map<String, Any>)
}

internal class FirebaseRemoteConfigSource(private val remoteConfig: FirebaseRemoteConfig) : RemoteConfigSource {
    override fun string(key: String): String? {
        val value = remoteConfig.getValue(key)
        return if (value.source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) null else value.asString()
    }

    override fun addUpdateListener(onUpdate: () -> Unit): () -> Unit {
        val registration = remoteConfig.addOnConfigUpdateListener(
            object : ConfigUpdateListener {
                override fun onUpdate(configUpdate: ConfigUpdate) = onUpdate()

                override fun onError(error: FirebaseRemoteConfigException) {
                    log(LogLevel.WARN, "Firebase Remote Config real-time updates failed: ${error.code}")
                }
            },
        )
        return registration::remove
    }

    override fun activate(onDone: () -> Unit) {
        remoteConfig.activate().addOnCompleteListener { onDone() }
    }
}

internal class FirebaseAnalyticsSink(private val analytics: FirebaseAnalytics) : AnalyticsSink {
    override fun logEvent(name: String, params: Map<String, Any>) {
        val bundle = Bundle()
        params.forEach { (key, value) ->
            when (value) {
                is String -> bundle.putString(key, value)
                is Double -> bundle.putDouble(key, value)
                is Long -> bundle.putLong(key, value)
                is Int -> bundle.putLong(key, value.toLong())
                else -> bundle.putString(key, value.toString())
            }
        }
        analytics.logEvent(name, bundle)
    }
}

/** Through the host's configured Adroit logger and level. */
@OptIn(InternalAdroitApi::class)
internal fun log(level: LogLevel, message: String) {
    AdroitInternal.log(level, message)
}

/** Never emits and never completes: the activations when Firebase isn't initialized, so collectors stay alive. */
internal val never: Flow<Unit> = flow { awaitCancellation() }
