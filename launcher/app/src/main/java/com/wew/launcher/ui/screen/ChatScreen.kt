package com.wew.launcher.ui.screen

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wew.launcher.sms.SmsDirection
import com.wew.launcher.sms.SmsMessage
import com.wew.launcher.ui.theme.BrandViolet
import com.wew.launcher.ui.theme.Night
import com.wew.launcher.ui.theme.OnNight
import com.wew.launcher.ui.theme.WarningAmber
import com.wew.launcher.ui.viewmodel.ChatViewModel
import com.wew.launcher.ui.viewmodel.ConversationListViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
    threadId: Long,
    recipientAddress: String,
    displayName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val vm: ChatViewModel = viewModel(
        key = "chat_$threadId",
        factory = ChatViewModel.factory(
            app = context.applicationContext as Application,
            threadId = threadId,
            recipientAddress = recipientAddress,
            recipientName = displayName
        )
    )
    val state by vm.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Intercept system back — go to conversation list, not outside the launcher
    BackHandler { onBack() }

    // Scroll to bottom whenever the message count changes
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.lastIndex)
        }
    }

    // Launch phone dialer when a call is confirmed
    LaunchedEffect(state.pendingCall) {
        state.pendingCall?.let { address ->
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$address")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            vm.clearPendingCall()
        }
    }

    // Call confirmation dialog
    if (state.showCallConfirm) {
        CallConfirmDialog(
            recipientName = state.recipientName.ifBlank { displayName },
            tokenCost = 100,
            tokensExhausted = state.tokensExhausted,
            onConfirm = vm::confirmCall,
            onDismiss = vm::dismissCallConfirm
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        ChatTopBar(
            displayName = state.recipientName.ifBlank { displayName },
            onBack = onBack,
            onCall = vm::onCallClick
        )

        HorizontalDivider(color = OnNight.copy(alpha = 0.08f), thickness = 1.dp)

        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BrandViolet)
            }
        } else if (state.messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "no messages yet — say hi!",
                    fontSize = 15.sp,
                    color = OnNight.copy(alpha = 0.4f)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(state.messages, key = { it.id }) { msg ->
                    MessageBubble(message = msg)
                }
            }
        }

        InputBar(
            text = state.inputText,
            onTextChange = vm::onInputChange,
            onSend = vm::sendMessage,
            isSending = state.isSending,
            tokensExhausted = state.tokensExhausted
        )
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────

@Composable
private fun ChatTopBar(displayName: String, onBack: () -> Unit, onCall: () -> Unit = {}) {
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

        val initial = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val avatarColor = ConversationListViewModel.avatarColorFor(displayName)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(avatarColor.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = avatarColor
            )
        }

        Spacer(Modifier.width(10.dp))

        Text(
            text = displayName,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = OnNight,
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = onCall) {
            Icon(
                Icons.Default.Call,
                contentDescription = "Call",
                tint = BrandViolet
            )
        }
    }
}

// ── Call confirm dialog ───────────────────────────────────────────────────────

@Composable
private fun CallConfirmDialog(
    recipientName: String,
    tokenCost: Int,
    tokensExhausted: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E2E),
        title = {
            Text(
                text = "call $recipientName?",
                color = OnNight,
                fontWeight = FontWeight.Medium
            )
        },
        text = {
            if (tokensExhausted) {
                Text(
                    text = "you don't have enough tokens to make a call.",
                    color = WarningAmber,
                    fontSize = 14.sp
                )
            } else {
                Text(
                    text = "this will use $tokenCost tokens.",
                    color = OnNight.copy(alpha = 0.75f),
                    fontSize = 14.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !tokensExhausted,
                colors = ButtonDefaults.textButtonColors(contentColor = BrandViolet)
            ) {
                Text("call")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = OnNight.copy(alpha = 0.6f))
            ) {
                Text("cancel")
            }
        }
    )
}

// ── Message bubble ────────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(message: SmsMessage) {
    val isOutgoing = message.direction == SmsDirection.OUTGOING
    val bubbleBg = if (isOutgoing) BrandViolet else Color(0xFF1E1E2E)
    val textColor = if (isOutgoing) Color.White else OnNight
    val alignment = if (isOutgoing) Alignment.End else Alignment.Start
    val shape = if (isOutgoing) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(bubbleBg)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = message.body,
                fontSize = 15.sp,
                color = textColor,
                lineHeight = 20.sp
            )
        }
        Spacer(Modifier.height(1.dp))
        Text(
            text = formatBubbleTime(message.date),
            fontSize = 11.sp,
            color = OnNight.copy(alpha = 0.4f),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

// ── Input bar ─────────────────────────────────────────────────────────────────

@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
    tokensExhausted: Boolean
) {
    val canSend = text.isNotBlank() && !isSending && !tokensExhausted

    Column {
        if (tokensExhausted) {
            Text(
                text = "tokens exhausted — can't send",
                fontSize = 12.sp,
                color = WarningAmber,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Night)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF13131F))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "message",
                        color = OnNight.copy(alpha = 0.35f),
                        fontSize = 15.sp
                    )
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                maxLines = 5,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandViolet,
                    unfocusedBorderColor = OnNight.copy(alpha = 0.15f),
                    focusedTextColor = OnNight,
                    unfocusedTextColor = OnNight,
                    cursorColor = BrandViolet,
                    focusedContainerColor = Color(0xFF1E1E2E),
                    unfocusedContainerColor = Color(0xFF1E1E2E)
                ),
                textStyle = TextStyle(fontSize = 15.sp)
            )

            Spacer(Modifier.width(8.dp))

            // Token cost label + send button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "10",
                    fontSize = 10.sp,
                    color = if (tokensExhausted) WarningAmber else BrandViolet.copy(alpha = 0.7f)
                )
                IconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (canSend) BrandViolet else BrandViolet.copy(alpha = 0.25f))
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Timestamp ─────────────────────────────────────────────────────────────────

private fun formatBubbleTime(epochMs: Long): String {
    if (epochMs == 0L) return ""
    val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
    val now = Calendar.getInstance()
    val sameDay = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMs))
    } else {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(epochMs))
    }
}
