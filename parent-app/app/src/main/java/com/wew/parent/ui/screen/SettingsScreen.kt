package com.wew.parent.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.wew.parent.data.model.NotificationConfig
import com.wew.parent.data.repository.ParentRepository
import com.wew.parent.ui.theme.BrandViolet
import com.wew.parent.ui.theme.ParentBackground
import com.wew.parent.ui.theme.SafetyGreen
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(userId: String, deviceId: String) {
    val repo = remember { ParentRepository() }
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf<NotificationConfig?>(null) }
    var lowCreditThreshold by remember { mutableFloatStateOf(20f) }
    var dailySummaryEnabled by remember { mutableStateOf(true) }
    var notifyBlockedApps by remember { mutableStateOf(true) }
    var notifyTamperAttempts by remember { mutableStateOf(true) }

    LaunchedEffect(userId) {
        config = repo.getNotificationConfig(userId)
        config?.let {
            lowCreditThreshold = it.lowCreditThresholdPct.toFloat()
            dailySummaryEnabled = it.dailySummaryEnabled
            notifyBlockedApps = it.notifyBlockedApps
            notifyTamperAttempts = it.notifyTamperAttempts
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ParentBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text("settings", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1A1A2E))
        Spacer(Modifier.height(16.dp))

        SectionCard(title = "notifications") {
            SettingRow(
                label = "low credit alerts",
                subtitle = "alert when credits drop below ${lowCreditThreshold.toInt()}%",
                trailing = {
                    Slider(
                        value = lowCreditThreshold,
                        onValueChange = { lowCreditThreshold = it },
                        onValueChangeFinished = {
                            scope.launch {
                                config?.let { c ->
                                    repo.upsertNotificationConfig(c.copy(lowCreditThresholdPct = lowCreditThreshold.toInt()))
                                }
                            }
                        },
                        valueRange = 5f..50f,
                        colors = SliderDefaults.colors(thumbColor = BrandViolet, activeTrackColor = BrandViolet),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
            SettingToggleRow(
                label = "daily summary",
                subtitle = "receive a summary at 8 PM each day",
                checked = dailySummaryEnabled,
                onCheckedChange = {
                    dailySummaryEnabled = it
                    scope.launch {
                        config?.let { c -> repo.upsertNotificationConfig(c.copy(dailySummaryEnabled = it)) }
                    }
                }
            )
            SettingToggleRow(
                label = "blocked app attempts",
                subtitle = "alert when child tries to open an unapproved app",
                checked = notifyBlockedApps,
                onCheckedChange = {
                    notifyBlockedApps = it
                    scope.launch {
                        config?.let { c -> repo.upsertNotificationConfig(c.copy(notifyBlockedApps = it)) }
                    }
                }
            )
            SettingToggleRow(
                label = "tamper alerts",
                subtitle = "alert on device admin or security events",
                checked = notifyTamperAttempts,
                onCheckedChange = {
                    notifyTamperAttempts = it
                    scope.launch {
                        config?.let { c -> repo.upsertNotificationConfig(c.copy(notifyTamperAttempts = it)) }
                    }
                }
            )
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, fontSize = 13.sp, color = Color(0xFF3D3D5C), fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content()
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, color = Color(0xFF1A1A2E))
            Text(subtitle, fontSize = 12.sp, color = Color(0xFF3D3D5C))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = SafetyGreen, checkedTrackColor = SafetyGreen.copy(alpha = 0.3f))
        )
    }
}

@Composable
private fun SettingRow(label: String, subtitle: String, trailing: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 15.sp, color = Color(0xFF1A1A2E))
        Text(subtitle, fontSize = 12.sp, color = Color(0xFF3D3D5C))
        trailing()
    }
}
