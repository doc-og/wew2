package com.wew.launcher.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wew.launcher.data.model.AppInfo
import com.wew.launcher.ui.theme.EmergencyRed
import com.wew.launcher.ui.theme.Night
import com.wew.launcher.ui.theme.OnNight
import com.wew.launcher.ui.theme.SurfaceVariant
import com.wew.launcher.ui.theme.WarningAmber
import com.wew.launcher.ui.theme.ElectricViolet
import com.wew.launcher.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onSosClick: () -> Unit,
    onAppClick: (AppInfo) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show access denied snackbar
    LaunchedEffect(uiState.showAccessDeniedSnackbar) {
        if (uiState.showAccessDeniedSnackbar) {
            snackbarHostState.showSnackbar("access denied — ask your parent")
            viewModel.onAccessDeniedSnackbarShown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar: clock + token counter
            TopBar(
                tokens = uiState.currentTokens,
                dailyBudget = uiState.dailyTokenBudget
            )

            Spacer(modifier = Modifier.height(16.dp))

            // App grid (scrollable) — SOS is the last tile in the grid
            if (uiState.tokensExhausted) {
                // Credits out — show emergency-only overlay
                CreditsExhaustedMessage(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else {
                AppGrid(
                    apps = uiState.apps,
                    appsWithNotifications = uiState.appsWithNotifications,
                    onAppClick = { app ->
                        viewModel.onAppClicked(app)
                        onAppClick(app)
                    },
                    onSosClick = onSosClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Dim overlay when credits are exhausted (except SOS area)
        if (uiState.tokensExhausted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0f) // just blocks touches on non-emergency areas
                    .background(Night.copy(alpha = 0.6f))
            )
        }

        // Snackbar for access denied
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
        )
    }

    // Passcode dialog
    if (uiState.showPasscodeDialog && uiState.pendingUnauthorizedApp != null) {
        PasscodeDialog(
            appName = uiState.pendingUnauthorizedApp!!.appName,
            attemptsLeft = uiState.passcodeAttemptsLeft,
            onPinSubmit = { pin -> viewModel.onPasscodeSubmitted(pin) },
            onDismiss = { viewModel.onPasscodeDismissed() }
        )
    }

    // Time selection dialog
    if (uiState.showTimeSelectionDialog && uiState.pendingUnauthorizedApp != null) {
        TimeSelectionDialog(
            appName = uiState.pendingUnauthorizedApp!!.appName,
            onTimeSelected = { minutes -> viewModel.onTimeSelected(minutes) },
            onDismiss = { viewModel.onPasscodeDismissed() }
        )
    }
}

@Composable
private fun TopBar(tokens: Int, dailyBudget: Int) {
    val lowCredits = tokens < (dailyBudget * 0.2f)
    val creditColor = if (lowCredits) WarningAmber else OnNight

    // Pulse animation when low
    val infiniteTransition = rememberInfiniteTransition(label = "creditPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (lowCredits) 0.4f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "creditAlpha"
    )

    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            delay(1000)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Clock
        Text(
            text = currentTime.format(DateTimeFormatter.ofPattern("h:mm")),
            fontSize = 32.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.5).sp,
            color = OnNight,
            modifier = Modifier.semantics {
                contentDescription = "Current time: ${currentTime.format(DateTimeFormatter.ofPattern("h:mm a"))}"
            }
        )

        // Credit counter
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .alpha(if (lowCredits) alpha else 1f)
                .semantics {
                    contentDescription = "$tokens tokens remaining"
                }
        ) {
            Text(
                text = tokens.toString(),
                fontFamily = FontFamily.Monospace,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = creditColor
            )
            Text(
                text = "tokens",
                fontSize = 11.sp,
                color = creditColor.copy(alpha = 0.7f)
            )
            if (lowCredits) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = " running low",
                        fontSize = 10.sp,
                        color = WarningAmber
                    )
                }
            }
        }
    }
}

@Composable
private fun AppGrid(
    apps: List<AppInfo>,
    appsWithNotifications: Set<String>,
    onAppClick: (AppInfo) -> Unit,
    onSosClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier
    ) {
        items(apps, key = { it.packageName }) { app ->
            AppIconItem(
                app = app,
                hasNotification = app.packageName in appsWithNotifications,
                onClick = { onAppClick(app) }
            )
        }
        // SOS tile is always the last item in the grid
        item(key = "sos_tile") {
            SosTile(onClick = onSosClick)
        }
    }
}

@Composable
private fun AppIconItem(
    app: AppInfo,
    hasNotification: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
            .semantics { contentDescription = app.appName }
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            app.icon?.let { drawable ->
                val bitmap = android.graphics.Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            } ?: Icon(
                Icons.Default.Phone,
                contentDescription = null,
                tint = ElectricViolet,
                modifier = Modifier.size(32.dp)
            )

            // Notification dot — shown only when there is a pending unread notification
            if (hasNotification) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4A90E2))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = app.appName,
            fontSize = 11.sp,
            color = OnNight.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun SosTile(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
            .semantics { contentDescription = "SOS emergency button" }
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(EmergencyRed),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Phone,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "SOS",
            fontSize = 11.sp,
            color = EmergencyRed,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun CreditsExhaustedMessage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "You're out of credits",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = OnNight,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Ask your parent to add more, or they'll reset automatically tomorrow morning.",
            fontSize = 16.sp,
            color = OnNight.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "You can still call your emergency contacts using the SOS tile.",
            fontSize = 14.sp,
            color = OnNight.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}
