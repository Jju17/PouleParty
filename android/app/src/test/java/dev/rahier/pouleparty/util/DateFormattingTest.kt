package dev.rahier.pouleparty.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DateFormattingTest {

    // 2026-06-06 18:30 UTC is 20:30 in Brussels (summer time).
    private val instant = Date(1_780_770_600_000L)

    @Test
    fun `times are shown in Brussels whatever the device zone`() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            assertEquals("20:30", formatTime(instant, Locale.FRANCE))
            assertTrue(formatDateTime(instant, Locale.FRANCE).contains("20:30"))
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun `the locale drives the format`() {
        assertTrue(formatTime(instant, Locale.US).contains("8:30"))
        assertTrue(formatDateTime(instant, Locale.of("nl", "BE")).contains("jun"))
    }

    @Test
    fun `today starts at midnight in Brussels`() {
        val lateEvening = Date(1_780_783_200_000L) // 2026-06-06 22:00 UTC, already June 7 in Brussels
        val start = startOfToday(lateEvening)
        assertEquals(7, start.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, start.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, start.get(Calendar.MILLISECOND))
    }

    @Test
    fun `calendarAt keeps the day and sets the Brussels time`() {
        val cal = calendarAt(instant, 9, 15)
        assertEquals(9, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(6, cal.get(Calendar.DAY_OF_MONTH))
    }
}
