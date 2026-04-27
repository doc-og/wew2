package com.wew.launcher.ui.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Formats message instants using the **device system timezone** — the local clock
 * for when the row exists on this phone (Telephony dates are UTC epoch ms).
 *
 * This matches how users expect “received on my phone” times to read, including
 * if the child travels or the system timezone is changed. It intentionally does
 * **not** use [devices.timezone] from Supabase (that zone is for server-side
 * schedules like daily summaries, not SMS UI).
 */
object MessageTimeFormat {

    /** Android’s current default zone (Settings → Date & time). */
    fun receivedOnDeviceZone(): ZoneId = ZoneId.systemDefault()

    fun formatBubble(epochMs: Long, zone: ZoneId): String {
        if (epochMs == 0L) return ""
        val zdt = Instant.ofEpochMilli(epochMs).atZone(zone)
        val now = ZonedDateTime.now(zone)
        val sameDay = zdt.toLocalDate() == now.toLocalDate()
        val pattern = if (sameDay) "h:mm a" else "MMM d, h:mm a"
        val fmt = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
        return fmt.format(zdt)
    }

    fun formatThreadPreview(epochMs: Long, zone: ZoneId): String {
        if (epochMs == 0L) return ""
        val msg = Instant.ofEpochMilli(epochMs).atZone(zone)
        val now = ZonedDateTime.now(zone)
        val msgDate = msg.toLocalDate()
        val today = now.toLocalDate()
        val wf = WeekFields.of(Locale.getDefault())
        return when {
            msgDate == today ->
                DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()).format(msg)
            msgDate == today.minusDays(1) -> "Yesterday"
            msg.get(wf.weekBasedYear()) == now.get(wf.weekBasedYear()) &&
                msg.get(wf.weekOfWeekBasedYear()) == now.get(wf.weekOfWeekBasedYear()) ->
                DateTimeFormatter.ofPattern("EEE", Locale.getDefault()).format(msg)
            else ->
                DateTimeFormatter.ofPattern("MM/dd/yy", Locale.getDefault()).format(msg)
        }
    }
}
