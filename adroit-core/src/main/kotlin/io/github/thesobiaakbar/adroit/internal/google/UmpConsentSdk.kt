package io.github.thesobiaakbar.adroit.internal.google

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.provider.Settings
import io.github.thesobiaakbar.adroit.AdError
import io.github.thesobiaakbar.adroit.ConsentStatus
import io.github.thesobiaakbar.adroit.config.DebugGeography
import io.github.thesobiaakbar.adroit.internal.callbacks.OnceCallback
import io.github.thesobiaakbar.adroit.internal.logging.AdLog
import io.github.thesobiaakbar.adroit.internal.port.ConsentForm
import io.github.thesobiaakbar.adroit.internal.port.ConsentParams
import io.github.thesobiaakbar.adroit.internal.port.ConsentSdk
import io.github.thesobiaakbar.adroit.internal.port.ConsentUpdateResult
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.security.MessageDigest

/**
 * [ConsentSdk] over UMP. UMP usually calls back on the main thread; results are posted anyway through
 * [OnceCallback], so delivery is uniform and never synchronous. Every UMP call is wrapped, so an SDK exception
 * becomes an [AdError] instead of a crash.
 */
internal class UmpConsentSdk(
    private val appContext: Context,
    private val log: AdLog,
) : ConsentSdk {
    // Synchronized: warmUp() may create it on a background thread while start() needs it on the main thread.
    private val consentInformation: ConsentInformation by lazy { UserMessagingPlatform.getConsentInformation(appContext) }

    // getConsentInformation reads UMP's SharedPreferences (about 5 ms on the main thread of a debug build, reported by
    // StrictMode). Unlike the form methods, the reference doesn't restrict it to the main thread.
    override fun warmUp() {
        try {
            consentInformation
        } catch (e: Exception) {
            log.w(e) { "Loading the consent information early failed; it loads in start() instead" }
        }
    }

    override fun requestUpdate(activity: Activity, params: ConsentParams, onDone: (ConsentUpdateResult) -> Unit) {
        val done = OnceCallback(onDone)
        try {
            consentInformation.requestConsentInfoUpdate(
                activity,
                requestParameters(params),
                { done(ConsentUpdateResult.Success) },
                { error -> done(ConsentUpdateResult.Failure(GoogleErrorMapper.map(error))) },
            )
        } catch (e: Exception) {
            log.e(e) { "Requesting consent information failed" }
            done(ConsentUpdateResult.Failure(AdError.Internal("Requesting consent information failed", e)))
        }
    }

    private fun requestParameters(params: ConsentParams): ConsentRequestParameters {
        val builder = ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(params.tagUnderAgeOfConsent)
        params.consentSyncId?.let(builder::setConsentSyncId)
        if (params.debugGeography != DebugGeography.DISABLED || params.testDeviceHashedIds.isNotEmpty()) {
            val debug = ConsentDebugSettings.Builder(appContext)
                .setDebugGeography(UmpMapping.debugGeography(params.debugGeography))
            val thisDevice = if (params.includeThisDevice) thisDeviceHashedId() else null
            (params.testDeviceHashedIds + listOfNotNull(thisDevice)).distinct().forEach(debug::addTestDeviceHashedId)
            builder.setConsentDebugSettings(debug.build())
        }
        return builder.build()
    }

    /**
     * UMP's hashed ID of this device (the MD5 of its Android ID, as UMP logs it), so a debuggable build's debug
     * geography applies on a phone without copying its ID from logcat. Emulators are test devices already.
     */
    @SuppressLint("HardwareIds")
    private fun thisDeviceHashedId(): String? = try {
        Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)?.let { id ->
            MessageDigest.getInstance("MD5").digest(id.toByteArray()).joinToString("") { "%02X".format(it) }
        }
    } catch (e: Exception) {
        log.w(e) { "Reading this device's ID failed; debug geography applies on emulators and listed test devices only" }
        null
    }

    override fun loadAndShowFormIfRequired(activity: Activity, onDone: (AdError?) -> Unit) {
        val done = OnceCallback(onDone)
        try {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error -> done(error?.let(GoogleErrorMapper::map)) }
        } catch (e: Exception) {
            log.e(e) { "Showing the consent form failed" }
            done(AdError.Internal("Showing the consent form failed", e))
        }
    }

    override fun loadFormIfRequired(activity: Activity, onLoaded: (ConsentForm?, AdError?) -> Unit) {
        val done = OnceCallback<Pair<ConsentForm?, AdError?>>(callback = { (form, error) -> onLoaded(form, error) })
        if (consentStatus() != ConsentStatus.REQUIRED) {
            done(null to null)
            return
        }
        try {
            UserMessagingPlatform.loadConsentForm(
                activity,
                { form -> done(ConsentForm { showActivity, onDone -> showForm(form, showActivity, onDone) } to null) },
                { error -> done(null to GoogleErrorMapper.map(error)) },
            )
        } catch (e: Exception) {
            log.e(e) { "Loading the consent form failed" }
            done(null to AdError.Internal("Loading the consent form failed", e))
        }
    }

    private fun showForm(form: com.google.android.ump.ConsentForm, activity: Activity, onDone: (AdError?) -> Unit) {
        val done = OnceCallback(onDone)
        try {
            form.show(activity) { error -> done(error?.let(GoogleErrorMapper::map)) }
        } catch (e: Exception) {
            log.e(e) { "Showing the consent form failed" }
            done(AdError.Internal("Showing the consent form failed", e))
        }
    }

    override fun canRequestAds(): Boolean = read(false) { consentInformation.canRequestAds() }

    override fun consentStatus(): ConsentStatus =
        read(ConsentStatus.UNKNOWN) { UmpMapping.consentStatus(consentInformation.consentStatus) }

    override fun privacyOptionsRequired(): Boolean =
        read(false) { UmpMapping.privacyOptionsRequired(consentInformation.privacyOptionsRequirementStatus) }

    override fun showPrivacyOptionsForm(activity: Activity, onDone: (AdError?) -> Unit) {
        val done = OnceCallback(onDone)
        try {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { error -> done(error?.let(GoogleErrorMapper::map)) }
        } catch (e: Exception) {
            log.e(e) { "Showing the privacy options form failed" }
            done(AdError.Internal("Showing the privacy options form failed", e))
        }
    }

    override fun reset() {
        try {
            consentInformation.reset()
        } catch (e: Exception) {
            log.e(e) { "Resetting consent failed" }
        }
    }

    private inline fun <T> read(fallback: T, block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        log.e(e) { "Reading consent information failed" }
        fallback
    }
}

/** UMP's int constants and enums mapped to library types, with safe defaults for values added by newer versions. */
internal object UmpMapping {
    fun debugGeography(geography: DebugGeography): Int = when (geography) {
        DebugGeography.DISABLED -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_DISABLED
        DebugGeography.EEA -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
        DebugGeography.REGULATED_US_STATE -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_REGULATED_US_STATE
        DebugGeography.OTHER -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_OTHER
    }

    fun consentStatus(status: Int): ConsentStatus = when (status) {
        ConsentInformation.ConsentStatus.NOT_REQUIRED -> ConsentStatus.NOT_REQUIRED
        ConsentInformation.ConsentStatus.REQUIRED -> ConsentStatus.REQUIRED
        ConsentInformation.ConsentStatus.OBTAINED -> ConsentStatus.OBTAINED
        else -> ConsentStatus.UNKNOWN
    }

    // Matched by identity: a host app's R8 may rename UMP's enum constants.
    fun privacyOptionsRequired(status: ConsentInformation.PrivacyOptionsRequirementStatus): Boolean = when (status) {
        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED -> true
        else -> false
    }
}
