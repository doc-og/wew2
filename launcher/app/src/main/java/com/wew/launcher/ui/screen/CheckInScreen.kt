package com.wew.launcher.ui.screen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wew.launcher.ui.theme.ElectricViolet
import com.wew.launcher.ui.theme.Night
import com.wew.launcher.ui.theme.OnNight
import com.wew.launcher.ui.theme.SafetyGreen
import com.wew.launcher.ui.theme.SurfaceVariant
import com.wew.launcher.ui.viewmodel.CheckInStep
import com.wew.launcher.ui.viewmodel.CheckInViewModel
import kotlin.math.roundToInt

@Composable
fun CheckInScreen(
    onClose: () -> Unit,
    viewModel: CheckInViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // System permission launcher — used when step == NEEDS_PERMISSION
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) viewModel.onPermissionGranted() else viewModel.onPermissionDenied()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        when (uiState.step) {
            CheckInStep.NEEDS_PERMISSION -> NeedsPermissionContent(
                onRequestPermission = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                onCancel = onClose
            )

            CheckInStep.GETTING_LOCATION -> GettingLocationContent()

            CheckInStep.CONFIRM -> ConfirmContent(
                latitude = uiState.latitude ?: 0.0,
                longitude = uiState.longitude ?: 0.0,
                accuracyMeters = uiState.accuracyMeters,
                message = uiState.message,
                onMessageChanged = viewModel::onMessageChanged,
                onCheckIn = { viewModel.onCheckIn(onClose) },
                onCancel = onClose
            )

            CheckInStep.SUBMITTING -> SubmittingContent()

            CheckInStep.SUCCESS -> SuccessContent()

            CheckInStep.ERROR -> ErrorContent(
                errorMessage = uiState.errorMessage ?: "Something went wrong.",
                onRetry = viewModel::onRetry,
                onCancel = onClose
            )
        }
    }
}

@Composable
private fun NeedsPermissionContent(
    onRequestPermission: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(ElectricViolet.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = ElectricViolet,
                modifier = Modifier.size(52.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "location access needed",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnNight,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "wew needs your location to send a check-in to your parent. Please allow location access when prompted.",
            fontSize = 15.sp,
            color = OnNight.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = ElectricViolet,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "  allow location",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onCancel) {
            Text(
                text = "cancel",
                fontSize = 16.sp,
                color = OnNight.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun GettingLocationContent() {
    val infiniteTransition = rememberInfiniteTransition(label = "locationPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .scale(scale)
                .alpha(alpha)
                .clip(CircleShape)
                .background(ElectricViolet.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = ElectricViolet,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "getting your location...",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = OnNight,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "this only takes a moment",
            fontSize = 14.sp,
            color = OnNight.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ConfirmContent(
    latitude: Double,
    longitude: Double,
    accuracyMeters: Float?,
    message: String,
    onMessageChanged: (String) -> Unit,
    onCheckIn: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Location confirmed icon
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(SafetyGreen.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = SafetyGreen,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "got your location \u2713",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = SafetyGreen
        )

        Spacer(modifier = Modifier.height(6.dp))

        val latStr = "%.5f".format(latitude)
        val lngStr = "%.5f".format(longitude)
        Text(
            text = "$latStr, $lngStr",
            fontSize = 13.sp,
            color = OnNight.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Normal
        )

        accuracyMeters?.let { acc ->
            Text(
                text = "accurate to ~${acc.roundToInt()} m",
                fontSize = 12.sp,
                color = OnNight.copy(alpha = 0.4f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "share your location with your parent?",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = OnNight,
            textAlign = TextAlign.Center,
            lineHeight = 28.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Optional note field
        OutlinedTextField(
            value = message,
            onValueChange = onMessageChanged,
            label = {
                Text(
                    text = "add a note (optional)",
                    color = OnNight.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            },
            placeholder = {
                Text(
                    text = "e.g. at school, at the park",
                    color = OnNight.copy(alpha = 0.35f),
                    fontSize = 14.sp
                )
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = OnNight,
                unfocusedTextColor = OnNight,
                focusedBorderColor = ElectricViolet,
                unfocusedBorderColor = OnNight.copy(alpha = 0.25f),
                cursorColor = ElectricViolet,
                focusedContainerColor = SurfaceVariant,
                unfocusedContainerColor = SurfaceVariant
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Big check in button
        Button(
            onClick = onCheckIn,
            colors = ButtonDefaults.buttonColors(
                containerColor = ElectricViolet,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "  check in",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onCancel) {
            Text(
                text = "cancel",
                fontSize = 16.sp,
                color = OnNight.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun SubmittingContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = ElectricViolet,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "sending to your parent...",
            fontSize = 18.sp,
            color = OnNight,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SuccessContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(SafetyGreen.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = SafetyGreen,
                modifier = Modifier.size(52.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "your parent has been notified \u2713",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = OnNight,
            textAlign = TextAlign.Center,
            lineHeight = 28.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "closing in a moment...",
            fontSize = 14.sp,
            color = OnNight.copy(alpha = 0.45f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErrorContent(
    errorMessage: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = OnNight.copy(alpha = 0.4f),
            modifier = Modifier.size(56.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "something went wrong",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = OnNight,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = errorMessage,
            fontSize = 14.sp,
            color = OnNight.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = ElectricViolet,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = "try again",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onCancel) {
            Text(
                text = "cancel",
                fontSize = 16.sp,
                color = OnNight.copy(alpha = 0.5f)
            )
        }
    }
}
