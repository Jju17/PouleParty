package dev.rahier.pouleparty.util

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale

/** Games happen in Belgium: times are shown and picked in Brussels time. */
val BUSINESS_ZONE: ZoneId = ZoneId.of("Europe/Brussels")

fun formatDateTime(date: Date, locale: Locale, zone: ZoneId = BUSINESS_ZONE): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(zone)
        .format(date.toInstant())

fun formatTime(date: Date, locale: Locale, zone: ZoneId = BUSINESS_ZONE): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(zone)
        .format(date.toInstant())
