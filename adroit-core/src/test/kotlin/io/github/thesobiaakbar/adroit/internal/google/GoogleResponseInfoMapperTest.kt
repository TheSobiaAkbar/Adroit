package io.github.thesobiaakbar.adroit.internal.google

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleResponseInfoMapperTest {
    private val extras = mapOf(
        GoogleResponseInfoMapper.EXTRA_MEDIATION_GROUP_NAME to "Group A",
        GoogleResponseInfoMapper.EXTRA_AB_TEST_NAME to "Test 1",
        GoogleResponseInfoMapper.EXTRA_AB_TEST_VARIANT to "B",
    )

    @Test
    fun `all fields are mapped`() {
        val info = GoogleResponseInfoMapper.map(
            responseId = "resp-1",
            responseAdapterClassName = "com.response.Adapter",
            sourceName = "AdMob Network",
            sourceId = "5450213213286189855",
            sourceInstanceName = "Instance",
            sourceInstanceId = "123",
            sourceAdapterClassName = "com.source.Adapter",
            extra = extras::get,
        )
        assertEquals("AdMob Network", info.name)
        assertEquals("5450213213286189855", info.id)
        assertEquals("Instance", info.instanceName)
        assertEquals("123", info.instanceId)
        assertEquals("com.source.Adapter", info.adapterClassName)
        assertEquals("resp-1", info.responseId)
        assertEquals("Group A", info.mediationGroupName)
        assertEquals("Test 1", info.abTestName)
        assertEquals("B", info.abTestVariant)
    }

    @Test
    fun `missing loaded source falls back to the response adapter and blanks become null`() {
        val info = GoogleResponseInfoMapper.map(
            responseId = " ",
            responseAdapterClassName = "com.response.Adapter",
            sourceName = null,
            sourceId = "",
            sourceInstanceName = null,
            sourceInstanceId = null,
            sourceAdapterClassName = null,
            extra = { null },
        )
        assertEquals("com.response.Adapter", info.adapterClassName)
        assertNull(info.responseId)
        assertNull(info.id)
        assertNull(info.name)
        assertNull(info.mediationGroupName)
    }

    @Test
    fun `null response info maps to null`() {
        assertNull(GoogleResponseInfoMapper.map(null))
    }
}
