// The framework PreferenceManager is deprecated, but it's the reference for the default preferences file name.
@file:Suppress("DEPRECATION")

package com.company.admob.internal.consent

import android.content.Context
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.company.admob.internal.logging.AdLog
import com.company.admob.logging.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RdpWriterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val writer = RdpWriter(context, AdLog({ _, _, _, _ -> }, LogLevel.NONE))
    private val defaultPreferences get() = PreferenceManager.getDefaultSharedPreferences(context)

    @Test
    fun `the file name matches PreferenceManager's default preferences`() {
        assertEquals(
            PreferenceManager.getDefaultSharedPreferencesName(context),
            RdpWriter.defaultPreferencesName(context),
        )
    }

    @Test
    fun `enabling writes gad_rdp = 1 to the default preferences`() {
        writer.apply(restrictedDataProcessing = true)
        assertEquals(1, defaultPreferences.getInt("gad_rdp", 0))
    }

    @Test
    fun `disabling removes the key`() {
        writer.apply(restrictedDataProcessing = true)
        writer.apply(restrictedDataProcessing = false)
        assertFalse(defaultPreferences.contains("gad_rdp"))
    }
}
