package io.github.thesobiaakbar.adroit.firebase

import io.github.thesobiaakbar.adroit.AdFormat
import io.github.thesobiaakbar.adroit.AdroitInternal
import io.github.thesobiaakbar.adroit.InternalAdroitApi
import io.github.thesobiaakbar.adroit.config.remote.AdConfigProvider
import io.github.thesobiaakbar.adroit.config.remote.AdOverrides
import io.github.thesobiaakbar.adroit.firebase.internal.FirebaseRemoteConfigSource
import io.github.thesobiaakbar.adroit.firebase.internal.RemoteConfigSource
import io.github.thesobiaakbar.adroit.firebase.internal.never
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

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
 * It emits the activated values when collected, then again after each real-time update, which it activates, and after
 * the [RemoteConfigSession]'s fetch. Collecting it starts that shared session if no one did
 * ([RemoteConfigSession.startDefault]), so the app doesn't need to: it fetches once per app session and retries on
 * later screens.
 *
 * Firebase must be initialized by the app (the google-services plugin).
 *
 * @param remoteConfig the instance to read, for apps with several Firebase apps.
 * @param autoFetch ignored: the shared [RemoteConfigSession] always fetches. Kept for compatibility.
 * @param settingsKey the name of the settings parameter.
 */
@OptIn(InternalAdroitApi::class)
public class FirebaseRemoteConfigProvider internal constructor(
    private val source: RemoteConfigSource,
    private val autoFetch: Boolean,
    private val settingsKey: String,
    private val formats: () -> Map<String, AdFormat>,
    private val activations: () -> Flow<Unit>,
) : AdConfigProvider {
    /** Reads [remoteConfig]; see the class description for [autoFetch] and [settingsKey]. */
    @JvmOverloads
    public constructor(
        remoteConfig: FirebaseRemoteConfig,
        autoFetch: Boolean = false,
        settingsKey: String = DEFAULT_SETTINGS_KEY,
    ) : this(
        FirebaseRemoteConfigSource(remoteConfig),
        autoFetch,
        settingsKey,
        AdroitInternal::configuredPlacementFormats,
        { RemoteConfigSession.startDefault(remoteConfig)?.activations ?: never },
    )

    /** Uses the default `FirebaseRemoteConfig` instance and the `AdSettings` parameter. */
    public constructor() : this(FirebaseRemoteConfig.getInstance())

    override fun overrides(): Flow<AdOverrides> = flow {
        // Collected after initialize(), so the configured placements are known.
        val keys = formats()
        fun read(): AdOverrides = AdroitInternal.parseRemoteProfiles(
            keys,
            keys.keys.mapNotNull { key -> source.string(key)?.let { key to it } }.toMap(),
            source.string(settingsKey),
        )
        // Firebase calls back on its own threads: a StateFlow makes the updates thread-safe and drops repeats.
        val latest = MutableStateFlow(read())
        val removeListener = source.addUpdateListener { source.activate { latest.value = read() } }
        try {
            coroutineScope {
                // The session's activation raises no Firebase callback: re-read when it lands.
                launch { activations().collect { latest.value = read() } }
                emitAll(latest)
            }
        } finally {
            removeListener()
        }
    }

    override fun toString(): String = "FirebaseRemoteConfigProvider(autoFetch=$autoFetch, settingsKey=$settingsKey)"
}

/** The settings parameter name, as in docs/AD_PROFILES.md. */
private const val DEFAULT_SETTINGS_KEY = "AdSettings"
