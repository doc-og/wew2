package com.wew.parent.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wew.parent.data.repository.ParentRepository
import com.wew.parent.ui.theme.BrandViolet
import com.wew.parent.ui.theme.ElectricViolet
import com.wew.parent.ui.theme.ParentBackground
import com.wew.parent.util.toUserMessage
import kotlinx.coroutines.launch

@Composable
fun RegisterDeviceScreen(
    setupMode: Boolean = false,
    onDeviceRegistered: (deviceId: String) -> Unit
) {
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
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(64.dp))

        // Brand
        Text(
            text = "WeW",
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold,
            color = BrandViolet,
            letterSpacing = (-1).sp
        )
        Text(
            text = "Parent",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = ElectricViolet.copy(alpha = 0.75f),
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(48.dp))

        if (registeredDeviceId == null) {
            // Step 1: name the device
            Text(
                text = "Register child's device",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1A1A2E)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Give the child's device a name so you can recognise it in the dashboard.",
                fontSize = 14.sp,
                color = Color(0xFF6B6B8A),
                textAlign = TextAlign.Center,
                lineHeight = 21.sp
            )

            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = { Text("Device name (e.g. Sam's Tablet)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = errorMessage!!,
                    fontSize = 13.sp,
                    color = Color(0xFFC0392B),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        runCatching { repo.registerDevice(deviceName.trim()) }
                            .onSuccess { device ->
                                if (setupMode) {
                                    // Return device ID directly to the launcher — skip copy screen
                                    onDeviceRegistered(device.id)
                                } else {
                                    registeredDeviceId = device.id
                                }
                            }
                            .onFailure { e ->
                                errorMessage = e.toUserMessage("Couldn't register device — please try again")
                            }
                        isLoading = false
                    }
                },
                enabled = deviceName.isNotBlank() && !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandViolet,
                    disabledContainerColor = BrandViolet.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text(
                        "Register Device",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            // Step 2: show the device ID to copy into the launcher
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF27AE60),
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Device registered!",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1A1A2E)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Enter this device ID on the child's launcher when prompted to link the devices.",
                fontSize = 14.sp,
                color = Color(0xFF6B6B8A),
                textAlign = TextAlign.Center,
                lineHeight = 21.sp
            )

            Spacer(Modifier.height(32.dp))

            // Device ID display box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .border(1.5.dp, BrandViolet.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "DEVICE ID",
                    fontSize = 11.sp,
                    color = Color(0xFF9999AA),
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = registeredDeviceId!!,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF1A1A2E),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(registeredDeviceId!!))
                    copied = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (copied) Color(0xFF27AE60) else BrandViolet
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(
                    if (copied) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = if (copied) "Copied!" else "Copy Device ID",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { onDeviceRegistered(registeredDeviceId!!) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(
                    "Continue to Dashboard",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(64.dp))
    }
}
