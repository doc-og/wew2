package com.wew.launcher.schedule

import com.wew.launcher.data.model.AccessScheduleDay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** True when [now] is outside the allowed window for this day (when the day rule is enabled). */
fun isOutsideAccessWindow(day: AccessScheduleDay?, now: LocalTime): Boolean {
    if (day == null || !day.isEnabled) return false
    val start = LocalTime.parse(day.allowedStart, DateTimeFormatter.ofPattern("HH:mm:ss"))
    val end = LocalTime.parse(day.allowedEnd, DateTimeFormatter.ofPattern("HH:mm:ss"))
    return if (start.isBefore(end)) {
        now.isBefore(start) || now.isAfter(end)
    } else {
        now.isBefore(start) && now.isAfter(end)
    }
}

/**
 * Human-readable time when access opens again (e.g. next allowed_start), using the same
 * day indexing as [AccessScheduleDay.dayOfWeek] (0 = Sun … 6 = Sat).
 */
fun nextUnlockTimeLabel(
    days: List<AccessScheduleDay>,
    todayDow: Int,
    nowMinutes: Int
): String {
    val today = days.firstOrNull { it.dayOfWeek == todayDow }
    if (today != null && today.isEnabled) {
        val startMinutes = timeStringToMinutes(today.allowedStart)
        if (startMinutes > nowMinutes) {
            return formatMinutesAsClock(startMinutes)
        }
    }
    for (offset in 1..7) {
        val dow = (todayDow + offset) % 7
        val day = days.firstOrNull { it.dayOfWeek == dow }
        if (day != null && day.isEnabled) {
            return formatMinutesAsClock(timeStringToMinutes(day.allowedStart))
        }
    }
    return ""
}

private fun timeStringToMinutes(hhmmss: String): Int {
    val parts = hhmmss.split(":").map { it.toIntOrNull() ?: 0 }
    return (parts.getOrElse(0) { 0 }) * 60 + (parts.getOrElse(1) { 0 })
}

private fun formatMinutesAsClock(totalMinutes: Int): String {
    val hour = totalMinutes / 60
    val minute = totalMinutes % 60
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return if (minute == 0) "$displayHour $amPm" else "%d:%02d %s".format(displayHour, minute, amPm)
}
