package com.wew.parent.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wew.parent.data.repository.ParentRepository
import com.wew.parent.ui.theme.BrandViolet
import com.wew.parent.ui.theme.ElectricViolet
import com.wew.parent.ui.theme.ParentBackground
import com.wew.parent.util.toUserMessage
import kotlinx.coroutines.launch

@Composable
fun RegisterDeviceScreen(onDeviceRegistered: () -> Unit) {
    val repo = remember { ParentRepository() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var deviceName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var registeredDeviceId by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ParentBackground)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "wew",
            fontSize = 48.sp,
            fontWeight = FontWeight.Medium,
            color = BrandViolet
        )
        Text(
            text = "safe by design",
            fontSize = 14.sp,
            color = ElectricViolet.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(48.dp))

        if (registeredDeviceId == null) {
            // Step 1: name the device
            Text(
                text = "register child's device",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1A1A2E)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "give the child's device a name so you can recognise it in the dashboard.",
                fontSize = 15.sp,
                color = Color(0xFF3D3D5C),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = { Text("device name (e.g. Sam's Tablet)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = errorMessage!!, fontSize = 14.sp, color = Color(0xFFC0392B))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        runCatching { repo.registerDevice(deviceName.trim()) }
                            .onSuccess { device -> registeredDeviceId = device.id }
                            .onFailure { e -> errorMessage = e.toUserMessage("couldn't register device — please try again") }
                        isLoading = false
                    }
                },
                enabled = deviceName.isNotBlank() && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = BrandViolet),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.height(20.dp))
                } else {
                    Text("register device", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        } else {
            // Step 2: show the device ID to copy into the launcher
            Text(
                text = "device registered!",
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1A1A2E)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "enter this device ID on the child's launcher when prompted to link the devices.",
                fontSize = 15.sp,
                color = Color(0xFF3D3D5C),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Device ID display box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, BrandViolet.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "device id",
                    fontSize = 12.sp,
                    color = Color(0xFF3D3D5C),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = registeredDeviceId!!,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF1A1A2E),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(registeredDeviceId!!))
                    copied = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (copied) Color(0xFF27AE60) else BrandViolet
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(
                    text = if (copied) "copied!" else "copy device id",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDeviceRegistered,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("continue to dashboard", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
