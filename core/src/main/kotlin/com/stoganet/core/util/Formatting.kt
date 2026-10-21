package com.stoganet.core.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

fun formatRuntime(minutes: Int): String {
    if (minutes <= 0) return ""
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

fun formatAirDate(isoDate: String?): String? {
    if (isoDate == null) return null
    return try {
        LocalDate.parse(isoDate).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    } catch (_: DateTimeParseException) {
        null
    }
}
