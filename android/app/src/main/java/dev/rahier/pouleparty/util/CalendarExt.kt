package dev.rahier.pouleparty.util

import java.util.Calendar
import java.util.Date
import java.util.TimeZone

private val businessTimeZone: TimeZone = TimeZone.getTimeZone(BUSINESS_ZONE)

fun startOfToday(now: Date = Date(), zone: TimeZone = businessTimeZone): Calendar = Calendar.getInstance(zone).apply {
    time = now
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

fun calendarAt(base: Date? = null, hour: Int, minute: Int, zone: TimeZone = businessTimeZone): Calendar =
    Calendar.getInstance(zone).apply {
        if (base != null) time = base
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
