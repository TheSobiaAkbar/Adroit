package com.company.admob.internal.google

import android.app.Activity
import android.content.Context
import com.company.admob.AdError
import com.company.admob.ConsentStatus
import com.company.admob.config.DebugGeography
import com.company.admob.internal.callbacks.OnceCallback
import com.company.admob.internal.logging.AdLog
import com.company.admob.internal.port.ConsentParams
import com.company.admob.internal.port.ConsentSdk
import com.company.admob.internal.port.ConsentUpdateResult
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * [ConsentSdk] over UMP. UMP usually calls back on the main thread; results are posted anyway through
 * [OnceCallback], so delivery is uniform and never synchronous. Every UMP call is wrapped, so an SDK exception
 * becomes an [AdError] instead of a crash.
 */
internal class UmpConsentSdk(
    private val appContext: Context,
    private val log: AdLog,
) : ConsentSdk {
    private val consentInformation: ConsentInformation by lazy { UserMessagingPlatform.getConsentInformation(appContext) }

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
            params.testDeviceHashedIds.forEach(debug::addTestDeviceHashedId)
            builder.setConsentDebugSettings(debug.build())
        }
        return builder.build()
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
