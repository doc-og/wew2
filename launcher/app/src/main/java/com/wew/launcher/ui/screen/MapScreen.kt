package com.wew.launcher.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wew.launcher.ui.theme.BrandViolet
import com.wew.launcher.ui.theme.Night
import com.wew.launcher.ui.theme.OnNight
import com.wew.launcher.ui.theme.WarningAmber
import com.wew.launcher.ui.viewmodel.MapViewModel
import com.wew.launcher.ui.viewmodel.ShareStatus

@Composable
fun MapScreen(onBack: () -> Unit) {
    val vm: MapViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current

    BackHandler { onBack() }

    // Fire native Maps intent when ViewModel sets pendingNavigationUri
    LaunchedEffect(state.pendingNavigationUri) {
        state.pendingNavigationUri?.let { uri ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            // Try Maps first, fall back to any browser
            runCatching { context.startActivity(intent) }
            vm.clearPendingNavigation()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = OnNight
                )
            }
            Text(
                text = "Map",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = OnNight,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
            // Refresh location
            IconButton(onClick = vm::locateDevice) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = "Refresh location",
                    tint = BrandViolet
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Current location card ─────────────────────────────────────────
            LocationCard(
                address = state.currentAddress,
                isLocating = state.isLocating
            )

            // ── Share location ────────────────────────────────────────────────
            ShareLocationButton(
                shareStatus = state.shareStatus,
                tokensExhausted = state.tokensExhausted,
                onShare = vm::shareLocation
            )

            // ── Divider ───────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(OnNight.copy(alpha = 0.08f))
            )

            // ── Directions ────────────────────────────────────────────────────
            Text(
                text = "Get directions",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnNight.copy(alpha = 0.5f),
                letterSpacing = 0.8.sp
            )

            OutlinedTextField(
                value = state.destination,
                onValueChange = vm::onDestinationChange,
                placeholder = {
                    Text("Where to?", color = OnNight.copy(alpha = 0.35f))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = OnNight,
                    unfocusedTextColor = OnNight,
                    focusedBorderColor = BrandViolet,
                    unfocusedBorderColor = OnNight.copy(alpha = 0.2f),
                    cursorColor = BrandViolet
                ),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = {
                    keyboard?.hide()
                    vm.getDirections()
                }),
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = null,
                        tint = BrandViolet,
                        modifier = Modifier.size(20.dp)
                    )
                }
            )

            Button(
                onClick = {
                    keyboard?.hide()
                    vm.getDirections()
                },
                enabled = state.destination.isNotBlank() && !state.isLocating,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandViolet,
                    disabledContainerColor = BrandViolet.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Open in Maps", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }

            // Tokens note
            if (!state.tokensExhausted) {
                Text(
                    text = "Sharing your location costs 10 tokens",
                    fontSize = 12.sp,
                    color = OnNight.copy(alpha = 0.35f)
                )
            } else {
                Text(
                    text = "No tokens left — can't share location",
                    fontSize = 12.sp,
                    color = WarningAmber
                )
            }
        }
    }
}

// ── Current location card ─────────────────────────────────────────────────────

@Composable
private fun LocationCard(address: String, isLocating: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A2E), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(BrandViolet.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isLocating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = BrandViolet,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = BrandViolet,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column {
            Text(
                text = "You are here",
                fontSize = 12.sp,
                color = OnNight.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = address,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = OnNight,
                lineHeight = 20.sp
            )
        }
    }
}

// ── Share location button ─────────────────────────────────────────────────────

@Composable
private fun ShareLocationButton(
    shareStatus: ShareStatus,
    tokensExhausted: Boolean,
    onShare: () -> Unit
) {
    AnimatedContent(
        targetState = shareStatus,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "share_status"
    ) { status ->
        when (status) {
            ShareStatus.IDLE -> {
                Button(
                    onClick = onShare,
                    enabled = !tokensExhausted,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1A1A2E),
                        contentColor = BrandViolet,
                        disabledContainerColor = Color(0xFF1A1A2E).copy(alpha = 0.5f),
                        disabledContentColor = BrandViolet.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Share location with parent", fontSize = 15.sp)
                }
            }

            ShareStatus.SHARING -> {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = Color(0xFF1A1A2E).copy(alpha = 0.5f),
                        disabledContentColor = BrandViolet.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = BrandViolet,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Sharing…", fontSize = 15.sp)
                }
            }

            ShareStatus.SHARED -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFF1A3A2A),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Location shared with parent",
                        fontSize = 15.sp,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            ShareStatus.FAILED -> {
                Button(
                    onClick = onShare,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WarningAmber.copy(alpha = 0.15f),
                        contentColor = WarningAmber
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Failed — tap to retry", fontSize = 15.sp)
                }
            }
        }
    }
}
