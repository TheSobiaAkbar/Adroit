package com.company.admob.internal.google

import com.company.admob.events.AdRevenue
import com.company.admob.events.RevenuePrecision
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.PrecisionType

/** Maps Google's `AdValue` to [AdRevenue]. */
internal object GoogleRevenueMapper {
    // Matched by identity: a host app's R8 may rename Google's enum constants (see GoogleErrorMapper).
    fun map(value: AdValue): AdRevenue =
        map(value.valueMicros, value.currencyCode, precisionName(value.precisionType) ?: value.precisionType.name)

    @Suppress("REDUNDANT_ELSE_IN_WHEN") // Kept for constants added by a newer SDK at runtime.
    fun precisionName(type: PrecisionType): String? = when (type) {
        PrecisionType.UNKNOWN -> "UNKNOWN"
        PrecisionType.ESTIMATED -> "ESTIMATED"
        PrecisionType.PUBLISHER_PROVIDED -> "PUBLISHER_PROVIDED"
        PrecisionType.PRECISE -> "PRECISE"
        else -> null
    }

    fun map(valueMicros: Long, currencyCode: String, precisionName: String): AdRevenue =
        AdRevenue(valueMicros, currencyCode, mapPrecision(precisionName))

    fun mapPrecision(name: String): RevenuePrecision = when (name) {
        "ESTIMATED" -> RevenuePrecision.ESTIMATED
        "PUBLISHER_PROVIDED" -> RevenuePrecision.PUBLISHER_PROVIDED
        "PRECISE" -> RevenuePrecision.PRECISE
        else -> RevenuePrecision.UNKNOWN
    }
}
