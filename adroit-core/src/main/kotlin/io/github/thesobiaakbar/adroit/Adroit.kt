package io.github.thesobiaakbar.adroit

import android.app.Activity
import android.app.Application
import androidx.annotation.VisibleForTesting
import io.github.thesobiaakbar.adroit.config.AdMobConfig
import io.github.thesobiaakbar.adroit.events.AdEvent
import io.github.thesobiaakbar.adroit.internal.UninitializedAdsClient
import io.github.thesobiaakbar.adroit.internal.events.EventBus
import io.github.thesobiaakbar.adroit.internal.logging.AdLog
import io.github.thesobiaakbar.adroit.internal.runtime.AdsRuntime
import io.github.thesobiaakbar.adroit.internal.runtime.RuntimeDeps
import kotlinx.coroutines.CoroutineScope
import io.github.thesobiaakbar.adroit.logging.AndroidAdLogger
import io.github.thesobiaakbar.adroit.logging.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration

/**
 * The process-wide entry point to Adroit.
 *
 * Call `initialize()` in `Application.onCreate()`, then [start] from your first Activity. Everything else is
 * described on [AdsClient]. For dependency injection, bind [AdsClient] to [client].
 *
 * [state] and [events] are the same instances before and after initialization, so they can be collected at any
 * time. Other objects obtained before initialization (for example availability flows) stay inert, so initialize
 * before using the rest of the API.
 */
public object Adroit : AdsClient {
    private val bootstrapLog = AdLog(AndroidAdLogger(), LogLevel.WARN)

    internal val sharedState: MutableStateFlow<AdsState> = MutableStateFlow(AdsState.NotInitialized)

    internal val eventBus: EventBus = EventBus(bootstrapLog)

    /** The implementation behind the facade. Replaced once by `initialize()`. */
    @Volatile
    internal var delegate: AdsClient =
        UninitializedAdsClient(sharedState.asStateFlow(), eventBus.events, bootstrapLog)

    /** The last value passed to [setAdFree], so an early call is applied at initialization. */
    @Volatile
    internal var requestedAdFree: Boolean? = null

    private val lock = Any()

    /**
     * A test double installed by [installForTesting] (adroit-testing's `AdroitTestRule`). While set, the whole
     * facade, [state] and [events] included, is that client.
     */
    @Volatile
    private var testClient: AdsClient? = null

    /** The object graph built by `initialize()`, or null before it. */
    @Volatile
    internal var runtime: AdsRuntime? = null
        private set

    /** [runtime] unless a test client stands in for it (adroit-testing), in which case callers use [delegate]. */
    internal val liveRuntime: AdsRuntime? get() = if (testClient == null) runtime else null

    /**
     * Sets up the library. Call it once, in `Application.onCreate()`. Cheap and synchronous: it validates [config]
     * and attaches lifecycle observers, with no network and no Google SDK calls. Consent and SDK initialization
     * happen in [start]. Callable from any thread.
     *
     * Later calls are ignored: with an equal configuration silently, with a different one with an error log and a
     * [AdEvent.ConfigurationWarning]; the first configuration is kept.
     *
     * An invalid configuration throws [IllegalStateException] listing every problem when `failFast` is in effect
     * (the default outside PRODUCTION). Otherwise [state] becomes [AdsState.Failed] with
     * [AdError.InvalidConfiguration], ads stay off, and the app keeps running.
     */
    @JvmStatic
    public fun initialize(application: Application, config: AdMobConfig) {
        initialize(application, config) { log, scope -> RuntimeDeps.production(application, log, scope) }
    }

    internal fun initialize(
        application: Application,
        config: AdMobConfig,
        depsFactory: (AdLog, CoroutineScope) -> RuntimeDeps,
    ) {
        synchronized(lock) {
            runtime?.let {
                it.onInitializeAgain(config)
                return
            }
            val created = AdsRuntime(application, config, sharedState, eventBus, depsFactory)
            runtime = created
            delegate = created.client
        }
        // setAdFree() before initialize() was remembered; apply it now. Reading after publishing the delegate means
        // a concurrent setAdFree() is applied by one side or the other.
        requestedAdFree?.let { delegate.setAdFree(it) }
    }

    /** Writes to the configured log once initialized, to the bootstrap log (warnings and errors) before. */
    internal fun log(level: LogLevel, message: String) {
        val log = runtime?.log ?: bootstrapLog
        if (log.isEnabled(level)) log.write(level, message, null)
    }

    /** Each configured placement's format, by key; empty before `initialize`. */
    internal fun configuredPlacementFormats(): Map<String, AdFormat> =
        runtime?.config?.placements?.associate { it.placement.key to it.format }.orEmpty()

    /** Returns the facade to its uninitialized state, for tests that initialize it. Not thread-safe. */
    @VisibleForTesting
    internal fun resetForTests() {
        synchronized(lock) {
            runtime?.close()
            runtime = null
            testClient = null
            requestedAdFree = null
            sharedState.value = AdsState.NotInitialized
            delegate = UninitializedAdsClient(sharedState.asStateFlow(), eventBus.events, bootstrapLog)
        }
    }

    /**
     * Makes the facade [client] until the returned function is called, which restores what was there before
     * (including an earlier installation). For adroit-testing only (ADR-012).
     */
    internal fun installForTesting(client: AdsClient): () -> Unit = synchronized(lock) {
        val previousDelegate = delegate
        val previousTestClient = testClient
        val previousAdFree = requestedAdFree
        delegate = client
        testClient = client
        return {
            synchronized(lock) {
                delegate = previousDelegate
                testClient = previousTestClient
                requestedAdFree = previousAdFree
            }
        }
    }

    /** This facade as an [AdsClient], for dependency injection. Stable across initialization. */
    @JvmStatic
    public val client: AdsClient
        get() = this

    private val publicState: StateFlow<AdsState> = sharedState.asStateFlow()

    override val state: StateFlow<AdsState> get() = testClient?.state ?: publicState

    override val isReady: Boolean get() = state.value == AdsState.Ready

    override fun start(activity: Activity, onComplete: ((AdsState) -> Unit)?): Unit =
        delegate.start(activity, onComplete)

    override suspend fun awaitReady(timeout: Duration): Boolean = delegate.awaitReady(timeout)

    override val privacy: PrivacyApi get() = delegate.privacy

    override val interstitial: InterstitialApi get() = delegate.interstitial

    override val rewarded: RewardedApi get() = delegate.rewarded

    override val rewardedInterstitial: RewardedInterstitialApi get() = delegate.rewardedInterstitial

    override val appOpen: AppOpenApi get() = delegate.appOpen

    override val banner: BannerApi get() = delegate.banner

    override val native: NativeApi get() = delegate.native

    override val diagnostics: DiagnosticsApi get() = delegate.diagnostics

    override val events: SharedFlow<AdEvent> get() = testClient?.events ?: eventBus.events

    override fun setAdFree(adFree: Boolean) {
        requestedAdFree = adFree
        delegate.setAdFree(adFree)
    }

    /**
     * The last value passed to [setAdFree] (before or after [initialize]); false when it was never called. The one
     * premium source for the library: `SplashFlow` uses it by default. In an app with Adroit Billing, the bridge
     * `AdroitBilling.entitlements.collect { Adroit.setAdFree(it.adFree) }` in `Application.onCreate` keeps it current.
     * Any thread.
     */
    @JvmStatic
    public val isAdFree: Boolean
        get() = requestedAdFree == true

    override fun showInterstitial(
        activity: Activity,
        placement: InterstitialPlacement,
        onResult: (FullScreenResult) -> Unit,
    ): Unit = delegate.showInterstitial(activity, placement, onResult)

    override fun showRewarded(
        activity: Activity,
        placement: RewardedPlacement,
        onResult: (RewardResult) -> Unit,
    ): Unit = delegate.showRewarded(activity, placement, onResult)

    override fun preload(vararg placements: AdPlacement): Unit = delegate.preload(*placements)
}
