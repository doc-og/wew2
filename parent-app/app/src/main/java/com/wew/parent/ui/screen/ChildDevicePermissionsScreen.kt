package com.wew.parent.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wew.parent.data.model.Device
import com.wew.parent.data.repository.ParentRepository
import com.wew.parent.ui.theme.BrandViolet
import com.wew.parent.ui.theme.ParentBackground
import com.wew.parent.ui.theme.SafetyGreen
import kotlinx.coroutines.launch

/**
 * Shows messaging-related capability flags synced from the child launcher (not the parent phone).
 * Matches common “Settings → Apps → permissions” style: list rows, status on the right, help text below.
 */
@Composable
fun ChildDevicePermissionsScreen(
    deviceId: String,
    onBack: () -> Unit
) {
    val repo = remember { ParentRepository() }
    val scope = rememberCoroutineScope()
    var device by remember { mutableStateOf<Device?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }

    suspend fun reload() {
        device = repo.getDevice(deviceId)
    }

    LaunchedEffect(deviceId) {
        loading = true
        runCatching { reload() }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ParentBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Child phone permissions",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1A1A2E),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    scope.launch {
                        refreshing = true
                        runCatching { reload() }
                        refreshing = false
                    }
                },
                enabled = !refreshing && !loading
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(8.dp),
                        color = BrandViolet,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        }

        HorizontalDivider(color = Color(0xFFE8E8F0))

        when {
            loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = BrandViolet)
                }
            }
            device == null -> {
                Text(
                    "Couldn't load this device.",
                    modifier = Modifier.padding(24.dp),
                    color = Color(0xFF6B6B8A)
                )
            }
            else -> {
                val d = device!!
                val reported = d.childMessagingHealthAt != null
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        "These apply to your child's WeW phone, not this parent app. " +
                            "The child device reports status when WeW is open or running in the background.",
                        fontSize = 13.sp,
                        color = Color(0xFF6B6B8A),
                        lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(16.dp))

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            MessagingPermissionRow(
                                title = "Default SMS app",
                                description = "Android only allows one messaging app. WeW must be selected so texts send and arrive in the app.",
                                granted = d.childDefaultSmsApp,
                                reported = reported,
                                leadingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Message,
                                        contentDescription = null,
                                        tint = BrandViolet
                                    )
                                }
                            )
                            HorizontalDivider(color = Color(0xFFF0F0F5))
                            MessagingPermissionRow(
                                title = "SMS access",
                                description = "Read, receive, and send SMS — required alongside the default app role.",
                                granted = d.childSmsPermissionsOk,
                                reported = reported,
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Security,
                                        contentDescription = null,
                                        tint = BrandViolet
                                    )
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        "If something shows “Action needed”",
                        fontSize = 11.sp,
                        color = Color(0xFF9999AA),
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "On your child's phone, open WeW. Use the permission screen to allow SMS and set WeW as the default messaging app. " +
                            "You can also use Settings → Apps → WeW → Permissions.",
                        fontSize = 13.sp,
                        color = Color(0xFF6B6B8A),
                        lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        lastUpdatedLabel(d),
                        fontSize = 12.sp,
                        color = Color(0xFF9999AA)
                    )
                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }
}

@Composable
private fun MessagingPermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    reported: Boolean,
    leadingIcon: @Composable () -> Unit
) {
    val (statusText, statusColor) = when {
        !reported -> "Waiting for device" to Color(0xFF9999AA)
        granted -> "Granted" to SafetyGreen
        else -> "Action needed" to Color(0xFFC45C26)
    }
    ListItem(
        headlineContent = {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1A1A2E))
        },
        supportingContent = {
            Text(
                description,
                fontSize = 13.sp,
                color = Color(0xFF6B6B8A),
                lineHeight = 18.sp
            )
        },
        leadingContent = leadingIcon,
        trailingContent = {
            Text(
                statusText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = statusColor
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.White)
    )
}

private fun lastUpdatedLabel(d: Device): String {
    val raw = d.childMessagingHealthAt
    if (raw.isNullOrBlank()) return "Last updated: not reported yet (open WeW on the child's phone)."
    return "Last updated: $raw"
}
