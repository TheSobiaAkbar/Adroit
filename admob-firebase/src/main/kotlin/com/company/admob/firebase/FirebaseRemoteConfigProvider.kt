package com.company.admob.firebase

import com.company.admob.AdFormat
import com.company.admob.CompanyAdMobInternal
import com.company.admob.InternalCompanyAdMobApi
import com.company.admob.config.remote.AdConfigProvider
import com.company.admob.config.remote.AdOverrides
import com.company.admob.firebase.internal.FirebaseRemoteConfigSource
import com.company.admob.firebase.internal.RemoteConfigSource
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Remote overrides from Firebase Remote Config, one parameter per ad (docs/AD_PROFILES.md). Register it with
 * `adMobConfig { remoteConfig = … }`.
 *
 * For every configured placement (for example the ads in your `ads_config.json`), it reads the Remote Config
 * parameter named like the placement key, for example `HistoryAd`, whose value is that ad's JSON: `enabled`,
 * `adUnitIds`, `counter`, `timerSeconds`, `maxPerSession`, `style` and so on. It also reads the settings parameter
 * ([settingsKey], `AdSettings` by default): `adsEnabled`, `maxInterstitialsPerSession` and
 * `minSecondsBetweenFullScreenAds`. An unset or empty parameter leaves that ad as configured, and a field left out of
 * a value keeps its configured value. An invalid value or field is logged and ignored; the rest still applies.
 *
 * It emits the activated values when collected, then again after each real-time update, which it activates. It
 * doesn't fetch unless [autoFetch] is true: your app usually owns fetch timing (`fetchAndActivate` at startup).
 *
 * Firebase must be initialized by the app (the google-services plugin).
 *
 * @param remoteConfig the instance to read, for apps with several Firebase apps.
 * @param autoFetch whether to call `fetchAndActivate` when collection starts.
 * @param settingsKey the name of the settings parameter.
 */
@OptIn(InternalCompanyAdMobApi::class)
public class FirebaseRemoteConfigProvider internal constructor(
    private val source: RemoteConfigSource,
    private val autoFetch: Boolean,
    private val settingsKey: String,
    private val formats: () -> Map<String, AdFormat>,
) : AdConfigProvider {
    public constructor(
        remoteConfig: FirebaseRemoteConfig,
        autoFetch: Boolean = false,
        settingsKey: String = DEFAULT_SETTINGS_KEY,
    ) : this(FirebaseRemoteConfigSource(remoteConfig), autoFetch, settingsKey, CompanyAdMobInternal::configuredPlacementFormats)

    /** Uses the default `FirebaseRemoteConfig` instance, without fetching, and the `AdSettings` parameter. */
    public constructor() : this(FirebaseRemoteConfig.getInstance())

    override fun overrides(): Flow<AdOverrides> = flow {
        // Collected after initialize(), so the configured placements are known.
        val keys = formats()
        fun read(): AdOverrides = CompanyAdMobInternal.parseRemoteProfiles(
            keys,
            keys.keys.mapNotNull { key -> source.string(key)?.let { key to it } }.toMap(),
            source.string(settingsKey),
        )
        // Firebase calls back on its own threads: a StateFlow makes the updates thread-safe and drops repeats.
        val latest = MutableStateFlow(read())
        val refresh = { latest.value = read() }
        val removeListener = source.addUpdateListener { source.activate(refresh) }
        if (autoFetch) source.fetchAndActivate(refresh)
        try {
            emitAll(latest)
        } finally {
            removeListener()
        }
    }

    override fun toString(): String = "FirebaseRemoteConfigProvider(autoFetch=$autoFetch, settingsKey=$settingsKey)"
}

/** The settings parameter name, as in docs/AD_PROFILES.md. */
private const val DEFAULT_SETTINGS_KEY = "AdSettings"
