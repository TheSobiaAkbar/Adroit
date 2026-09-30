package io.github.thesobiaakbar.adroit.internal.google

import io.github.thesobiaakbar.adroit.config.AgeTreatment
import io.github.thesobiaakbar.adroit.config.MaxAdContentRating
import io.github.thesobiaakbar.adroit.internal.port.SdkRequestConfig
import com.google.android.libraries.ads.mobile.sdk.common.AgeRestrictedTreatment
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration

/**
 * Maps [SdkRequestConfig] to Google's `RequestConfiguration`, always complete because
 * `MobileAds.setRequestConfiguration` replaces the whole configuration. Age treatment uses
 * `setAgeRestrictedTreatment`, never the deprecated child-directed and under-age-of-consent tags.
 */
internal object GoogleRequestConfigMapper {
    fun map(config: SdkRequestConfig): RequestConfiguration = RequestConfiguration.Builder()
        .setTestDeviceIds(config.testDeviceIds)
        .setAgeRestrictedTreatment(map(config.ageTreatment))
        .setMaxAdContentRating(map(config.maxAdContentRating))
        .build()

    fun map(treatment: AgeTreatment): AgeRestrictedTreatment = when (treatment) {
        AgeTreatment.UNSPECIFIED -> AgeRestrictedTreatment.UNSPECIFIED
        AgeTreatment.CHILD -> AgeRestrictedTreatment.CHILD
        AgeTreatment.TEEN -> AgeRestrictedTreatment.TEEN
    }

    fun map(rating: MaxAdContentRating?): RequestConfiguration.MaxAdContentRating = when (rating) {
        null -> RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_UNSPECIFIED
        MaxAdContentRating.G -> RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_G
        MaxAdContentRating.PG -> RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_PG
        MaxAdContentRating.T -> RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_T
        MaxAdContentRating.MA -> RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_MA
    }
}
