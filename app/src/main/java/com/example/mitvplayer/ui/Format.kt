package com.example.mitvplayer.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SPANISH: Locale = Locale.forLanguageTag("es-MX")

/** "04:57 PM" */
fun formatClock(millis: Long): String =
    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(millis))

/** "octubre 12, 2026" */
fun formatDateLong(millis: Long): String =
    SimpleDateFormat("MMMM d, yyyy", SPANISH).format(Date(millis))

/** "lun 12 oct" */
fun formatDayShort(millis: Long): String =
    SimpleDateFormat("EEE d MMM", SPANISH).format(Date(millis))

/** "17:30" a partir de segundos desde epoch. */
fun formatHm(epochSeconds: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochSeconds * 1000))

/** Clave de día (para agrupar programas por fecha). */
fun dayKey(epochSeconds: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(epochSeconds * 1000))

/** "hace 5 min" */
fun agoText(updatedAtMillis: Long, nowMillis: Long): String {
    val seconds = ((nowMillis - updatedAtMillis) / 1000).coerceAtLeast(0)
    return when {
        seconds < 60 -> "hace ${seconds.coerceAtLeast(1)} seg"
        seconds < 3600 -> "hace ${seconds / 60} min"
        seconds < 86400 -> "hace ${seconds / 3600} h"
        else -> "hace ${seconds / 86400} d"
    }
}

/** "01:23:45" o "23:45" */
fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** "123 MB" */
fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.0f KB".format(bytes / 1024.0)
}
