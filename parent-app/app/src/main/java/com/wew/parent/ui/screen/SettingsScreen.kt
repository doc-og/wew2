package com.wew.parent.ui.screen

import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wew.parent.data.model.Device
import com.wew.parent.data.model.NotificationConfig
import com.wew.parent.data.repository.ParentRepository
import com.wew.parent.ui.theme.BrandViolet
import com.wew.parent.ui.theme.ParentBackground
import com.wew.parent.ui.theme.SafetyGreen
import kotlinx.coroutines.launch

// Timeout options: value in minutes (0 = Off)
private val timeoutOptions = listOf(
    0 to "Off",
    1 to "1 min",
    2 to "2 min",
    5 to "5 min"
)

@Composable
fun SettingsScreen(userId: String, deviceId: String) {
    val repo = remember { ParentRepository() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("wew_parent_prefs", Context.MODE_PRIVATE) }

    var config by remember { mutableStateOf<NotificationConfig?>(null) }
    var lowCreditThreshold by remember { mutableFloatStateOf(20f) }
    var dailySummaryEnabled by remember { mutableStateOf(true) }
    var notifyBlockedApps by remember { mutableStateOf(true) }
    var notifyTamperAttempts by remember { mutableStateOf(true) }
    var selectedTimeoutMins by remember { mutableIntStateOf(prefs.getInt("auto_logout_timeout_mins", 2)) }
    var hasPasscode by remember { mutableStateOf(false) }
    var showPinEntry by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }

    var deviceRow by remember { mutableStateOf<Device?>(null) }
    var parentPhoneInput by remember { mutableStateOf("") }
    var parentNameInput by remember { mutableStateOf("") }
    var timezoneInput by remember { mutableStateOf("America/Los_Angeles") }
    var parentContactSaved by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        config = repo.getNotificationConfig(userId)
        config?.let {
            lowCreditThreshold = it.lowCreditThresholdPct.toFloat()
            dailySummaryEnabled = it.dailySummaryEnabled
            notifyBlockedApps = it.notifyBlockedApps
            notifyTamperAttempts = it.notifyTamperAttempts
        }
        hasPasscode = repo.getPasscode(deviceId) != null
    }

    LaunchedEffect(deviceId) {
        val d = repo.getDevice(deviceId)
        deviceRow = d
        parentPhoneInput = d?.parentPhone.orEmpty()
        parentNameInput = d?.parentDisplayName.orEmpty()
        timezoneInput = d?.timezone ?: "America/Los_Angeles"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ParentBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "Settings",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A2E)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Manage notifications and security",
            fontSize = 14.sp,
            color = Color(0xFF6B6B8A)
        )
        Spacer(Modifier.height(20.dp))

        // Security section
        SectionCard(title = "Security") {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Auto-logout timeout
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Auto-logout timeout",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1A1A2E)
                    )
                    Text(
                        "Sign out after this period of inactivity or when the app closes",
                        fontSize = 12.sp,
                        color = Color(0xFF6B6B8A),
                        lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    TimeoutPicker(
                        selectedMins = selectedTimeoutMins,
                        onSelect = { mins ->
                            selectedTimeoutMins = mins
                            prefs.edit().putInt("auto_logout_timeout_mins", mins).apply()
                        }
                    )
                }

                // Child passcode
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Child passcode",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1A1A2E)
                    )
                    Text(
                        "Lets your child unlock any app for a limited time",
                        fontSize = 12.sp,
                        color = Color(0xFF6B6B8A),
                        lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    if (showPinEntry) {
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { if (it.length <= 4) pinInput = it },
                            label = { Text("4-digit PIN") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (pinInput.length == 4) {
                                        scope.launch {
                                            repo.setPasscode(deviceId, hashPin(deviceId, pinInput))
                                            hasPasscode = true
                                            showPinEntry = false
                                            pinInput = ""
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandViolet)
                            ) { Text("Save", color = Color.White) }
                            TextButton(onClick = { showPinEntry = false; pinInput = "" }) {
                                Text("Cancel", color = Color(0xFF6B6B8A))
                            }
                        }
                    } else {
                        if (!hasPasscode) {
                            Button(
                                onClick = { showPinEntry = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandViolet)
                            ) { Text("Set passcode", color = Color.White) }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { showPinEntry = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandViolet)
                                ) { Text("Change passcode", color = Color.White) }
                                TextButton(onClick = {
                                    scope.launch { repo.removePasscode(deviceId); hasPasscode = false }
                                }) { Text("Remove", color = Color(0xFF6B6B8A)) }
                            }
                        }
                    }
                }
            }
        }

        // How your child reaches you (SOS + WeW Parent thread)
        SectionCard(title = "Your contact on child phone") {
            Text(
                "Add the number your child should call in an emergency. It also labels the parent chat.",
                fontSize = 12.sp,
                color = Color(0xFF6B6B8A),
                lineHeight = 17.sp
            )
            OutlinedTextField(
                value = parentPhoneInput,
                onValueChange = { parentPhoneInput = it },
                label = { Text("Your mobile number") },
                placeholder = { Text("+1…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = parentNameInput,
                onValueChange = { parentNameInput = it },
                label = { Text("How your name appears") },
                placeholder = { Text("Mom") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = timezoneInput,
                onValueChange = { timezoneInput = it },
                label = { Text("Home timezone (IANA)") },
                placeholder = { Text("America/Los_Angeles") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            parentContactSaved?.let { msg ->
                Text(msg, fontSize = 13.sp, color = SafetyGreen)
            }
            Button(
                onClick = {
                    scope.launch {
                        runCatching {
                            repo.updateDeviceParentContact(
                                deviceId = deviceId,
                                parentPhone = parentPhoneInput,
                                parentDisplayName = parentNameInput,
                                timezone = timezoneInput
                            )
                            parentContactSaved = "Saved"
                        }.onFailure { parentContactSaved = it.message ?: "Could not save" }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandViolet),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save contact info")
            }
        }

        // Notifications section
        SectionCard(title = "Notifications") {
            SettingRow(
                label = "Low credit alerts",
                subtitle = "Alert when credits drop below ${lowCreditThreshold.toInt()}%",
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
                label = "Daily summary",
                subtitle = "Receive a summary at 8 PM each day",
                checked = dailySummaryEnabled,
                onCheckedChange = {
                    dailySummaryEnabled = it
                    scope.launch {
                        config?.let { c -> repo.upsertNotificationConfig(c.copy(dailySummaryEnabled = it)) }
                    }
                }
            )
            SettingToggleRow(
                label = "Blocked app attempts",
                subtitle = "Alert when child tries to open an unapproved app",
                checked = notifyBlockedApps,
                onCheckedChange = {
                    notifyBlockedApps = it
                    scope.launch {
                        config?.let { c -> repo.upsertNotificationConfig(c.copy(notifyBlockedApps = it)) }
                    }
                }
            )
            SettingToggleRow(
                label = "Tamper alerts",
                subtitle = "Alert on device admin or security events",
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
private fun TimeoutPicker(selectedMins: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        timeoutOptions.forEach { (mins, label) ->
            val isSelected = selectedMins == mins
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) BrandViolet else Color.White)
                    .border(
                        width = 1.5.dp,
                        color = if (isSelected) BrandViolet else Color(0xFFDDDDEE),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelect(mins) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Color.White else Color(0xFF3D3D5C),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title.uppercase(),
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
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                content()
            }
        }
        Spacer(Modifier.height(20.dp))
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
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1A1A2E))
            Text(subtitle, fontSize = 12.sp, color = Color(0xFF6B6B8A), lineHeight = 17.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SafetyGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFCCCCDD)
            )
        )
    }
}

@Composable
private fun SettingRow(label: String, subtitle: String, trailing: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1A1A2E))
        Text(subtitle, fontSize = 12.sp, color = Color(0xFF6B6B8A))
        trailing()
    }
}

private fun hashPin(deviceId: String, pin: String): String {
    val input = "$deviceId$pin"
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
}
