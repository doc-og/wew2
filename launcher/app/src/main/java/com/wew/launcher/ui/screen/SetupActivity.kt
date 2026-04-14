package com.wew.launcher.ui.screen

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
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
import androidx.compose.runtime.mutableIntStateOf
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

    // Launcher for Device Admin activation (step 0)
    private val adminActivationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val prefs = getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
        if (result.resultCode == Activity.RESULT_OK) {
            prefs.edit().putBoolean("device_admin_active", true).apply()
        }
        recompose(adminAlreadyActive = true)
    }

    // Launcher for default SMS app request (step 2)
    private val defaultSmsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Whether approved or denied, move on — SMS is optional but recommended
        recompose(adminAlreadyActive = true, defaultSmsHandled = true)
    }

    private var currentAdminActive = false
    private var currentDefaultSmsHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
        currentAdminActive = prefs.getBoolean("device_admin_active", false)
        currentDefaultSmsHandled = prefs.getBoolean("default_sms_handled", false) ||
            isDefaultSmsApp()
        recompose(currentAdminActive, currentDefaultSmsHandled)
    }

    private fun recompose(adminAlreadyActive: Boolean, defaultSmsHandled: Boolean = currentDefaultSmsHandled) {
        currentAdminActive = adminAlreadyActive
        currentDefaultSmsHandled = defaultSmsHandled
        setContent {
            WewLauncherTheme {
                SetupScreen(
                    adminAlreadyActive = adminAlreadyActive,
                    defaultSmsAlreadySet = defaultSmsHandled,
                    onActivateAdmin = ::requestDeviceAdmin,
                    onRequestDefaultSms = ::requestDefaultSmsApp,
                    onSaveDeviceId = ::saveDeviceId,
                    onSkip = ::finishSetup
                )
            }
        }
    }

    private fun requestDeviceAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "wew needs device admin access to protect your settings and keep you safe."
            )
        }
        adminActivationLauncher.launch(intent)
    }

    private fun requestDefaultSmsApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                defaultSmsLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
            } else {
                // Already the default or unavailable — move on
                recompose(adminAlreadyActive = currentAdminActive, defaultSmsHandled = true)
            }
        } else {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            }
            defaultSmsLauncher.launch(intent)
        }
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
            .putBoolean("default_sms_handled", true)
            .apply()
        finish()
    }

    private fun isDefaultSmsApp(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager.isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            Telephony.Sms.getDefaultSmsPackage(this) == packageName
        }
    }
}

@Composable
private fun SetupScreen(
    adminAlreadyActive: Boolean,
    defaultSmsAlreadySet: Boolean,
    onActivateAdmin: () -> Unit,
    onRequestDefaultSms: () -> Unit,
    onSaveDeviceId: (String) -> Unit,
    onSkip: () -> Unit
) {
    // Steps: 0 = device admin, 1 = device id, 2 = default sms app
    val startStep = when {
        !adminAlreadyActive -> 0
        !defaultSmsAlreadySet -> 2
        else -> 1
    }
    var step by remember { mutableIntStateOf(startStep) }
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

        when (step) {

            // ── Step 0: Device Admin ──────────────────────────────────────────
            0 -> {
                SetupHeading("one more step to get started")
                Spacer(modifier = Modifier.height(24.dp))
                SetupBody(
                    "wew needs to be set as a device administrator to protect your settings and keep you safe. your parent has already set this up."
                )
                Spacer(modifier = Modifier.height(40.dp))
                SetupPrimaryButton("activate and continue", onClick = onActivateAdmin)
                Spacer(modifier = Modifier.height(16.dp))
                SetupSkipButton("skip for now") { step = 1 }
            }

            // ── Step 1: Link device ID ────────────────────────────────────────
            1 -> {
                SetupHeading("link to parent account")
                Spacer(modifier = Modifier.height(24.dp))
                SetupBody("enter the device ID shown in your parent's wew app to connect this device.")
                Spacer(modifier = Modifier.height(32.dp))
                OutlinedTextField(
                    value = deviceIdInput,
                    onValueChange = { deviceIdInput = it },
                    placeholder = { Text("device id", color = OnNight.copy(alpha = 0.4f)) },
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
                    onClick = {
                        onSaveDeviceId(deviceIdInput)
                        if (!defaultSmsAlreadySet) step = 2
                    },
                    enabled = deviceIdInput.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandViolet,
                        contentColor = Color.White,
                        disabledContainerColor = BrandViolet.copy(alpha = 0.4f),
                        disabledContentColor = Color.White.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("connect device", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(16.dp))
                SetupSkipButton("skip — i'll do this later", onClick = onSkip)
            }

            // ── Step 2: Default SMS app ───────────────────────────────────────
            2 -> {
                SetupHeading("set wew as your messaging app")
                Spacer(modifier = Modifier.height(24.dp))
                SetupBody(
                    "wew needs to be your default messaging app so you can send and receive texts right here in the app. your conversations stay private on your device."
                )
                Spacer(modifier = Modifier.height(40.dp))
                SetupPrimaryButton("set as default", onClick = onRequestDefaultSms)
                Spacer(modifier = Modifier.height(16.dp))
                SetupSkipButton("skip — i'll do this later", onClick = onSkip)
            }
        }
    }
}

@Composable
private fun SetupHeading(text: String) {
    Text(
        text = text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Medium,
        color = OnNight,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun SetupBody(text: String) {
    Text(
        text = text,
        fontSize = 16.sp,
        color = OnNight.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
        lineHeight = 24.sp
    )
}

@Composable
private fun SetupPrimaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = BrandViolet,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SetupSkipButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, color = OnNight.copy(alpha = 0.5f), fontSize = 14.sp)
    }
}
