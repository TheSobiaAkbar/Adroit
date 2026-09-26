package com.company.admob.firebase

import com.company.admob.config.remote.AdConfigProvider
import com.company.admob.config.remote.AdOverrides
import com.company.admob.firebase.internal.FirebaseRemoteConfigSource
import com.company.admob.firebase.internal.OverridesParser
import com.company.admob.firebase.internal.RemoteConfigSource
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Remote overrides from Firebase Remote Config. Register it with `adMobConfig { remoteConfig = … }`.
 *
 * Reads these keys (all optional; an unset or empty key leaves the local configuration alone):
 * - `company_admob_enabled` (boolean): false turns every ad off.
 * - `company_admob_disabled_formats` (string): comma-separated `AdFormat` names, for example `NATIVE,APP_OPEN`.
 * - `company_admob_min_fullscreen_interval_seconds` (number): the global interval between full-screen ads.
 * - `company_admob_placements_json` (JSON): an object per placement key, with any of `enabled`,
 *   `minIntervalSeconds`, `maxPerSession`, `preload` (`AFTER_READY`, `ON_DEMAND`, `NONE`) and `adUnitId`.
 *
 * It emits the activated values when collected, then again after each real-time update, which it activates. It
 * doesn't fetch unless [autoFetch] is true: your app usually owns fetch timing (`fetchAndActivate` at startup). An
 * invalid value is logged and ignored; the other keys still apply.
 *
 * Firebase must be initialized by the app (the google-services plugin).
 *
 * @param remoteConfig the instance to read, for apps with several Firebase apps.
 * @param autoFetch whether to call `fetchAndActivate` when collection starts.
 */
public class FirebaseRemoteConfigProvider internal constructor(
    private val source: RemoteConfigSource,
    private val autoFetch: Boolean,
) : AdConfigProvider {
    public constructor(remoteConfig: FirebaseRemoteConfig, autoFetch: Boolean = false) :
        this(FirebaseRemoteConfigSource(remoteConfig), autoFetch)

    /** Uses the default `FirebaseRemoteConfig` instance, without fetching. */
    public constructor() : this(FirebaseRemoteConfig.getInstance())

    override fun overrides(): Flow<AdOverrides> = flow {
        // Firebase calls back on its own threads: a StateFlow makes the updates thread-safe and drops repeats.
        val latest = MutableStateFlow(OverridesParser.parse(source))
        val refresh = { latest.value = OverridesParser.parse(source) }
        val removeListener = source.addUpdateListener { source.activate(refresh) }
        if (autoFetch) source.fetchAndActivate(refresh)
        try {
            emitAll(latest)
        } finally {
            removeListener()
        }
    }

    override fun toString(): String = "FirebaseRemoteConfigProvider(autoFetch=$autoFetch)"
}
