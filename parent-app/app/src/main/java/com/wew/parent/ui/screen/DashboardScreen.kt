package com.wew.parent.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    onAddCredits: () -> Unit = {},
    onRemoveCredits: () -> Unit = {},
    onNavigateUrls: () -> Unit = {},
    onNavigateContacts: () -> Unit = {}
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

        // ── Compact header ───────────────────────────────────────────────
        item {
            CompactHeader(
                state = state,
                onRefresh = { viewModel.loadDashboard() },
                onAddTokens = { viewModel.addTokens(it) },
                onRemoveTokens = { viewModel.removeTokens(it) }
            )
        }

        // ── Error banner ─────────────────────────────────────────────────
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

        // ── Pending requests section ─────────────────────────────────────
        val hasPending = state.pendingUrlCount > 0 || state.pendingContactCount > 0
        if (hasPending) {
            item {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "ACTION NEEDED",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF9999AA),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(8.dp))
            }
            if (state.pendingUrlCount > 0) {
                item {
                    PendingRequestRow(
                        icon = Icons.Default.Language,
                        tint = BrandViolet,
                        label = "URL Approvals",
                        count = state.pendingUrlCount,
                        onClick = onNavigateUrls,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp)
                    )
                }
            }
            if (state.pendingContactCount > 0) {
                item {
                    PendingRequestRow(
                        icon = Icons.Default.People,
                        tint = Color(0xFF0EA5E9),
                        label = "Contact Requests",
                        count = state.pendingContactCount,
                        onClick = onNavigateContacts,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp)
                    )
                }
            }
        }

        // ── Activity section header ──────────────────────────────────────
        item {
            Spacer(Modifier.height(if (hasPending) 16.dp else 20.dp))
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
            items(items = state.activityFeed, key = { it.id }) { entry ->
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
// Compact header — gradient strip with device info + token bar + adjust chips
// ---------------------------------------------------------------------------

@Composable
private fun CompactHeader(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onAddTokens: (Int) -> Unit,
    onRemoveTokens: (Int) -> Unit
) {
    val tokens = state.device?.currentTokens ?: 0
    val budget = state.device?.dailyTokenBudget?.coerceAtLeast(1) ?: 10000
    val fraction = (tokens.toFloat() / budget.toFloat()).coerceIn(0f, 1f)

    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(1000),
        label = "tokenBar"
    )

    val barColor = when {
        fraction > 0.5f -> Color(0xFF4ADE80)
        fraction > 0.2f -> Color(0xFFFBBF24)
        else -> Color(0xFFF87171)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(BrandViolet, ElectricViolet)
                )
            )
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 20.dp)
            .semantics {
                contentDescription =
                    "${state.device?.deviceName ?: "Device"}: $tokens of $budget tokens"
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Top row: device name + last-seen + refresh ────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.device?.deviceName ?: "No device paired",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val lastSeen = formatLastSeen(state.device?.lastSeenAt)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = lastSeen,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Token bar ─────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Progress track
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.20f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedFraction)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(barColor)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${formatTokens(tokens)} / ${formatTokens(budget)}",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "daily tokens",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.50f)
            )

            Spacer(Modifier.height(14.dp))

            // ── Quick-adjust chips ────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(500, 1000, 2500).forEach { amount ->
                    TokenChip(
                        label = "+${formatTokens(amount)}",
                        textColor = Color(0xFF4ADE80),
                        backgroundColor = Color(0xFF4ADE80).copy(alpha = 0.18f),
                        onClick = { onAddTokens(amount) },
                        modifier = Modifier.weight(1f)
                    )
                }
                listOf(500, 1000).forEach { amount ->
                    TokenChip(
                        label = "−${formatTokens(amount)}",
                        textColor = Color(0xFFF87171),
                        backgroundColor = Color(0xFFF87171).copy(alpha = 0.18f),
                        onClick = { onRemoveTokens(amount) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TokenChip(
    label: String,
    textColor: Color,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

// ---------------------------------------------------------------------------
// Pending requests row
// ---------------------------------------------------------------------------

@Composable
private fun PendingRequestRow(
    icon: ImageVector,
    tint: Color,
    label: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1A1A2E),
                modifier = Modifier.weight(1f)
            )
            // Count badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFF9800).copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = count.toString(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF9800)
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = Color(0xFFCCCCDD),
                modifier = Modifier.size(20.dp)
            )
        }
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
                Text("No activity yet today", fontSize = 15.sp, color = Color(0xFF9999AA))
            }
        }
    }
}

@Composable
private fun ActivityRow(entry: ActivityLogEntry, modifier: Modifier = Modifier) {
    val display = activityDisplayFor(entry)
    val tokenCost = if (entry.tokensConsumed > 0) entry.tokensConsumed else entry.creditsDeducted

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.semantics {
            contentDescription = "${display.label}, ${formatTimestamp(entry.createdAt)}" +
                if (tokenCost > 0) ", −$tokenCost tokens" else ""
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(display.tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(display.icon, null, tint = display.tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
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
            if (tokenCost > 0) {
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(BrandViolet.copy(alpha = 0.09f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "−${formatTokens(tokenCost)}",
                        fontSize = 12.sp,
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
            Icon(Icons.Default.Warning, null, tint = EmergencyRed, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                color = EmergencyRed,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Dismiss error", tint = EmergencyRed, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private data class ActivityDisplay(val icon: ImageVector, val tint: Color, val label: String)

private fun activityDisplayFor(entry: ActivityLogEntry): ActivityDisplay =
    when (entry.actionType) {
        "app_open" -> ActivityDisplay(
            Icons.Default.Apps, ElectricViolet,
            "Opened ${entry.appName ?: entry.appPackage ?: "an app"}"
        )
        "message_sent", "SMS_SENT", "MMS_SENT" -> ActivityDisplay(
            Icons.Default.Message, Color(0xFF0EA5E9),
            "Sent a message${entry.appName?.let { " via $it" } ?: ""}"
        )
        "call_made" -> ActivityDisplay(Icons.Default.Phone, SafetyGreen, "Made a phone call")
        "call_received" -> ActivityDisplay(Icons.Default.Phone, Color(0xFF10B981), "Received a phone call")
        "photo_taken" -> ActivityDisplay(Icons.Default.CameraAlt, Color(0xFFF59E0B), "Took a photo")
        "photo_shared" -> ActivityDisplay(Icons.Default.Share, Color(0xFFF97316), "Shared a photo")
        "web_link_opened", "WEB_SESSION" -> ActivityDisplay(
            Icons.Default.Language, ElectricViolet,
            "Opened a web link"
        )
        "app_blocked" -> ActivityDisplay(
            Icons.Default.Block, EmergencyRed,
            "Blocked: ${entry.appName ?: "unknown app"}"
        )
        "device_admin_revoked" -> ActivityDisplay(Icons.Default.Warning, EmergencyRed, "Tamper attempt detected")
        "lock_activated" -> ActivityDisplay(Icons.Default.Lock, EmergencyRed, "Device locked")
        "lock_deactivated" -> ActivityDisplay(Icons.Default.LockOpen, SafetyGreen, "Device unlocked")
        "check_in", "CHECK_IN" -> ActivityDisplay(Icons.Default.Share, Color(0xFF4A90E2), "Checked in with parent 📍")
        "token_request" -> ActivityDisplay(Icons.Default.Warning, Color(0xFFFF9800), "Requested more tokens")
        else -> ActivityDisplay(
            Icons.Default.AccessTime, Color(0xFF9999AA),
            entry.actionType.replace("_", " ").replaceFirstChar { it.uppercaseChar() }
        )
    }

internal fun formatTokens(n: Int): String = when {
    n >= 1000 -> "${n / 1000}.${(n % 1000) / 100}k"
    else -> n.toString()
}

private fun formatLastSeen(iso: String?): String {
    if (iso == null) return "Unknown"
    return try {
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
            diffMins < 1  -> "Just now"
            diffMins < 60 -> "${diffMins}m ago"
            diffHours < 24 -> "${diffHours}h ago"
            diffHours < 48 -> "Yesterday"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
        }
    } catch (e: Exception) { "Unknown" }
}

private fun formatTimestamp(iso: String): String {
    return try {
        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            .parse(iso.take(19)) ?: return iso
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
    } catch (e: Exception) { iso }
}
