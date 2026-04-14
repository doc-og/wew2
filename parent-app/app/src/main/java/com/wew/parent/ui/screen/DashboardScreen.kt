package com.wew.parent.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wew.parent.data.model.ActivityLogEntry
import com.wew.parent.ui.theme.BrandViolet
import com.wew.parent.ui.theme.ElectricViolet
import com.wew.parent.ui.theme.EmergencyRed
import com.wew.parent.ui.theme.ParentBackground
import com.wew.parent.ui.theme.SafetyGreen
import com.wew.parent.ui.viewmodel.DashboardUiState
import com.wew.parent.ui.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Screen root
// ---------------------------------------------------------------------------

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    onAddCredits: () -> Unit,
    onRemoveCredits: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    if (state.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ParentBackground),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = BrandViolet)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ParentBackground),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {

        // ── Hero card ────────────────────────────────────────────────────
        item {
            HeroCard(state = state, onRefresh = { viewModel.loadDashboard() })
        }

        // ── Inline error banner ──────────────────────────────────────────
        item {
            AnimatedVisibility(
                visible = state.errorMessage != null,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                ErrorBanner(
                    message = state.errorMessage ?: "",
                    onDismiss = { viewModel.dismissError() },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // ── Status + last-seen row ───────────────────────────────────────
        item {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LockCard(
                    isLocked = state.device?.isLocked ?: false,
                    onToggle = { viewModel.remoteLock(!(state.device?.isLocked ?: false)) },
                    modifier = Modifier.weight(1f)
                )
                LastSeenCard(
                    lastSeen = state.device?.lastSeenAt,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── Credit quick-adjust card ─────────────────────────────────────
        item {
            Spacer(Modifier.height(12.dp))
            CreditActionsCard(
                currentCredits = state.device?.currentCredits ?: 0,
                dailyBudget = state.device?.dailyCreditBudget ?: 100,
                onAdd = { amount -> viewModel.addCredits(amount, null) },
                onRemove = { amount -> viewModel.removeCredits(amount, null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }

        // ── Activity section header ──────────────────────────────────────
        item {
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Activity",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1A1A2E)
                )
                if (state.activityFeed.isNotEmpty()) {
                    Text(
                        text = "${state.activityFeed.size} events",
                        fontSize = 13.sp,
                        color = Color(0xFF9999AA)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // ── Activity feed ────────────────────────────────────────────────
        if (state.activityFeed.isEmpty()) {
            item {
                EmptyActivityCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
        } else {
            items(
                items = state.activityFeed,
                key = { it.id }
            ) { entry ->
                ActivityRow(
                    entry = entry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Hero card — gradient header with credit arc
// ---------------------------------------------------------------------------

@Composable
private fun HeroCard(state: DashboardUiState, onRefresh: () -> Unit) {
    val credits = state.device?.currentCredits ?: 0
    val budget = state.device?.dailyCreditBudget?.coerceAtLeast(1) ?: 100
    val fraction = (credits.toFloat() / budget.toFloat()).coerceIn(0f, 1f)

    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 1200),
        label = "creditArc"
    )

    // Arc progress colour shifts with balance health
    val arcColor = when {
        fraction > 0.5f -> Color(0xFF4ADE80) // green
        fraction > 0.2f -> Color(0xFFFBBF24) // amber
        else -> Color(0xFFF87171)             // red
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(BrandViolet, ElectricViolet)
                )
            )
            .padding(bottom = 28.dp)
            .semantics {
                contentDescription =
                    "${state.device?.deviceName ?: "Device"}: $credits of $budget credits remaining"
            }
    ) {
        // Refresh button — top-right, 48dp touch target (accessibility)
        IconButton(
            onClick = onRefresh,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
                .size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh dashboard",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(22.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Device name
            Text(
                text = state.device?.deviceName ?: "No device paired",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(6.dp))

            // Lock status pill
            val isLocked = state.device?.isLocked ?: false
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.18f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = if (isLocked) Color(0xFFFCA5A5) else Color(0xFF86EFAC),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (isLocked) "Locked" else "Active",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(28.dp))

            // Credit arc
            CreditArc(
                animatedFraction = animatedFraction,
                credits = credits,
                arcColor = arcColor
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = "of $budget daily credits",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.65f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Credit arc — custom Canvas gauge
// ---------------------------------------------------------------------------

@Composable
private fun CreditArc(
    animatedFraction: Float,
    credits: Int,
    arcColor: Color
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(180.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "$credits credits remaining" }
        ) {
            val strokeWidth = 18.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)

            // Background track
            drawArc(
                color = Color.White.copy(alpha = 0.20f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Progress
            if (animatedFraction > 0f) {
                drawArc(
                    color = arcColor,
                    startAngle = 135f,
                    sweepAngle = 270f * animatedFraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        // Centre label
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = credits.toString(),
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color.White,
                lineHeight = 48.sp
            )
            Text(
                text = "credits",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.70f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Lock card
// ---------------------------------------------------------------------------

@Composable
private fun LockCard(
    isLocked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = if (isLocked) EmergencyRed else SafetyGreen
    val icon = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen
    val statusText = if (isLocked) "Locked" else "Active"
    val actionText = if (isLocked) "Unlock" else "Lock"

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.semantics {
            contentDescription = "Phone is $statusText. Double-tap to $actionText."
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Icon badge
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            Text(
                text = "Phone",
                fontSize = 12.sp,
                color = Color(0xFF9999AA),
                fontWeight = FontWeight.Medium
            )

            Text(
                text = statusText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )

            // Action button — minimum 48dp height for accessibility
            Button(
                onClick = onToggle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = statusColor,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    text = actionText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Last seen card
// ---------------------------------------------------------------------------

@Composable
private fun LastSeenCard(
    lastSeen: String?,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.semantics {
            contentDescription = "Last seen: ${formatLastSeen(lastSeen)}"
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(BrandViolet.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = BrandViolet,
                    modifier = Modifier.size(26.dp)
                )
            }

            Text(
                text = "Last Seen",
                fontSize = 12.sp,
                color = Color(0xFF9999AA),
                fontWeight = FontWeight.Medium
            )

            Text(
                text = formatLastSeen(lastSeen),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A2E),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )

            // Spacer so both cards end at the same height
            Spacer(Modifier.height(44.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Credit quick-adjust card
// ---------------------------------------------------------------------------

@Composable
private fun CreditActionsCard(
    currentCredits: Int,
    dailyBudget: Int,
    onAdd: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Adjust Credits",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1A1A2E)
                )
                Text(
                    text = "$currentCredits / $dailyBudget",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF9999AA)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Add row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ADD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SafetyGreen,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.width(52.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    listOf(10, 25, 50).forEach { amount ->
                        CreditChip(
                            label = "+$amount",
                            textColor = SafetyGreen,
                            backgroundColor = SafetyGreen.copy(alpha = 0.10f),
                            onClick = { onAdd(amount) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Remove row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "REMOVE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = EmergencyRed,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.width(52.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    listOf(10, 25).forEach { amount ->
                        CreditChip(
                            label = "-$amount",
                            textColor = EmergencyRed,
                            backgroundColor = EmergencyRed.copy(alpha = 0.10f),
                            onClick = { onRemove(amount) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Spacer to keep alignment with 3-col add row
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CreditChip(
    label: String,
    textColor: Color,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(44.dp) // meets 44dp minimum touch target (Apple HIG / WCAG 2.5.5)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(
                onClickLabel = "Adjust credits by $label"
            ) { onClick() }
            .semantics { contentDescription = "Adjust credits $label" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

// ---------------------------------------------------------------------------
// Activity feed
// ---------------------------------------------------------------------------

@Composable
private fun EmptyActivityCard(modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = Color(0xFFCCCCDD),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "No activity yet today",
                    fontSize = 15.sp,
                    color = Color(0xFF9999AA)
                )
            }
        }
    }
}

@Composable
private fun ActivityRow(entry: ActivityLogEntry, modifier: Modifier = Modifier) {
    val display = activityDisplayFor(entry)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.semantics {
            contentDescription = "${display.label}, ${formatTimestamp(entry.createdAt)}" +
                if (entry.creditsDeducted > 0) ", minus ${entry.creditsDeducted} credits" else ""
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(display.tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = display.icon,
                    contentDescription = null,
                    tint = display.tint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            // Description + timestamp
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = display.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1A1A2E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatTimestamp(entry.createdAt),
                    fontSize = 12.sp,
                    color = Color(0xFF9999AA)
                )
            }

            // Credit cost badge
            if (entry.creditsDeducted > 0) {
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(BrandViolet.copy(alpha = 0.09f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "−${entry.creditsDeducted}",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandViolet
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Error banner
// ---------------------------------------------------------------------------

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = EmergencyRed.copy(alpha = 0.10f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.semantics { contentDescription = "Error: $message" }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = EmergencyRed,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                color = EmergencyRed,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss error",
                    tint = EmergencyRed,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private data class ActivityDisplay(
    val icon: ImageVector,
    val tint: Color,
    val label: String
)

private fun activityDisplayFor(entry: ActivityLogEntry): ActivityDisplay =
    when (entry.actionType) {
        "app_open" -> ActivityDisplay(
            Icons.Default.Apps, ElectricViolet,
            "Opened ${entry.appName ?: entry.appPackage ?: "an app"}"
        )
        "message_sent" -> ActivityDisplay(
            Icons.Default.Message, Color(0xFF0EA5E9),
            "Sent a message via ${entry.appName ?: "messaging"}"
        )
        "call_made" -> ActivityDisplay(
            Icons.Default.Phone, SafetyGreen,
            "Made a phone call"
        )
        "call_received" -> ActivityDisplay(
            Icons.Default.Phone, Color(0xFF10B981),
            "Received a phone call"
        )
        "photo_taken" -> ActivityDisplay(
            Icons.Default.CameraAlt, Color(0xFFF59E0B),
            "Took a photo"
        )
        "photo_shared" -> ActivityDisplay(
            Icons.Default.Share, Color(0xFFF97316),
            "Shared a photo"
        )
        "web_link_opened" -> ActivityDisplay(
            Icons.Default.Language, ElectricViolet,
            "Opened a web link"
        )
        "app_blocked" -> ActivityDisplay(
            Icons.Default.Block, EmergencyRed,
            "Blocked: ${entry.appName ?: "unknown app"}"
        )
        "device_admin_revoked" -> ActivityDisplay(
            Icons.Default.Warning, EmergencyRed,
            "Tamper attempt detected"
        )
        "lock_activated" -> ActivityDisplay(
            Icons.Default.Lock, EmergencyRed,
            "Device locked"
        )
        "lock_deactivated" -> ActivityDisplay(
            Icons.Default.LockOpen, SafetyGreen,
            "Device unlocked"
        )
        else -> ActivityDisplay(
            Icons.Default.AccessTime, Color(0xFF9999AA),
            entry.actionType.replace("_", " ").replaceFirstChar { it.uppercaseChar() }
        )
    }

private fun formatLastSeen(iso: String?): String {
    if (iso == null) return "Unknown"
    return try {
        // Try with timezone offset, then without
        val withTz = runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).parse(iso)
        }.getOrNull()
        val date: Date = withTz
            ?: SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                .parse(iso.take(19))
            ?: return "Unknown"

        val diffMs = System.currentTimeMillis() - date.time
        val diffMins = diffMs / 60_000
        val diffHours = diffMins / 60
        when {
            diffMins < 1 -> "Just now"
            diffMins < 60 -> "${diffMins}m ago"
            diffHours < 24 -> "${diffHours}h ago"
            diffHours < 48 -> "Yesterday"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
        }
    } catch (e: Exception) {
        "Unknown"
    }
}

private fun formatTimestamp(iso: String): String {
    return try {
        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            .parse(iso.take(19)) ?: return iso
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
    } catch (e: Exception) {
        iso
    }
}
