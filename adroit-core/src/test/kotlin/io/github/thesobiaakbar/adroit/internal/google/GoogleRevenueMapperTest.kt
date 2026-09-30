package io.github.thesobiaakbar.adroit.internal.google

import io.github.thesobiaakbar.adroit.events.AdRevenue
import io.github.thesobiaakbar.adroit.events.RevenuePrecision
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.PrecisionType
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleRevenueMapperTest {
    @Test
    fun `every precision type maps to the same-named value`() {
        PrecisionType.values().forEach {
            assertEquals(RevenuePrecision.valueOf(it.name), GoogleRevenueMapper.mapPrecision(it.name))
        }
    }

    @Test
    fun `every precision constant is matched by identity`() {
        PrecisionType.values().forEach { assertEquals(it.name, GoogleRevenueMapper.precisionName(it)) }
    }

    @Test
    fun `unknown precision maps to UNKNOWN`() {
        assertEquals(RevenuePrecision.UNKNOWN, GoogleRevenueMapper.mapPrecision("SOMETHING_NEW"))
    }

    @Test
    fun `value and currency are kept`() {
        assertEquals(AdRevenue(12_345, "EUR", RevenuePrecision.ESTIMATED), GoogleRevenueMapper.map(12_345, "EUR", "ESTIMATED"))
        assertEquals(
            AdRevenue(7, "USD", RevenuePrecision.PRECISE),
            GoogleRevenueMapper.map(AdValue(PrecisionType.PRECISE, 7, "USD")),
        )
    }
}
