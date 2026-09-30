package io.github.thesobiaakbar.adroit.internal.runtime

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.thesobiaakbar.adroit.AdsState
import io.github.thesobiaakbar.adroit.InterstitialPlacement
import io.github.thesobiaakbar.adroit.config.AdMobConfig
import io.github.thesobiaakbar.adroit.config.TestIds
import io.github.thesobiaakbar.adroit.config.adMobConfig
import io.github.thesobiaakbar.adroit.events.AdEvent
import io.github.thesobiaakbar.adroit.fakes.FakeActivityTracker
import io.github.thesobiaakbar.adroit.fakes.FakeAdsSdk
import io.github.thesobiaakbar.adroit.fakes.FakeAppForegroundSource
import io.github.thesobiaakbar.adroit.fakes.FakeNativeRenderPort
import io.github.thesobiaakbar.adroit.fakes.FakeClock
import io.github.thesobiaakbar.adroit.fakes.FakeConsentSdk
import io.github.thesobiaakbar.adroit.fakes.InMemoryKeyValueStore
import io.github.thesobiaakbar.adroit.internal.events.EventBus
import io.github.thesobiaakbar.adroit.internal.logging.AdLog
import io.github.thesobiaakbar.adroit.logging.LogLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.robolectric.Shadows.shadowOf

/** Collects every error-level log line, so tests can assert that no illegal transition was attempted. */
internal class LogCapture {
    val errors = mutableListOf<String>()

    // One instance, so configs built with it compare equal.
    private val logger = io.github.thesobiaakbar.adroit.logging.AdLogger { level, _, message, _ ->
        if (level == LogLevel.ERROR) synchronized(errors) { errors += message }
    }

    fun configure(builder: AdMobConfig.Builder) {
        builder.logging {
            level = LogLevel.VERBOSE
            logger = this@LogCapture.logger
        }
    }
}

/** A valid DEVELOPMENT configuration with one interstitial placement. */
internal fun testConfig(logs: LogCapture? = null, extra: AdMobConfig.Builder.() -> Unit = {}): AdMobConfig =
    adMobConfig {
        placements { interstitial(InterstitialPlacement("level"), adUnitId = TestIds.unit(1)) }
        logs?.configure(this)
        extra()
    }

/** Fakes for every port, driven by [scope]'s virtual time and Robolectric's main looper. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class RuntimeHarness(val scope: TestScope) {
    val app: Application = ApplicationProvider.getApplicationContext()
    val adsSdk = FakeAdsSdk(scope)
    val consentSdk = FakeConsentSdk(scope)
    val clock = FakeClock()
    val store = InMemoryKeyValueStore()
    val tracker = FakeActivityTracker()
    val foreground = FakeAppForegroundSource()
    val logs = LogCapture()
    val state = MutableStateFlow<AdsState>(AdsState.NotInitialized)
    val bus = EventBus(AdLog({ _, _, _, _ -> }, LogLevel.NONE))
    var manifestAppId: String? = TestIds.APP_ID
    var isDebuggable = true

    /** Unconfined by default, so the launch count is read during `initialize` as the tests expect. */
    var ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
    var registerCount = 0
    var depsBuilt = 0

    fun deps(): RuntimeDeps {
        depsBuilt++
        return RuntimeDeps(
            adsSdk, consentSdk, tracker, foreground, clock, store, FakeNativeRenderPort(), manifestAppId, isDebuggable,
            ioDispatcher = ioDispatcher,
            onRegister = { registerCount++ },
        )
    }

    fun runtime(config: AdMobConfig = testConfig(logs)): AdsRuntime =
        AdsRuntime(app, config, state, bus) { _, _ -> deps() }

    /** Runs virtual time and the main looper until both are idle. */
    fun settle() {
        repeat(5) {
            shadowOf(Looper.getMainLooper()).idle()
            scope.advanceUntilIdle()
        }
    }

    fun stateChanges(): List<AdsState> = bus.recent().filterIsInstance<AdEvent.StateChanged>().map { it.state }
}
