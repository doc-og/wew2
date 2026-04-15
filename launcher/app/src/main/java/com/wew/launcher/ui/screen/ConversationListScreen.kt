package com.wew.launcher.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wew.launcher.ui.theme.BrandViolet
import com.wew.launcher.ui.theme.ElectricViolet
import com.wew.launcher.ui.theme.Night
import com.wew.launcher.ui.theme.OnNight
import com.wew.launcher.ui.theme.WarningAmber
import com.wew.launcher.ui.viewmodel.ConversationItem
import com.wew.launcher.ui.viewmodel.ConversationListUiState
import com.wew.launcher.ui.viewmodel.ConversationListViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationListScreen(
    viewModel: ConversationListViewModel,
    onOpenThread: (threadId: Long, address: String, displayName: String) -> Unit,
    onOpenNewCompose: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenCheckIn: () -> Unit,
    onOpenMap: () -> Unit = {},
    onOpenCalendar: (String) -> Unit = {},
    onOpenWeather: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Launch dialer + send SOS SMS when SOS is confirmed
    LaunchedEffect(state.pendingEmergencyCall) {
        state.pendingEmergencyCall?.let { number ->
            // Send the SOS SMS first
            runCatching {
                @Suppress("DEPRECATION")
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    context.getSystemService(SmsManager::class.java)
                else
                    SmsManager.getDefault()
                smsManager?.sendTextMessage(
                    number, null,
                    "Please call me back I sent this from the SOS",
                    null, null
                )
            }
            // Then open the dialer
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$number")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            viewModel.clearPendingEmergencyCall()
        }
    }

    // Launch parent app when passcode verified
    LaunchedEffect(state.pendingLaunchParentApp) {
        if (state.pendingLaunchParentApp) {
            val intent = context.packageManager
                .getLaunchIntentForPackage("com.wew.parent")
                ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            intent?.let { context.startActivity(it) }
            viewModel.clearPendingLaunchParentApp()
        }
    }

    // SOS confirmation dialog
    if (state.showSosConfirm) {
        SosConfirmDialog(
            parentName = state.parentName,
            parentPhone = state.parentPhoneNumber,
            onConfirm = viewModel::confirmSos,
            onDismiss = viewModel::hideSosDialog
        )
    }

    // Parent app passcode dialog
    if (state.showParentPasscode) {
        PasscodeDialog(
            appName = "Parent App",
            attemptsLeft = state.passcodeAttemptsLeft,
            onPinSubmit = { pin -> viewModel.verifyPasscodeForParentApp(pin) },
            onDismiss = viewModel::dismissParentPasscode
        )
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
            TopBar(
                state = state,
                onMenuClick = { viewModel.showNavMenu() },
                onNewConversation = onOpenNewCompose
            )

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandViolet)
                }
            } else if (state.pinned.isEmpty() && state.conversations.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    // Pinned section
                    if (state.pinned.isNotEmpty()) {
                        item {
                            SectionLabel("Pinned")
                        }
                        items(state.pinned, key = { it.thread.threadId }) { item ->
                            ThreadRow(
                                item = item,
                                onClick = { onOpenThread(item.thread.threadId, item.thread.address, item.resolvedName) },
                                onLongPress = { viewModel.onLongPress(item) }
                            )
                        }
                        item { SectionDivider() }
                    }

                    // All other conversations
                    items(state.conversations, key = { it.thread.threadId }) { item ->
                        ThreadRow(
                            item = item,
                            onClick = { onOpenThread(item.thread.threadId, item.thread.address, item.resolvedName) },
                            onLongPress = { viewModel.onLongPress(item) }
                        )
                    }

                }
            }
        }

        // Tokens exhausted overlay
        AnimatedVisibility(
            visible = state.tokensExhausted,
            enter = fadeIn(), exit = fadeOut()
        ) {
            TokensExhaustedBanner(onRequestMore = { viewModel.requestMoreTokens() })
        }
    }

    // Context menu (long-press on a thread)
    state.contextMenuThread?.let { target ->
        DropdownMenu(
            expanded = true,
            onDismissRequest = { viewModel.dismissContextMenu() },
            modifier = Modifier.background(Color(0xFF1E1E2E))
        ) {
            val pinLabel = if (target.isPinned) "Unpin" else "Pin"
            val muteLabel = if (target.isMuted) "Unmute" else "Mute"
            ContextMenuItem(pinLabel) {
                if (target.isPinned) viewModel.unpinThread(target)
                else viewModel.pinThread(target)
            }
            ContextMenuItem(muteLabel) {
                if (target.isMuted) viewModel.unmuteThread(target)
                else viewModel.muteThread(target)
            }
            if (target.thread.unreadCount > 0) {
                ContextMenuItem("Mark as read") { viewModel.markRead(target) }
            }
            if (!target.isParent) {
                ContextMenuItem("Delete", destructive = true) { viewModel.deleteThread(target) }
            }
        }
    }

    // Navigation menu — slides DOWN from top (scrim fades, sheet slides)
    AnimatedVisibility(
        visible = state.showNavMenu,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable { viewModel.hideNavMenu() }
        )
    }
    AnimatedVisibility(
        visible = state.showNavMenu,
        enter = slideInVertically(tween(280)) { -it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(240)) { -it } + fadeOut(tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF13131F))
                .statusBarsPadding()
        ) {
            NavigationMenuSheet(
                currentTokens = state.currentTokens,
                dailyBudget = state.dailyTokenBudget,
                onConversations = { viewModel.hideNavMenu() },
                onContacts = { viewModel.hideNavMenu(); onOpenContacts() },
                onCheckIn = { viewModel.hideNavMenu(); onOpenCheckIn() },
                onMap = { viewModel.hideNavMenu(); onOpenMap() },
                onParentAccess = { viewModel.hideNavMenu(); viewModel.showParentPasscodeDialog() },
                onOpenCalendar = state.approvedCalendarPackage?.let { pkg ->
                    { viewModel.hideNavMenu(); onOpenCalendar(pkg) }
                },
                onOpenWeather = state.approvedWeatherPackage?.let { pkg ->
                    { viewModel.hideNavMenu(); onOpenWeather(pkg) }
                },
                onSos = { viewModel.hideNavMenu(); viewModel.showSosDialog() }
            )
        }
    }

}

// ── Top bar ───────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(
    state: ConversationListUiState,
    onMenuClick: () -> Unit,
    onNewConversation: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMenuClick) {
            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = OnNight)
        }

        Spacer(modifier = Modifier.weight(1f))

        TokenChip(tokens = state.currentTokens, daily = state.dailyTokenBudget)

        Spacer(modifier = Modifier.weight(1f))

        IconButton(onClick = onNewConversation) {
            Icon(Icons.Default.Create, contentDescription = "New conversation", tint = OnNight)
        }
    }
}

@Composable
private fun TokenChip(tokens: Int, daily: Int) {
    val low = tokens < (daily * 0.2f)
    // High-contrast chip on dark background (WCAG AA–oriented)
    val bg = Color(0xFFF5F3FF)
    val fg = Color(0xFF1A1A2E)
    val borderColor = if (low) WarningAmber else Color(0xFF3D2FA8)
    Row(
        modifier = Modifier
            .semantics {
                contentDescription =
                    "tokens remaining: ${formatTokens(tokens)} of ${formatTokens(daily)} daily budget"
            }
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.5.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (low) {
            Icon(
                Icons.Default.Warning,
                contentDescription = "low token warning",
                tint = Color(0xFFB45309),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = formatTokens(tokens),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg
        )
    }
}

private fun formatTokens(n: Int): String = when {
    n >= 1000 -> "${n / 1000}.${(n % 1000) / 100}k"
    else -> n.toString()
}

// ── Thread row ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadRow(
    item: ConversationItem,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        ContactAvatar(name = item.resolvedName, color = item.avatarColor, isParent = item.isParent)

        Spacer(Modifier.width(12.dp))

        // Content
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.resolvedName,
                        fontSize = 16.sp,
                        fontWeight = if (item.thread.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                        color = OnNight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.isPinned) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = null,
                            tint = BrandViolet.copy(alpha = 0.7f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    if (item.isMuted) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = OnNight.copy(alpha = 0.4f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Text(
                    text = formatTimestamp(item.thread.date),
                    fontSize = 12.sp,
                    color = if (item.thread.unreadCount > 0) BrandViolet else OnNight.copy(alpha = 0.5f)
                )
            }

            Spacer(Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.thread.snippet,
                    fontSize = 14.sp,
                    color = if (item.thread.unreadCount > 0) OnNight else OnNight.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (item.thread.unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    UnreadBadge(count = item.thread.unreadCount)
                }
            }
        }
    }
}

@Composable
private fun ContactAvatar(name: String, color: Color, isParent: Boolean) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (isParent) BrandViolet else color.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = if (isParent) Color.White else color
        )
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    Box(
        modifier = Modifier
            .size(if (count > 9) 22.dp else 18.dp)
            .clip(CircleShape)
            .background(BrandViolet),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

// ── Section helpers ───────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = OnNight.copy(alpha = 0.45f),
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SectionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 16.dp)
            .background(OnNight.copy(alpha = 0.08f))
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("no conversations yet", fontSize = 18.sp, color = OnNight.copy(alpha = 0.5f))
            Spacer(Modifier.height(8.dp))
            Text(
                "tap  +  to start one",
                fontSize = 14.sp,
                color = OnNight.copy(alpha = 0.35f)
            )
        }
    }
}

@Composable
private fun TokensExhaustedBanner(onRequestMore: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Night.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1E1E2E))
                .padding(32.dp)
        ) {
            Text("daily tokens used up", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = OnNight)
            Spacer(Modifier.height(8.dp))
            Text(
                "ask your parent for more, or wait until tomorrow.",
                fontSize = 15.sp,
                color = OnNight.copy(alpha = 0.65f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 22.sp
            )
            Spacer(Modifier.height(24.dp))
            androidx.compose.material3.Button(
                onClick = onRequestMore,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = BrandViolet)
            ) {
                Text("request more tokens")
            }
        }
    }
}

// ── Context menu item ─────────────────────────────────────────────────────────

@Composable
private fun ContextMenuItem(label: String, destructive: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                color = if (destructive) Color(0xFFFF5C5C) else OnNight,
                fontSize = 15.sp
            )
        },
        onClick = onClick
    )
}

// ── Navigation menu sheet ─────────────────────────────────────────────────────

@Composable
fun NavigationMenuSheet(
    currentTokens: Int,
    dailyBudget: Int,
    onConversations: () -> Unit,
    onContacts: () -> Unit,
    onCheckIn: () -> Unit,
    onMap: () -> Unit = {},
    onParentAccess: () -> Unit = {},
    /** Non-null only when the calendar app is approved by the parent. */
    onOpenCalendar: (() -> Unit)? = null,
    /** Non-null only when the weather app is approved by the parent. */
    onOpenWeather: (() -> Unit)? = null,
    onSos: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        Text("wew", fontSize = 28.sp, fontWeight = FontWeight.Medium, color = ElectricViolet)
        Spacer(Modifier.height(4.dp))

        // Token gauge
        val fraction = (currentTokens.toFloat() / dailyBudget.coerceAtLeast(1)).coerceIn(0f, 1f)
        val gaugeColor = when {
            fraction < 0.2f -> WarningAmber
            fraction < 0.5f -> Color(0xFFFFD24E)
            else -> BrandViolet
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(OnNight.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(gaugeColor)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "${formatTokens(currentTokens)} / ${formatTokens(dailyBudget)} tokens",
                fontSize = 12.sp,
                color = OnNight.copy(alpha = 0.6f)
            )
        }

        Spacer(Modifier.height(28.dp))

        NavItem("Messages", onClick = onConversations)
        NavItem("Contacts", onClick = onContacts)
        NavItem("Check In", onClick = onCheckIn)
        NavItem("Map", onClick = onMap)
        onOpenCalendar?.let { cb -> NavItem("Calendar", onClick = cb) }
        onOpenWeather?.let { cb -> NavItem("Weather", onClick = cb) }
        NavItem("Parent App", onClick = onParentAccess)

        // Divider before emergency option
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(1.dp)
                .background(OnNight.copy(alpha = 0.1f))
        )

        // SOS — calls parent owner, no tokens required
        TextButton(
            onClick = onSos,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp, horizontal = 0.dp)
        ) {
            Text(
                "SOS",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFF5C5C),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun NavItem(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 0.dp)
    ) {
        Text(
            label,
            fontSize = 18.sp,
            fontWeight = FontWeight.Normal,
            color = OnNight,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ── SOS confirm dialog ────────────────────────────────────────────────────────

@Composable
private fun SosConfirmDialog(
    parentName: String?,
    parentPhone: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val displayName = parentName.takeUnless { it.isNullOrBlank() } ?: "your parent"
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E2E),
        title = {
            Text(
                text = "Call $displayName",
                color = Color(0xFFFF5C5C),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (parentPhone.isNullOrBlank()) {
                Text(
                    text = "parent number not set up yet. ask your parent to complete setup.",
                    color = WarningAmber,
                    fontSize = 14.sp
                )
            } else {
                Text(
                    text = parentPhone,
                    color = Color(0xFF93C5FD),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable {
                        context.startActivity(
                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(parentPhone)}"))
                        )
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !parentPhone.isNullOrBlank(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFFFF5C5C)
                )
            ) {
                Text("Call", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = OnNight.copy(alpha = 0.6f)
                )
            ) {
                Text("Cancel")
            }
        }
    )
}

// ── Timestamp helpers ─────────────────────────────────────────────────────────

private fun formatTimestamp(epochMs: Long): String {
    if (epochMs == 0L) return ""
    val msgCal = Calendar.getInstance().apply { timeInMillis = epochMs }
    val now = Calendar.getInstance()
    return when {
        isSameDay(msgCal, now) ->
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMs))
        isYesterday(msgCal, now) -> "Yesterday"
        isSameWeek(msgCal, now) ->
            SimpleDateFormat("EEE", Locale.getDefault()).format(Date(epochMs))
        else ->
            SimpleDateFormat("MM/dd/yy", Locale.getDefault()).format(Date(epochMs))
    }
}

private fun isSameDay(a: Calendar, b: Calendar) =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

private fun isYesterday(a: Calendar, b: Calendar): Boolean {
    val yesterday = Calendar.getInstance().apply {
        timeInMillis = b.timeInMillis
        add(Calendar.DAY_OF_YEAR, -1)
    }
    return isSameDay(a, yesterday)
}

private fun isSameWeek(a: Calendar, b: Calendar) =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.WEEK_OF_YEAR) == b.get(Calendar.WEEK_OF_YEAR)
