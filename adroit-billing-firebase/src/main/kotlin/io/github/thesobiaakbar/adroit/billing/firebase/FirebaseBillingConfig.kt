package io.github.thesobiaakbar.adroit.billing.firebase

import android.util.Log
import io.github.thesobiaakbar.adroit.billing.BillingConfigSource
import io.github.thesobiaakbar.adroit.firebase.RemoteConfigSession
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.flow.Flow

/**
 * Billing and paywall settings from Firebase Remote Config:
 *
 * ```
 * AdroitBilling.initialize(app, BillingSettings(…, config = FirebaseBillingConfig.source()))
 * ```
 *
 * The source answers at once from the values Remote Config has active now: the last session's fetch, or none (the
 * enum defaults apply). The fetch is the shared [RemoteConfigSession] (adroit-remote-config), the same one the ads and
 * update modules use, which this starts if no one did: once per process, never waiting, retried on each new Activity
 * and each return online until it succeeds. Its activation reloads the billing configuration; a paywall already open
 * keeps its values, the next one uses the new ones.
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
     * @param remoteConfig the instance to read if this call starts the shared session; null for the default one.
     */
    @JvmStatic
    @JvmOverloads
    public fun source(remoteConfig: FirebaseRemoteConfig? = null): BillingConfigSource = shared ?: synchronized(this) {
        shared ?: create(remoteConfig).also { shared = it }
    }

    private fun create(remoteConfig: FirebaseRemoteConfig?): BillingConfigSource {
        val session = RemoteConfigSession.startDefault(remoteConfig)
        if (session == null) {
            Log.w(TAG, "Firebase isn't initialized; paywalls use their in-code defaults.")
            return BillingConfigSource.none()
        }
        return SessionBillingConfig(session::string, session.activations)
    }
}

/** [BillingConfigSource] over the shared session: [read] answers at once, [updates] after its activation. */
internal class SessionBillingConfig(
    private val read: (String) -> String?,
    override val updates: Flow<Unit>,
) : BillingConfigSource {
    override fun raw(key: String): String? = read(key)
}

internal const val TAG: String = "AdroitBilling"
