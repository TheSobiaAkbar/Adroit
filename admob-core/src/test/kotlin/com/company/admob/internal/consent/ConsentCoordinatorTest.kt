package com.company.admob.internal.consent

import android.app.Activity
import android.os.Looper
import com.company.admob.AdError
import com.company.admob.ConsentState
import com.company.admob.ConsentStatus
import com.company.admob.config.AdEnvironment
import com.company.admob.config.AdMobConfig
import com.company.admob.config.AgeTreatment
import com.company.admob.config.DebugGeography
import com.company.admob.config.adMobConfig
import com.company.admob.events.AdEvent
import com.company.admob.fakes.FakeClock
import com.company.admob.fakes.FakeConsentSdk
import com.company.admob.internal.config.EffectiveConfig
import com.company.admob.internal.events.EventBus
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.port.ConsentUpdateResult
import com.company.admob.logging.LogLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.lang.ref.WeakReference

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ConsentCoordinatorTest {
    private val networkError = AdError.NetworkError()

    /** Wires a coordinator to [sdk]. Every hook asserts it only runs while UMP allows ads. */
    private inner class Harness(
        val sdk: FakeConsentSdk,
        config: AdMobConfig = adMobConfig { },
    ) {
        val order = mutableListOf<String>()
        var initCalls = 0
        var revokeCalls = 0
        val errors = mutableListOf<String>()
        val events = EventBus(AdLog({ _, _, _, _ -> }, LogLevel.NONE))
        val log = AdLog({ level, _, message, _ -> if (level == LogLevel.ERROR) errors += message }, LogLevel.VERBOSE)
        val coordinator = ConsentCoordinator(
            consentSdk = sdk,
            config = EffectiveConfig.from(config),
            log = log,
            events = events,
            clock = FakeClock(),
            onCanRequestAds = {
                if (!sdk.canRequestAds) fail("onCanRequestAds fired while canRequestAds() is false")
                initCalls++
                order += "init"
            },
            onConsentRevoked = {
                if (sdk.canRequestAds) fail("onConsentRevoked fired while canRequestAds() is true")
                revokeCalls++
            },
        )
        val results = mutableListOf<ConsentState>()

        fun gather(activity: Activity = Activity()) = coordinator.gather(activity) { results += it }
    }

    private fun TestScope.harness(
        canRequestAds: Boolean,
        status: ConsentStatus,
        config: AdMobConfig = adMobConfig { },
        setup: FakeConsentSdk.() -> Unit = {},
    ): Harness {
        val sdk = FakeConsentSdk(this).apply {
            this.canRequestAds = canRequestAds
            this.status = status
            setup()
        }
        return Harness(sdk, config).also { h -> sdk.onFormShown = sdk.onFormShown ?: { h.order += "form" } }
    }

    private fun FakeConsentSdk.grantOnForm() {
        onFormShown = { canRequestAds = true; status = ConsentStatus.OBTAINED }
    }

    @Test
    fun `first launch in the EEA shows the form, obtains consent, and initializes once`() = runTest {
        val h = harness(canRequestAds = false, status = ConsentStatus.REQUIRED) { grantOnForm() }

        h.gather()
        advanceUntilIdle()

        assertEquals(1, h.sdk.updateRequests.size)
        assertEquals(1, h.sdk.formShowCount)
        assertEquals(1, h.initCalls)
        assertEquals(listOf(ConsentState(ConsentStatus.OBTAINED, true, false, null)), h.results)
        assertEquals(h.results.single(), h.coordinator.state.value)
        assertFalse(h.coordinator.isGathering)
    }

    @Test
    fun `consent not required - the form step returns immediately and init runs`() = runTest {
        val h = harness(canRequestAds = true, status = ConsentStatus.NOT_REQUIRED)

        h.gather()
        advanceUntilIdle()

        assertEquals(1, h.sdk.formShowCount)
        assertEquals(1, h.initCalls)
        assertEquals(ConsentStatus.NOT_REQUIRED, h.results.single().status)
        assertTrue(h.results.single().canRequestAds)
    }

    @Test
    fun `returning user - init starts before the form and isn't requested again after it`() = runTest {
        val h = harness(canRequestAds = true, status = ConsentStatus.OBTAINED)

        h.gather()
        advanceUntilIdle()

        assertEquals(listOf("init", "form"), h.order)
        assertEquals(1, h.initCalls)
        assertEquals(1, h.results.size)
    }

    @Test
    fun `declined or closed without consent - no init and ads unavailable`() = runTest {
        for (status in listOf(ConsentStatus.REQUIRED, ConsentStatus.UNKNOWN)) {
            val h = harness(canRequestAds = false, status = status)

            h.gather()
            advanceUntilIdle()

            assertEquals(1, h.sdk.formShowCount)
            assertEquals(0, h.initCalls)
            val result = h.results.single()
            assertEquals(status, result.status)
            assertFalse("the init coordinator reads this as Unavailable(CONSENT_NOT_OBTAINED)", result.canRequestAds)
        }
    }

    @Test
    fun `update error with previous-session consent still initializes, without a form`() = runTest {
        val h = harness(canRequestAds = true, status = ConsentStatus.OBTAINED) {
            updateResult = ConsentUpdateResult.Failure(networkError)
        }

        h.gather()
        advanceUntilIdle()

        assertEquals(0, h.sdk.formShowCount)
        assertEquals(1, h.initCalls)
        assertEquals(networkError, h.results.single().lastError)
        assertTrue(h.results.single().canRequestAds)
    }

    @Test
    fun `update error without previous consent - no init and lastError is set`() = runTest {
        val h = harness(canRequestAds = false, status = ConsentStatus.UNKNOWN) {
            updateResult = ConsentUpdateResult.Failure(networkError)
        }

        h.gather()
        advanceUntilIdle()

        assertEquals(0, h.sdk.formShowCount)
        assertEquals(0, h.initCalls)
        assertEquals(networkError, h.coordinator.state.value.lastError)
        assertFalse(h.coordinator.state.value.canRequestAds)
    }

    @Test
    fun `form error re-checks canRequestAds`() = runTest {
        val formError = AdError.Timeout()
        val granted = harness(canRequestAds = false, status = ConsentStatus.REQUIRED) {
            formResult = formError
            grantOnForm()
        }
        granted.gather()
        advanceUntilIdle()
        assertEquals(1, granted.initCalls)
        assertEquals(formError, granted.results.single().lastError)

        val denied = harness(canRequestAds = false, status = ConsentStatus.REQUIRED) { formResult = formError }
        denied.gather()
        advanceUntilIdle()
        assertEquals(0, denied.initCalls)
        assertEquals(formError, denied.results.single().lastError)
    }

    @Test
    fun `rotation during the update attaches to the in-flight gather and uses the new activity for the form`() =
        runTest {
            val h = harness(canRequestAds = false, status = ConsentStatus.REQUIRED) {
                updateDelayMs = 1_000
                grantOnForm()
            }
            val first = Robolectric.buildActivity(Activity::class.java).setup()
            val second = Robolectric.buildActivity(Activity::class.java).setup()
            val results = mutableListOf<String>()

            h.coordinator.gather(first.get()) { results += "first" }
            advanceTimeBy(500)
            first.pause().stop().destroy()
            h.coordinator.gather(second.get()) { results += "second" }
            advanceUntilIdle()

            assertEquals(1, h.sdk.updateRequests.size)
            assertEquals(1, h.sdk.formShowCount)
            assertSame(second.get(), h.sdk.formActivity?.get())
            assertEquals(listOf("first", "second"), results)
            assertEquals(1, h.initCalls)
        }

    @Test
    fun `rotation during the form attaches, both callbacks fire once, and UMP is called once`() = runTest {
        lateinit var h: Harness
        var attached = false
        h = harness(canRequestAds = false, status = ConsentStatus.REQUIRED) {
            onFormShown = {
                if (!attached) {
                    attached = true
                    h.gather()
                }
                canRequestAds = true
                status = ConsentStatus.OBTAINED
            }
        }

        h.gather()
        advanceUntilIdle()

        assertTrue(attached)
        assertEquals(1, h.sdk.updateRequests.size)
        assertEquals(1, h.sdk.formShowCount)
        assertEquals(2, h.results.size)
        assertEquals(1, h.initCalls)
    }

    @Test
    fun `a later gather runs again, since consent is requested on every launch`() = runTest {
        val h = harness(canRequestAds = true, status = ConsentStatus.NOT_REQUIRED)

        h.gather()
        advanceUntilIdle()
        h.gather()
        advanceUntilIdle()

        assertEquals(2, h.sdk.updateRequests.size)
        assertEquals(2, h.results.size)
        assertEquals("once per gather; the init coordinator dedupes across gathers", 2, h.initCalls)
    }

    @Test
    fun `the activity isn't retained after the gather finishes`() = runTest {
        val h = harness(canRequestAds = false, status = ConsentStatus.REQUIRED) { grantOnForm() }
        val activityRef = gatherWithThrowawayActivity(h)

        assertTrue(h.coordinator.holdsActivity)
        advanceUntilIdle()

        assertFalse(h.coordinator.holdsActivity)
        repeat(10) {
            if (activityRef.get() == null) return@runTest
            System.gc()
            Thread.sleep(10)
        }
        assertNull("the Activity leaked", activityRef.get())
    }

    // A separate function, so no local variable keeps the Activity reachable.
    private fun gatherWithThrowawayActivity(h: Harness): WeakReference<Activity> {
        val activity = Activity()
        h.gather(activity)
        return WeakReference(activity)
    }

    @Test
    fun `privacy options not required - no form, callback with null`() = runTest {
        val h = harness(canRequestAds = true, status = ConsentStatus.NOT_REQUIRED)
        h.gather()
        advanceUntilIdle()
        val dismissed = mutableListOf<AdError?>()

        h.coordinator.showPrivacyOptions(Activity()) { dismissed += it }
        assertTrue("never synchronous", dismissed.isEmpty())
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf<AdError?>(null), dismissed)
        assertEquals(0, h.sdk.privacyOptionsFormShowCount)
        assertEquals(1, h.initCalls)
        assertEquals(0, h.revokeCalls)
    }

    @Test
    fun `privacy options withdraw consent - revoked and ConsentUpdated emitted`() = runTest {
        val h = harness(canRequestAds = true, status = ConsentStatus.OBTAINED) {
            privacyOptionsRequired = true
            onPrivacyOptionsFormShown = { canRequestAds = false; status = ConsentStatus.REQUIRED }
        }
        h.gather()
        advanceUntilIdle()
        assertTrue(h.coordinator.privacyOptionsRequired.value)
        val eventsBefore = h.events.recent().size
        val dismissed = mutableListOf<AdError?>()

        h.coordinator.showPrivacyOptions(Activity()) { dismissed += it }
        advanceUntilIdle()

        assertEquals(listOf<AdError?>(null), dismissed)
        assertEquals(1, h.revokeCalls)
        assertEquals(1, h.initCalls)
        assertFalse(h.coordinator.state.value.canRequestAds)
        val event = h.events.recent().drop(eventsBefore).single() as AdEvent.ConsentUpdated
        assertEquals(h.coordinator.state.value, event.consent)
    }

    @Test
    fun `privacy options grant consent later - init requested`() = runTest {
        val h = harness(canRequestAds = false, status = ConsentStatus.REQUIRED) {
            privacyOptionsRequired = true
            onPrivacyOptionsFormShown = { canRequestAds = true; status = ConsentStatus.OBTAINED }
        }
        h.gather()
        advanceUntilIdle()
        assertEquals(0, h.initCalls)

        h.coordinator.showPrivacyOptions(Activity()) { }
        advanceUntilIdle()

        assertEquals(1, h.initCalls)
        assertEquals(0, h.revokeCalls)
        assertTrue(h.coordinator.state.value.canRequestAds)
    }

    @Test
    fun `privacy options unchanged - neither init nor revoke`() = runTest {
        val h = harness(canRequestAds = true, status = ConsentStatus.OBTAINED) { privacyOptionsRequired = true }
        h.gather()
        advanceUntilIdle()

        h.coordinator.showPrivacyOptions(Activity()) { }
        advanceUntilIdle()

        assertEquals(1, h.initCalls)
        assertEquals(0, h.revokeCalls)
    }

    @Test
    fun `consent params - PRODUCTION never sends debug geography or test devices`() {
        val production = EffectiveConfig.from(
            adMobConfig {
                environment = AdEnvironment.PRODUCTION
                testing { testDeviceIds += "33BE2250B43518CCDA7DE426D04EE231" }
                privacy { debugGeography = DebugGeography.EEA }
            },
        )
        val params = ConsentCoordinator.consentParams(production)
        assertEquals(DebugGeography.DISABLED, params.debugGeography)
        assertTrue(params.testDeviceHashedIds.isEmpty())

        val staging = EffectiveConfig.from(
            adMobConfig {
                environment = AdEnvironment.STAGING
                testing { testDeviceIds += "33BE2250B43518CCDA7DE426D04EE231" }
                privacy { debugGeography = DebugGeography.REGULATED_US_STATE }
            },
        )
        val stagingParams = ConsentCoordinator.consentParams(staging)
        assertEquals(DebugGeography.REGULATED_US_STATE, stagingParams.debugGeography)
        assertEquals(listOf("33BE2250B43518CCDA7DE426D04EE231"), stagingParams.testDeviceHashedIds)
    }

    @Test
    fun `consent params - under-age tag only for CHILD, and the sync ID passes through`() {
        fun params(treatment: AgeTreatment, syncId: String? = null) = ConsentCoordinator.consentParams(
            EffectiveConfig.from(
                adMobConfig {
                    privacy {
                        ageTreatment = treatment
                        consentSyncId = syncId
                    }
                },
            ),
        )
        assertTrue(params(AgeTreatment.CHILD).tagUnderAgeOfConsent)
        assertFalse(params(AgeTreatment.TEEN).tagUnderAgeOfConsent)
        assertFalse(params(AgeTreatment.UNSPECIFIED).tagUnderAgeOfConsent)
        assertEquals("user-123", params(AgeTreatment.UNSPECIFIED, "user-123").consentSyncId)
        assertNull("blank is ignored", params(AgeTreatment.UNSPECIFIED, "  ").consentSyncId)
    }

    @Test
    fun `resetConsentForTesting is a no-op in PRODUCTION`() = runTest {
        val h = harness(canRequestAds = true, status = ConsentStatus.OBTAINED)
        PrivacyApiImpl(h.coordinator, AdEnvironment.PRODUCTION, h.log).resetConsentForTesting()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, h.sdk.resetCount)
        assertEquals(1, h.errors.size)

        PrivacyApiImpl(h.coordinator, AdEnvironment.DEVELOPMENT, h.log).resetConsentForTesting()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, h.sdk.resetCount)
    }

    @Test
    fun `PrivacyApi exposes the coordinator's state`() = runTest {
        val h = harness(canRequestAds = true, status = ConsentStatus.OBTAINED) { privacyOptionsRequired = true }
        val api = PrivacyApiImpl(h.coordinator, AdEnvironment.DEVELOPMENT, h.log)
        assertEquals(ConsentState.Unknown, api.consentState.value)

        h.gather()
        advanceUntilIdle()

        assertEquals(ConsentState(ConsentStatus.OBTAINED, true, true, null), api.consentState.value)
        assertTrue(api.isPrivacyOptionsRequired.value)
    }
}
