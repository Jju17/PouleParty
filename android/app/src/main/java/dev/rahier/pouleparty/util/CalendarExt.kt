package dev.rahier.pouleparty.util

import java.util.Calendar
import java.util.Date

/**
 * Returns a [Calendar] set to midnight (00:00:00.000) of today.
 */
fun startOfToday(): Calendar = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

/**
 * Returns a new [Calendar] initialized to [base] (or now when null) with [hour],
 * [minute] applied and seconds zeroed. Millisecond is preserved unless the caller
 * resets it.
 */
fun calendarAt(base: Date? = null, hour: Int, minute: Int): Calendar =
    Calendar.getInstance().apply {
        if (base != null) time = base
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
    }
