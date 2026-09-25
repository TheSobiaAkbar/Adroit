package com.company.admob.internal.runtime

import android.app.Application
import com.company.admob.AdError
import com.company.admob.AdsClient
import com.company.admob.AdsState
import com.company.admob.config.AdEnvironment
import com.company.admob.config.AdMobConfig
import com.company.admob.events.AdEvent
import com.company.admob.internal.AdFreeController
import com.company.admob.internal.config.ConfigIssueCode
import com.company.admob.internal.config.ConfigValidator
import com.company.admob.internal.config.EffectiveConfig
import com.company.admob.internal.consent.PrivacyApiImpl
import com.company.admob.internal.consent.RdpWriter
import com.company.admob.internal.events.EventBus
import com.company.admob.internal.init.InitCoordinator
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.storage.SharedPrefsKeyValueStore
import com.company.admob.internal.threading.libraryScope
import com.company.admob.internal.threading.runOnMain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The composition root (architecture §9): builds the object graph by hand from [config] and the ports made by
 * [depsFactory]. Construction is Phase A of initialization (§11): cheap, no network, no Google calls.
 *
 * [state] and [events] belong to the facade, so they stay the same instances before and after initialization.
 *
 * @throws IllegalStateException if [config] is invalid and `failFast` is in effect.
 */
internal class AdsRuntime(
    app: Application,
    val config: AdMobConfig,
    state: MutableStateFlow<AdsState>,
    private val events: EventBus,
    depsFactory: (AdLog, CoroutineScope) -> RuntimeDeps,
) {
    val effectiveConfig: EffectiveConfig = EffectiveConfig.from(config)
    val log: AdLog = AdLog(config.logging.logger, effectiveConfig.logLevel)
    private val scope: CoroutineScope = libraryScope(log)
    val deps: RuntimeDeps = depsFactory(log, scope)
    private val validation = ConfigValidator.validate(config, deps.manifestAppId, deps.isDebuggable)

    init {
        if (!validation.isValid && effectiveConfig.failFast) {
            scope.cancel()
            throw IllegalStateException(
                "Invalid CompanyAdMob configuration:\n" + validation.errors.joinToString("\n") { "- ${it.message}" },
            )
        }
    }

    val adFree: AdFreeController = AdFreeController(log)

    val init: InitCoordinator = InitCoordinator(
        mutableState = state,
        appContext = app.applicationContext,
        appId = config.appId?.value ?: deps.manifestAppId,
        config = effectiveConfig,
        adsSdk = deps.adsSdk,
        consentSdk = deps.consentSdk,
        rdpWriter = RdpWriter(app.applicationContext, log),
        adFree = adFree,
        clock = deps.clock,
        events = events,
        log = log,
    )

    val client: AdsClient = AdsClientImpl(
        state = init.state,
        eventBus = events,
        init = init,
        privacy = PrivacyApiImpl(init.consent, config.environment, log),
        runtime = this,
    )

    /** This process's launch number, counting from 1; 0 when the configuration is invalid. */
    var launchCount: Int = 0
        private set

    init {
        events.setListeners(config.eventListeners)
        events.recordRecent = config.environment != AdEnvironment.PRODUCTION
        for (warning in validation.warnings) {
            if (warning.code == ConfigIssueCode.NON_PRODUCTION_IN_RELEASE_BUILD) {
                log.e { warning.message }
            } else {
                log.w { warning.message }
            }
            events.emit(AdEvent.ConfigurationWarning(warning.message, deps.clock.currentTimeMillis()))
        }
        if (validation.isValid) {
            deps.register()
            val store = deps.keyValueStore
            launchCount = store.getInt(SharedPrefsKeyValueStore.KEY_LAUNCH_COUNT, 0) + 1
            store.putInt(SharedPrefsKeyValueStore.KEY_LAUNCH_COUNT, launchCount)
            // TODO(PROMPT-15): collect config.remoteConfig overrides in the library scope.
            runOnMain { init.onConfigValid() }
        } else {
            val reason = validation.errors.joinToString("; ") { it.message }
            log.e { "Invalid configuration; ads stay off. $reason" }
            runOnMain { init.onConfigInvalid(AdError.InvalidConfiguration(reason)) }
        }
    }

    /** `initialize()` was called again. The first configuration is kept. */
    fun onInitializeAgain(other: AdMobConfig) {
        if (other == config) {
            log.d { "initialize() called again with the same configuration; ignored" }
            return
        }
        val message = "initialize() called again with a different configuration; the first one is kept"
        log.e { message }
        events.emit(AdEvent.ConfigurationWarning(message, deps.clock.currentTimeMillis()))
    }

    /** Tears down for tests: cancels the library scope and detaches the observers. */
    fun close() {
        scope.cancel()
        deps.unregister()
        events.setListeners(emptyList())
        events.recordRecent = true
    }
}
