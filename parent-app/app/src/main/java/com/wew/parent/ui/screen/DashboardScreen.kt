package com.wew.parent.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wew.parent.data.model.ActivityLogEntry
import com.wew.parent.ui.theme.BrandViolet
import com.wew.parent.ui.theme.ElectricViolet
import com.wew.parent.ui.theme.EmergencyRed
import com.wew.parent.ui.theme.ParentBackground
import com.wew.parent.ui.theme.SafetyGreen
import com.wew.parent.ui.theme.SafetyTint
import com.wew.parent.ui.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    onAddCredits: () -> Unit,
    onRemoveCredits: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = BrandViolet)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ParentBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        // Header
        item {
            Text(
                text = "dashboard",
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1A1A2E)
            )
            state.device?.let {
                Text(
                    text = it.deviceName,
                    fontSize = 14.sp,
                    color = Color(0xFF3D3D5C)
                )
            }
        }

        // Metric cards row
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MetricCard(
                    label = "credits remaining",
                    value = state.device?.currentCredits?.toString() ?: "—",
                    modifier = Modifier.weight(1f),
                    valueColor = when {
                        (state.device?.currentCredits ?: 100) <= 0 -> EmergencyRed
                        (state.device?.currentCredits ?: 100) < 20 -> Color(0xFFF59E0B)
                        else -> BrandViolet
                    }
                )
                MetricCard(
                    label = "daily budget",
                    value = state.device?.dailyCreditBudget?.toString() ?: "—",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Lock toggle
                val isLocked = state.device?.isLocked ?: false
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isLocked) EmergencyRed.copy(alpha = 0.1f) else SafetyTint
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("phone status", fontSize = 14.sp, color = Color(0xFF3D3D5C))
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = if (isLocked) "locked" else "unlocked",
                                tint = if (isLocked) EmergencyRed else SafetyGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isLocked) "locked" else "active",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isLocked) EmergencyRed else SafetyGreen
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.remoteLock(!isLocked) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isLocked) "unlock remotely" else "lock remotely",
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Credit controls
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ElectricViolet.copy(alpha = 0.08f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("adjust credits", fontSize = 14.sp, color = Color(0xFF3D3D5C))
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onAddCredits,
                                colors = ButtonDefaults.buttonColors(containerColor = BrandViolet),
                                modifier = Modifier.weight(1f),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "add credits", modifier = Modifier.size(16.dp))
                                Text(" add", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = onRemoveCredits,
                                modifier = Modifier.weight(1f),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "remove credits", modifier = Modifier.size(16.dp))
                                Text(" remove", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Activity feed
        item {
            Text(
                "live activity",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1A1A2E)
            )
        }

        if (state.activityFeed.isEmpty()) {
            item {
                Text(
                    "no activity yet today",
                    fontSize = 14.sp,
                    color = Color(0xFF3D3D5C),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            items(state.activityFeed) { entry ->
                ActivityFeedItem(entry)
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = BrandViolet
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = modifier.semantics { contentDescription = "$label: $value" }
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, fontSize = 14.sp, color = Color(0xFF3D3D5C))
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = valueColor
            )
        }
    }
}

@Composable
private fun ActivityFeedItem(entry: ActivityLogEntry) {
    val actionLabel = when (entry.actionType) {
        "app_open" -> "opened ${entry.appName ?: entry.appPackage ?: "an app"}"
        "message_sent" -> "sent a message via ${entry.appName ?: "messaging"}"
        "call_made" -> "made a phone call"
        "call_received" -> "received a phone call"
        "photo_taken" -> "took a photo"
        "photo_shared" -> "shared a photo"
        "web_link_opened" -> "opened a web link"
        "app_blocked" -> "tried to open ${entry.appName ?: "a blocked app"}"
        "device_admin_revoked" -> "tried to disable wew — emergency mode activated"
        "lock_activated" -> "phone locked by schedule"
        "lock_deactivated" -> "phone unlocked by schedule"
        else -> entry.actionType.replace("_", " ")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(actionLabel, fontSize = 14.sp, color = Color(0xFF1A1A2E))
            Text(
                formatTimestamp(entry.createdAt),
                fontSize = 12.sp,
                color = Color(0xFF3D3D5C)
            )
        }
        if (entry.creditsDeducted > 0) {
            Text(
                "-${entry.creditsDeducted}",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = BrandViolet,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun formatTimestamp(iso: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = parser.parse(iso.take(19)) ?: return iso
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
    } catch (e: Exception) {
        iso
    }
}
