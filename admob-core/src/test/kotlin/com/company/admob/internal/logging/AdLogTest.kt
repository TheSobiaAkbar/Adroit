package com.company.admob.internal.logging

import com.company.admob.logging.AdLogger
import com.company.admob.logging.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class AdLogTest {
    private val written = mutableListOf<Pair<LogLevel, String>>()
    private val logger = AdLogger { level, _, message, _ -> written += level to message }

    @Test
    fun `messages below the level are never built`() {
        val log = AdLog(logger, LogLevel.WARN)
        var built = 0
        log.v { built++; "v" }
        log.d { built++; "d" }
        log.i { built++; "i" }
        assertEquals(0, built)
        log.w { built++; "w" }
        log.e { built++; "e" }
        assertEquals(2, built)
        assertEquals(listOf(LogLevel.WARN to "w", LogLevel.ERROR to "e"), written)
    }

    @Test
    fun `NONE disables everything`() {
        val log = AdLog(logger, LogLevel.NONE)
        var built = 0
        log.e { built++; "e" }
        assertEquals(0, built)
        assertEquals(emptyList<Pair<LogLevel, String>>(), written)
    }

    @Test
    fun `with redaction, every full ad unit ID is shortened at every level`() {
        val log = AdLog(logger, LogLevel.VERBOSE, redactAdUnitIds = true)
        log.d { "INTERSTITIAL:ca-app-pub-1234567890123456/1234567890: retry 1 in 2s" }
        log.e { "unit ca-app-pub-1234567890123456/0987654321 and ca-app-pub-1234567890123456/1111122222" }
        assertEquals(
            listOf(
                LogLevel.DEBUG to "INTERSTITIAL:…7890: retry 1 in 2s",
                LogLevel.ERROR to "unit …4321 and …2222",
            ),
            written,
        )
    }

    @Test
    fun `without redaction, ad unit IDs are logged in full`() {
        val log = AdLog(logger, LogLevel.VERBOSE)
        log.d { "ca-app-pub-1234567890123456/1234567890" }
        assertEquals(listOf(LogLevel.DEBUG to "ca-app-pub-1234567890123456/1234567890"), written)
    }

    @Test
    fun `a throwing logger never propagates`() {
        val log = AdLog({ _, _, _, _ -> throw IllegalStateException("boom") }, LogLevel.VERBOSE)
        log.e { "still fine" }
    }
}
