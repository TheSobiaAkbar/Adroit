package com.company.admob.internal.google

import com.company.admob.ConsentStatus
import com.company.admob.config.AgeTreatment
import com.company.admob.config.DebugGeography
import com.company.admob.config.MaxAdContentRating
import com.google.android.libraries.ads.mobile.sdk.common.AgeRestrictedTreatment
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Request configuration and UMP value mappings. */
class GoogleEnumMappingTest {
    @Test
    fun `age treatment maps to the same-named Google value`() {
        AgeTreatment.entries.forEach {
            assertEquals(AgeRestrictedTreatment.values().single { g -> g.name == it.name }, GoogleRequestConfigMapper.map(it))
        }
    }

    @Test
    fun `max ad content rating maps, and unset is unspecified`() {
        MaxAdContentRating.entries.forEach {
            assertEquals(
                RequestConfiguration.MaxAdContentRating.values().single { g -> g.name == "MAX_AD_CONTENT_RATING_${it.name}" },
                GoogleRequestConfigMapper.map(it),
            )
        }
        assertEquals(
            RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_UNSPECIFIED,
            GoogleRequestConfigMapper.map(null as MaxAdContentRating?),
        )
    }

    @Test
    fun `debug geography maps to UMP constants without the deprecated NOT_EEA`() {
        assertEquals(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_DISABLED, UmpMapping.debugGeography(DebugGeography.DISABLED))
        assertEquals(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA, UmpMapping.debugGeography(DebugGeography.EEA))
        assertEquals(
            ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_REGULATED_US_STATE,
            UmpMapping.debugGeography(DebugGeography.REGULATED_US_STATE),
        )
        assertEquals(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_OTHER, UmpMapping.debugGeography(DebugGeography.OTHER))
    }

    @Test
    fun `consent status maps, and unknown values are UNKNOWN`() {
        assertEquals(ConsentStatus.UNKNOWN, UmpMapping.consentStatus(ConsentInformation.ConsentStatus.UNKNOWN))
        assertEquals(ConsentStatus.NOT_REQUIRED, UmpMapping.consentStatus(ConsentInformation.ConsentStatus.NOT_REQUIRED))
        assertEquals(ConsentStatus.REQUIRED, UmpMapping.consentStatus(ConsentInformation.ConsentStatus.REQUIRED))
        assertEquals(ConsentStatus.OBTAINED, UmpMapping.consentStatus(ConsentInformation.ConsentStatus.OBTAINED))
        assertEquals(ConsentStatus.UNKNOWN, UmpMapping.consentStatus(42))
    }

    @Test
    fun `privacy options are required only for REQUIRED`() {
        assertTrue(UmpMapping.privacyOptionsRequired(ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED))
        assertFalse(UmpMapping.privacyOptionsRequired(ConsentInformation.PrivacyOptionsRequirementStatus.NOT_REQUIRED))
        assertFalse(UmpMapping.privacyOptionsRequired(ConsentInformation.PrivacyOptionsRequirementStatus.UNKNOWN))
    }
}
