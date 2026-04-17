package com.wew.launcher.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wew.launcher.ui.theme.BrandViolet
import com.wew.launcher.ui.theme.ElectricViolet
import com.wew.launcher.ui.theme.Night
import com.wew.launcher.ui.theme.OnNight

/**
 * Full-screen lock shown when the access schedule blocks phone use.
 *
 * @param unlockTime Display string for when the phone next becomes available (e.g. "7:00 AM").
 * @param onPasscodeUnlock Called when the parent enters the correct passcode — dismisses the lock
 *                         for the remainder of the session (does not persist).
 */
@Composable
fun ScheduleLockScreen(
    unlockTime: String,
    deviceId: String,
    onPasscodeUnlock: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showPasscode by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Night, Color(0xFF1A1040))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            // Lock icon
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(ElectricViolet.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = ElectricViolet,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = "phone is locked",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = OnNight,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = if (unlockTime.isNotBlank())
                    "available again at $unlockTime"
                else
                    "check back later",
                fontSize = 16.sp,
                color = OnNight.copy(alpha = 0.55f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(48.dp))

            // Parent override — subtle, not prominent
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.06f),
                modifier = Modifier.clip(RoundedCornerShape(14.dp))
            ) {
                TextButton(
                    onClick = { showPasscode = true },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "parent override",
                        fontSize = 14.sp,
                        color = OnNight.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }

    if (showPasscode) {
        PasscodeDialog(
            deviceId = deviceId,
            onSuccess = {
                showPasscode = false
                onPasscodeUnlock()
            },
            onDismiss = { showPasscode = false }
        )
    }
}

/**
 * Computes the next unlock time string from a list of AccessScheduleDay rows.
 * Returns e.g. "7:00 AM" for the next day's allowed_start, or "" if unknown.
 */
fun nextUnlockTimeString(
    days: List<com.wew.launcher.data.model.AccessScheduleDay>,
    todayDow: Int,
    nowMinutes: Int  // minutes since midnight
): String {
    // Check if today's window starts later today
    val today = days.firstOrNull { it.dayOfWeek == todayDow }
    if (today != null && today.isEnabled) {
        val startMinutes = timeStringToMinutes(today.allowedStart)
        if (startMinutes > nowMinutes) {
            return formatMinutes(startMinutes)
        }
    }

    // Look ahead through the next 7 days
    for (offset in 1..7) {
        val dow = (todayDow + offset) % 7
        val day = days.firstOrNull { it.dayOfWeek == dow }
        if (day != null && day.isEnabled) {
            return formatMinutes(timeStringToMinutes(day.allowedStart))
        }
    }

    return ""
}

private fun timeStringToMinutes(hhmmss: String): Int {
    val parts = hhmmss.split(":").map { it.toIntOrNull() ?: 0 }
    return (parts.getOrElse(0) { 0 }) * 60 + (parts.getOrElse(1) { 0 })
}

private fun formatMinutes(totalMinutes: Int): String {
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
