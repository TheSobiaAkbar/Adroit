package com.company.admob.internal.runtime

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.company.admob.AdsState
import com.company.admob.InterstitialPlacement
import com.company.admob.config.AdMobConfig
import com.company.admob.config.TestIds
import com.company.admob.config.adMobConfig
import com.company.admob.events.AdEvent
import com.company.admob.fakes.FakeActivityTracker
import com.company.admob.fakes.FakeAdsSdk
import com.company.admob.fakes.FakeAppForegroundSource
import com.company.admob.fakes.FakeClock
import com.company.admob.fakes.FakeConsentSdk
import com.company.admob.fakes.InMemoryKeyValueStore
import com.company.admob.internal.events.EventBus
import com.company.admob.internal.logging.AdLog
import com.company.admob.logging.LogLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.robolectric.Shadows.shadowOf

/** Collects every error-level log line, so tests can assert that no illegal transition was attempted. */
internal class LogCapture {
    val errors = mutableListOf<String>()

    // One instance, so configs built with it compare equal.
    private val logger = com.company.admob.logging.AdLogger { level, _, message, _ ->
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
    var registerCount = 0
    var depsBuilt = 0

    fun deps(): RuntimeDeps {
        depsBuilt++
        return RuntimeDeps(
            adsSdk, consentSdk, tracker, foreground, clock, store, manifestAppId, isDebuggable,
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
