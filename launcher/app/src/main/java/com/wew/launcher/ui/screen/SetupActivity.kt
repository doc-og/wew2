package com.wew.launcher.ui.screen

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wew.launcher.service.WewDeviceAdminReceiver
import com.wew.launcher.ui.theme.BrandViolet
import com.wew.launcher.ui.theme.ElectricViolet
import com.wew.launcher.ui.theme.Night
import com.wew.launcher.ui.theme.OnNight
import com.wew.launcher.ui.theme.WewLauncherTheme

class SetupActivity : ComponentActivity() {

    private val adminComponent by lazy {
        ComponentName(this, WewDeviceAdminReceiver::class.java)
    }

    private val adminActivationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val prefs = getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
        if (result.resultCode == Activity.RESULT_OK) {
            prefs.edit().putBoolean("device_admin_active", true).apply()
        }
        // Either way, move to device-id entry step — activity will recompose via state
        // The SetupScreen composable tracks its own step internally
        setContent {
            WewLauncherTheme {
                SetupScreen(
                    onActivateAdmin = ::requestDeviceAdmin,
                    onSaveDeviceId = ::saveDeviceId,
                    onSkip = ::finishSetup,
                    adminAlreadyActive = true
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
        val adminActive = prefs.getBoolean("device_admin_active", false)
        setContent {
            WewLauncherTheme {
                SetupScreen(
                    onActivateAdmin = ::requestDeviceAdmin,
                    onSaveDeviceId = ::saveDeviceId,
                    onSkip = ::finishSetup,
                    adminAlreadyActive = adminActive
                )
            }
        }
    }

    private fun requestDeviceAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "wew needs device admin access to keep your phone safe and prevent unauthorized changes."
            )
        }
        adminActivationLauncher.launch(intent)
    }

    private fun saveDeviceId(deviceId: String) {
        val trimmed = deviceId.trim()
        if (trimmed.isNotEmpty()) {
            getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("device_id", trimmed)
                .apply()
        }
        finishSetup()
    }

    private fun finishSetup() {
        getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("setup_skipped", true)
            .apply()
        finish()
    }
}

@Composable
private fun SetupScreen(
    onActivateAdmin: () -> Unit,
    onSaveDeviceId: (String) -> Unit,
    onSkip: () -> Unit,
    adminAlreadyActive: Boolean
) {
    var step by remember { mutableStateOf(if (adminAlreadyActive) 1 else 0) }
    var deviceIdInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "wew",
            fontSize = 48.sp,
            fontWeight = FontWeight.Medium,
            color = ElectricViolet
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (step == 0) {
            // Step 0: Device Admin activation
            Text(
                text = "one more step to get started",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = OnNight,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "wew needs to be set as a device administrator to protect your settings and keep you safe. your parent has already set this up.",
                fontSize = 16.sp,
                color = OnNight.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = onActivateAdmin,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandViolet,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "activate and continue",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = { step = 1 }) {
                Text(
                    text = "skip for now",
                    color = OnNight.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            }
        } else {
            // Step 1: Enter device ID from parent app
            Text(
                text = "link to parent account",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = OnNight,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "enter the device ID shown in your parent's wew app to connect this device.",
                fontSize = 16.sp,
                color = OnNight.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = deviceIdInput,
                onValueChange = { deviceIdInput = it },
                placeholder = {
                    Text(
                        text = "device id",
                        color = OnNight.copy(alpha = 0.4f)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandViolet,
                    unfocusedBorderColor = OnNight.copy(alpha = 0.3f),
                    focusedTextColor = OnNight,
                    unfocusedTextColor = OnNight,
                    cursorColor = BrandViolet
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onSaveDeviceId(deviceIdInput) },
                enabled = deviceIdInput.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandViolet,
                    contentColor = Color.White,
                    disabledContainerColor = BrandViolet.copy(alpha = 0.4f),
                    disabledContentColor = Color.White.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "connect device",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onSkip) {
                Text(
                    text = "skip — i'll do this later",
                    color = OnNight.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            }
        }
    }
}
