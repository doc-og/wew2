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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wew.launcher.data.repository.DeviceRepository
import com.wew.launcher.ui.theme.BrandViolet
import com.wew.launcher.ui.theme.ElectricViolet
import com.wew.launcher.ui.theme.Night
import com.wew.launcher.ui.theme.OnNight
import kotlinx.coroutines.launch

/**
 * Full-screen lock when schedule rules block phone use. Parent PIN is required to unlock;
 * there is no bypass if no PIN is configured on the device.
 */
@Composable
fun ScheduleLockScreen(
    unlockTime: String,
    deviceId: String,
    onPasscodeUnlock: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var passcodeConfigured by remember { mutableStateOf<Boolean?>(null) }
    var showPasscode by remember { mutableStateOf(false) }
    var attemptsLeft by remember { mutableIntStateOf(3) }

    LaunchedEffect(deviceId) {
        if (deviceId.isBlank()) {
            passcodeConfigured = false
            return@LaunchedEffect
        }
        val record = runCatching {
            DeviceRepository(context).getDevicePasscode(deviceId)
        }.getOrNull()
        passcodeConfigured = record != null
        showPasscode = record != null
    }

    if (passcodeConfigured == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Night),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = BrandViolet)
        }
        return
    }

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
                text = when {
                    unlockTime.isNotBlank() -> "available again at $unlockTime"
                    else -> "outside allowed hours — enter your parent’s code to use the phone"
                },
                fontSize = 16.sp,
                color = OnNight.copy(alpha = 0.55f),
                textAlign = TextAlign.Center
            )

            if (passcodeConfigured == true && !showPasscode) {
                Spacer(Modifier.height(32.dp))
                androidx.compose.material3.TextButton(onClick = { showPasscode = true }) {
                    Text(
                        "enter parent code",
                        color = ElectricViolet,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (passcodeConfigured == false) {
                Spacer(Modifier.height(28.dp))
                Text(
                    text = "your parent must set a child passcode in WeW Parent → Settings → Child passcode before you can unlock here.",
                    fontSize = 14.sp,
                    color = OnNight.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }
    }

    if (showPasscode && passcodeConfigured == true) {
        PasscodeDialog(
            appName = "parent code",
            attemptsLeft = attemptsLeft,
            onPinSubmit = { pin ->
                scope.launch {
                    val repo = DeviceRepository(context)
                    val record = repo.getDevicePasscode(deviceId) ?: return@launch
                    val input = deviceId + pin
                    val bytes = java.security.MessageDigest.getInstance("SHA-256")
                        .digest(input.toByteArray(Charsets.UTF_8))
                    val hash = bytes.joinToString("") { "%02x".format(it) }
                    if (hash == record.passcodeHash) {
                        showPasscode = false
                        onPasscodeUnlock()
                    } else {
                        val remaining = attemptsLeft - 1
                        if (remaining <= 0) {
                            showPasscode = false
                            attemptsLeft = 3
                        } else {
                            attemptsLeft = remaining
                        }
                    }
                }
            },
            onDismiss = {
                showPasscode = false
                attemptsLeft = 3
            }
        )
    }
}
