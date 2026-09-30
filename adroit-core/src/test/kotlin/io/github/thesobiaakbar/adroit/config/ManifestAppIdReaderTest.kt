package io.github.thesobiaakbar.adroit.config

import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import io.github.thesobiaakbar.adroit.internal.config.ConfigIssueCode
import io.github.thesobiaakbar.adroit.internal.config.ConfigValidator
import io.github.thesobiaakbar.adroit.internal.config.ManifestAppIdReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ManifestAppIdReaderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun setMetaData(metaData: Bundle?) {
        shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName)
            .applicationInfo!!.metaData = metaData
    }

    @Test
    fun `present app ID is returned`() {
        setMetaData(Bundle().apply { putString(ManifestAppIdReader.META_DATA_KEY, TestIds.APP_ID) })
        assertEquals(TestIds.APP_ID, ManifestAppIdReader.read(context))
    }

    @Test
    @Config(sdk = [32])
    fun `present app ID is returned before API 33`() {
        setMetaData(Bundle().apply { putString(ManifestAppIdReader.META_DATA_KEY, TestIds.APP_ID) })
        assertEquals(TestIds.APP_ID, ManifestAppIdReader.read(context))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        setMetaData(Bundle().apply { putString(ManifestAppIdReader.META_DATA_KEY, "  ${TestIds.APP_ID}\n") })
        assertEquals(TestIds.APP_ID, ManifestAppIdReader.read(context))
    }

    @Test
    fun `missing meta-data returns null`() {
        setMetaData(null)
        assertNull(ManifestAppIdReader.read(context))
        setMetaData(Bundle())
        assertNull(ManifestAppIdReader.read(context))
    }

    @Test
    fun `blank or non-string value returns null`() {
        setMetaData(Bundle().apply { putString(ManifestAppIdReader.META_DATA_KEY, "   ") })
        assertNull(ManifestAppIdReader.read(context))
        setMetaData(Bundle().apply { putInt(ManifestAppIdReader.META_DATA_KEY, 42) })
        assertNull(ManifestAppIdReader.read(context))
    }

    @Test
    fun `malformed value is returned as is and rejected by validation`() {
        setMetaData(Bundle().apply { putString(ManifestAppIdReader.META_DATA_KEY, "ca-app-pub-123~456") })
        val appId = ManifestAppIdReader.read(context)
        assertEquals("ca-app-pub-123~456", appId)

        val result = ConfigValidator.validate(adMobConfig { }, appId, isDebuggable = true)
        assertEquals(listOf(ConfigIssueCode.APP_ID_MALFORMED), result.errors.map { it.code })
    }
}
