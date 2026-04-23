package com.wew.parent.ui.screen

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wew.parent.data.model.AccessScheduleDay
import com.wew.parent.data.repository.ParentRepository
import com.wew.parent.ui.theme.BrandViolet
import com.wew.parent.ui.theme.ElectricViolet
import com.wew.parent.ui.theme.ParentBackground
import com.wew.parent.ui.theme.SafetyGreen
import kotlinx.coroutines.launch

private val DAY_NAMES = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val DAY_NAMES_FULL = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

// Default allowed window: 7 AM – 9 PM
private const val DEFAULT_START = "07:00:00"
private const val DEFAULT_END = "21:00:00"

// ── Screen ─────────────────────────────────────────────────────────────────────

@Composable
fun AccessScheduleScreen(
    deviceId: String,
    onBack: () -> Unit
) {
    val repo = remember { ParentRepository() }
    val scope = rememberCoroutineScope()

    // One mutable state object per day (index 0=Sun … 6=Sat)
    var days by remember {
        mutableStateOf(
            (0..6).map { dow ->
                AccessScheduleDay(
                    deviceId = deviceId,
                    dayOfWeek = dow,
                    isEnabled = false,
                    allowedStart = DEFAULT_START,
                    allowedEnd = DEFAULT_END
                )
            }
        )
    }
    var isLoading by remember { mutableStateOf(true) }
    var saveError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(deviceId) {
        isLoading = true
        if (deviceId.isBlank()) {
            isLoading = false
            saveError = "No device linked — open Settings from the dashboard after registering."
            return@LaunchedEffect
        }
        val loaded = runCatching { repo.getAccessSchedule(deviceId) }.getOrDefault(emptyList())
        // Merge loaded rows into the default list (keeps days absent from DB as disabled defaults)
        days = days.map { default ->
            loaded.firstOrNull { it.dayOfWeek == default.dayOfWeek } ?: default
        }
        isLoading = false
    }

    fun saveDay(updated: AccessScheduleDay) {
        val updatedList = days.map { if (it.dayOfWeek == updated.dayOfWeek) updated else it }
        days = updatedList
        scope.launch {
            runCatching {
                repo.upsertAccessSchedule(listOf(updated))
            }.onFailure { saveError = "Could not save — check your connection" }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ParentBackground)
    ) {
        // ── Hero header ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(BrandViolet, ElectricViolet))
                )
                .padding(horizontal = 8.dp)
                .padding(top = 12.dp, bottom = 24.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, top = 52.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.padding(start = 10.dp))
                    Text(
                        "Phone access schedule",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Set when your child's phone is accessible each day. Outside these hours the launcher shows a lock screen.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BrandViolet)
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(20.dp))

            saveError?.let { msg ->
                Text(
                    msg,
                    fontSize = 13.sp,
                    color = Color(0xFFC0392B),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFFEEEE))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
                Spacer(Modifier.height(12.dp))
            }

            Text(
                "DAILY SCHEDULE",
                fontSize = 11.sp,
                color = Color(0xFF9999AA),
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    days.forEachIndexed { index, day ->
                        DayScheduleRow(
                            day = day,
                            onUpdate = { saveDay(it) }
                        )
                        if (index < days.lastIndex) {
                            Divider(color = Color(0xFFF0F0F8))
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Tap a time to change it. Toggle off to allow access all day on that day.",
                fontSize = 12.sp,
                color = Color(0xFF9999AA),
                lineHeight = 17.sp
            )
            Spacer(Modifier.height(80.dp))
        }
    }
}

// ── Day row ────────────────────────────────────────────────────────────────────

@Composable
private fun DayScheduleRow(
    day: AccessScheduleDay,
    onUpdate: (AccessScheduleDay) -> Unit
) {
    val context = LocalContext.current

    fun showTimePicker(initialTime: String, onSet: (String) -> Unit) {
        val parts = initialTime.split(":").map { it.toIntOrNull() ?: 0 }
        val initialHour = parts.getOrElse(0) { 0 }
        val initialMinute = parts.getOrElse(1) { 0 }
        TimePickerDialog(
            context,
            { _, hour, minute -> onSet("%02d:%02d:00".format(hour, minute)) },
            initialHour,
            initialMinute,
            false
        ).show()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Day name + toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Day abbreviation badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (day.isEnabled) BrandViolet.copy(alpha = 0.10f) else Color(0xFFF0F0F8)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = DAY_NAMES[day.dayOfWeek],
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (day.isEnabled) BrandViolet else Color(0xFF9999AA)
                )
            }

            Spacer(Modifier.padding(start = 12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    DAY_NAMES_FULL[day.dayOfWeek],
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1A1A2E)
                )
                Text(
                    if (day.isEnabled) "limited hours" else "no restrictions",
                    fontSize = 12.sp,
                    color = if (day.isEnabled) BrandViolet.copy(alpha = 0.70f) else Color(0xFF9999AA)
                )
            }

            Switch(
                checked = day.isEnabled,
                onCheckedChange = { onUpdate(day.copy(isEnabled = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SafetyGreen,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFCCCCDD)
                )
            )
        }

        // Time range — only visible when enabled
        AnimatedVisibility(
            visible = day.isEnabled,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.size(36.dp)) // align with content after badge

                TimeChip(
                    label = "from",
                    time = formatTime(day.allowedStart),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        showTimePicker(day.allowedStart) { newTime ->
                            onUpdate(day.copy(allowedStart = newTime))
                        }
                    }
                )

                Text("–", fontSize = 14.sp, color = Color(0xFF9999AA))

                TimeChip(
                    label = "until",
                    time = formatTime(day.allowedEnd),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        showTimePicker(day.allowedEnd) { newTime ->
                            onUpdate(day.copy(allowedEnd = newTime))
                        }
                    }
                )
            }
        }
    }
}

// ── Time chip ──────────────────────────────────────────────────────────────────

@Composable
private fun TimeChip(
    label: String,
    time: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF5F4FF))
            .border(1.dp, BrandViolet.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = BrandViolet.copy(alpha = 0.60f),
            letterSpacing = 0.5.sp
        )
        Text(
            text = time,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = BrandViolet
        )
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

/** Formats "HH:mm:ss" into "h:mm AM/PM" for display. */
private fun formatTime(hhmmss: String): String {
    val parts = hhmmss.split(":").map { it.toIntOrNull() ?: 0 }
    val hour = parts.getOrElse(0) { 0 }
    val minute = parts.getOrElse(1) { 0 }
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "%d:%02d %s".format(displayHour, minute, amPm)
}
