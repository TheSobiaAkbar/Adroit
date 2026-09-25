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
    fun `a throwing logger never propagates`() {
        val log = AdLog({ _, _, _, _ -> throw IllegalStateException("boom") }, LogLevel.VERBOSE)
        log.e { "still fine" }
    }
}
