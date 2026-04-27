package com.wew.launcher.ui.screen

import android.app.Application
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wew.launcher.data.model.WewContact
import com.wew.launcher.data.model.composedDisplayName
import com.wew.launcher.data.model.matchesContactSearch
import com.wew.launcher.sms.SmsDirection
import com.wew.launcher.sms.SmsMessage
import com.wew.launcher.token.TokenEngine
import com.wew.launcher.ui.theme.BrandViolet
import com.wew.launcher.ui.theme.Night
import com.wew.launcher.ui.theme.OnNight
import com.wew.launcher.ui.theme.WarningAmber
import com.wew.launcher.data.model.ActionType
import com.wew.launcher.ui.viewmodel.ChatBubbleItem
import com.wew.launcher.telecom.CallParticipant
import com.wew.launcher.telecom.PhoneMatch
import com.wew.launcher.telecom.WewCallManager
import com.wew.launcher.ui.viewmodel.ChatViewModel
import com.wew.launcher.ui.viewmodel.ConversationListUiState
import com.wew.launcher.ui.viewmodel.ConversationListViewModel
import com.wew.launcher.ui.util.MessageTimeFormat
import java.time.ZoneId

private val URL_REGEX = Regex("""https?://[^\s<>"]+""")

private fun dedupeContactsByPhone(contacts: List<WewContact>): List<WewContact> {
    val out = mutableListOf<WewContact>()
    for (c in contacts) {
        val p = c.phone?.trim().orEmpty()
        if (p.isEmpty()) continue
        if (out.none { PhoneMatch.sameSubscriber(it.phone, p) }) out.add(c)
    }
    return out
}

/**
 * Recipient pool for new-message compose. Every approved contact is eligible —
 * including the parent, so the child can always reach them via compose in
 * addition to the pinned "WeW Parent" thread on the conversation list.
 */
@Suppress("UNUSED_PARAMETER")
private fun buildNewComposeRecipientPool(
    context: Context,
    ui: ConversationListUiState
): List<WewContact> = dedupeContactsByPhone(ui.approvedContacts)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    threadId: Long,
    recipientAddress: String,
    displayName: String,
    mergeSystemSummaries: Boolean,
    composeSession: Int = 0,
    conversationListViewModel: ConversationListViewModel? = null,
    isGroup: Boolean = false,
    unapprovedParticipantLabels: List<String> = emptyList(),
    participantAddresses: List<String> = emptyList(),
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val isNewCompose = threadId == -1L
    val staticListUi = remember { mutableStateOf(ConversationListUiState()) }
    val listUiState by if (isNewCompose && conversationListViewModel != null) {
        conversationListViewModel.uiState.collectAsState()
    } else {
        staticListUi
    }
    val recipientPool = remember(
        isNewCompose,
        conversationListViewModel,
        listUiState.parentPhoneNumber,
        listUiState.approvedContacts
    ) {
        if (!isNewCompose || conversationListViewModel == null) emptyList()
        else buildNewComposeRecipientPool(context, listUiState)
    }

    val vm: ChatViewModel = viewModel(
        key = if (isNewCompose) "chat_compose_$composeSession" else "chat_${threadId}_$recipientAddress",
        factory = ChatViewModel.factory(
            app = context.applicationContext as Application,
            threadId = threadId,
            recipientAddress = recipientAddress,
            recipientName = displayName,
            mergeSystemSummaries = mergeSystemSummaries,
            initialRecipients = emptyList(),
            initialIsGroup = isGroup,
            initialUnapprovedParticipantLabels = unapprovedParticipantLabels,
            initialRecipientAddresses = participantAddresses
        )
    )
    val state by vm.uiState.collectAsState()
    val listState = rememberLazyListState()
    val messageDisplayZone = MessageTimeFormat.receivedOnDeviceZone()

    LaunchedEffect(isNewCompose, conversationListViewModel) {
        if (isNewCompose && conversationListViewModel != null) {
            conversationListViewModel.load()
        }
    }

    // Photo picker — launched when user taps the attach button
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val mime = context.contentResolver.getType(it) ?: "image/jpeg"
            vm.attachImage(it.toString(), mime)
        }
    }

    // Intercept system back — dismiss full-screen viewer first, then go back
    BackHandler {
        if (state.fullScreenImageUri != null) vm.hideFullScreenImage()
        else onBack()
    }

    // Scroll to bottom whenever the message count changes
    LaunchedEffect(state.chatItems.size) {
        if (state.chatItems.isNotEmpty()) {
            listState.scrollToItem(state.chatItems.lastIndex)
        }
    }

    // Place call in-app (self-managed telecom — no system dialer)
    LaunchedEffect(state.pendingCall) {
        state.pendingCall?.let { address ->
            val groupMembers = buildList {
                if (state.selectedRecipients.isNotEmpty()) {
                    for (c in state.selectedRecipients) {
                        val p = c.phone?.trim().orEmpty()
                        if (p.isEmpty()) continue
                        add(CallParticipant(c.name, p))
                    }
                } else {
                    add(
                        CallParticipant(
                            state.recipientName.ifBlank { displayName },
                            address
                        )
                    )
                }
            }
            WewCallManager.placeCall(
                context = context,
                rawNumber = address,
                displayLabel = state.recipientName.ifBlank { displayName },
                groupMembers = groupMembers,
                chargeMetering = true,
                tokenBalanceHint = state.currentTokens
            )
            vm.clearPendingCall()
        }
    }

    // Call confirmation dialog
    if (state.showCallConfirm) {
        CallConfirmDialog(
            recipientName = state.recipientName.ifBlank { displayName },
            tokenCost = TokenEngine.calculateCost(ActionType.CALL_MADE, durationUnits = 0),
            tokensExhausted = state.tokensExhausted,
            onConfirm = vm::confirmCall,
            onDismiss = vm::dismissCallConfirm
        )
    }

    // Full-screen image viewer
    state.fullScreenImageUri?.let { uri ->
        FullScreenImageViewer(uri = uri, onDismiss = vm::hideFullScreenImage)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
            .statusBarsPadding()
    ) {
        if (!isNewCompose) {
            ChatTopBar(
                displayName = state.recipientName.ifBlank { displayName },
                onBack = onBack,
                onCall = vm::onCallClick,
                // Group calls aren't wired through WewCallManager yet; 1:1 calls only.
                callEnabled = state.recipientAddress.isNotBlank() && !state.isGroup,
                isGroup = state.isGroup
            )
        }

        if (isNewCompose && conversationListViewModel != null) {
            when {
                listUiState.isLoading && recipientPool.isEmpty() -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = BrandViolet,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "loading contacts…",
                            fontSize = 14.sp,
                            color = OnNight.copy(alpha = 0.55f)
                        )
                    }
                }
                recipientPool.isEmpty() -> {
                    Text(
                        text = "no approved contacts yet. your parent can add people in WeW Parent.",
                        fontSize = 14.sp,
                        color = OnNight.copy(alpha = 0.55f),
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                else -> {
                    NewComposeRecipientMultiselect(
                        pool = recipientPool,
                        selected = state.selectedRecipients,
                        onSelectionChange = vm::setRecipients,
                        onBack = onBack
                    )
                }
            }
        }

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
        } else if (state.chatItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        isNewCompose &&
                            conversationListViewModel != null &&
                            recipientPool.isNotEmpty() &&
                            state.selectedRecipients.isEmpty() -> {
                            "search for someone above, then say hi!"
                        }
                        else -> "no messages yet — say hi!"
                    },
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
                items(state.chatItems, key = { it.stableKey }) { item ->
                    when (item) {
                        is ChatBubbleItem.Local -> MessageBubble(
                            message = item.message,
                            displayZone = messageDisplayZone,
                            onImageClick = vm::showFullScreenImage,
                            onOpenUrl = onOpenUrl
                        )
                        is ChatBubbleItem.System -> SystemMessageBubble(
                            body = item.body,
                            timeLabel = MessageTimeFormat.formatBubble(item.createdAtMs, messageDisplayZone)
                        )
                    }
                }
            }
        }

        if (state.isReplyBlocked) {
            ReplyBlockedBanner(unapproved = state.unapprovedParticipantLabels)
        } else {
            InputBar(
                text = state.inputText,
                onTextChange = vm::onInputChange,
                onSend = vm::sendMessage,
                onAttach = { photoPicker.launch("image/*") },
                isSending = state.isSending,
                tokensExhausted = state.tokensExhausted,
                sendError = state.sendError,
                attachedImageUri = state.attachedImageUri,
                onClearAttachment = vm::clearAttachment,
                isMms = state.attachedImageUri != null,
                sendEnabled = state.selectedRecipients.isNotEmpty() || state.recipientAddress.isNotBlank()
            )
        }
    }
}

@Composable
private fun ReplyBlockedBanner(unapproved: List<String>) {
    val who = when (unapproved.size) {
        0 -> "someone in this group"
        1 -> unapproved.first()
        else -> unapproved.take(2).joinToString(" and ") +
            if (unapproved.size > 2) " and ${unapproved.size - 2} more" else ""
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.ime.union(WindowInsets.navigationBars)
            )
            .background(Color(0xFF13131F))
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(
            text = "replies paused",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = WarningAmber
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "you can read this group, but you can't reply until your parent approves $who.",
            fontSize = 13.sp,
            color = OnNight.copy(alpha = 0.75f),
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun chatFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BrandViolet,
    unfocusedBorderColor = OnNight.copy(alpha = 0.15f),
    focusedTextColor = OnNight,
    unfocusedTextColor = OnNight,
    focusedLabelColor = OnNight.copy(alpha = 0.65f),
    unfocusedLabelColor = OnNight.copy(alpha = 0.45f),
    cursorColor = BrandViolet,
    focusedContainerColor = Color(0xFF1E1E2E),
    unfocusedContainerColor = Color(0xFF1E1E2E)
)

@Composable
private fun NewComposeRecipientMultiselect(
    pool: List<WewContact>,
    selected: List<WewContact>,
    onSelectionChange: (List<WewContact>) -> Unit,
    onBack: () -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    val qTrim = query.trim()
    val filtered = remember(pool, qTrim, selected) {
        if (qTrim.isEmpty()) {
            emptyList()
        } else {
            pool.filter { candidate ->
                candidate.matchesContactSearch(qTrim) &&
                    selected.none { PhoneMatch.sameSubscriber(it.phone, candidate.phone) }
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = OnNight
                )
            }
            Spacer(Modifier.width(4.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1E1E2E))
                    .border(
                        width = 1.dp,
                        color = OnNight.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = OnNight.copy(alpha = 0.55f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            "Add Contact",
                            color = OnNight.copy(alpha = 0.35f),
                            fontSize = 14.sp
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, color = OnNight),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(BrandViolet),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        if (selected.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 4.dp)
            ) {
                items(
                    items = selected,
                    key = { c -> c.id ?: c.phone.orEmpty() }
                ) { c ->
                    InputChip(
                        selected = true,
                        onClick = {
                            onSelectionChange(
                                selected.filterNot { PhoneMatch.sameSubscriber(it.phone, c.phone) }
                            )
                        },
                        label = {
                            Text(
                                c.composedDisplayName(),
                                maxLines = 1,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }
        }
        if (filtered.isEmpty()) {
            if (qTrim.isNotEmpty()) {
                Text(
                    "no matches",
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                    color = OnNight.copy(alpha = 0.45f),
                    fontSize = 14.sp
                )
            }
        } else {
            Spacer(Modifier.height(6.dp))
            Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 288.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1A1A28),
                    tonalElevation = 4.dp,
                    shadowElevation = 6.dp
                ) {
                    LazyColumn {
                        itemsIndexed(
                            items = filtered,
                            key = { _, c -> c.id ?: c.phone.orEmpty() }
                        ) { index, c ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectionChange(dedupeContactsByPhone(selected + c))
                                        query = ""
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    c.composedDisplayName(),
                                    color = OnNight,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                val detail = buildList {
                                    c.phone?.takeIf { it.isNotBlank() }?.let { add(it) }
                                    c.relationship?.takeIf { it.isNotBlank() }?.let { add(it) }
                                    c.nickname?.takeIf { it.isNotBlank() }?.let { add(it) }
                                }.joinToString(" · ")
                                if (detail.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        detail,
                                        color = OnNight.copy(alpha = 0.55f),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            if (index < filtered.lastIndex) {
                                HorizontalDivider(
                                    color = OnNight.copy(alpha = 0.06f),
                                    thickness = 1.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
private fun SystemMessageBubble(body: String, timeLabel: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "wew",
            fontSize = 11.sp,
            color = OnNight.copy(alpha = 0.45f)
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF2A2540))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = body,
                fontSize = 14.sp,
                color = OnNight.copy(alpha = 0.92f),
                lineHeight = 20.sp
            )
        }
        if (timeLabel.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = timeLabel,
                fontSize = 11.sp,
                color = OnNight.copy(alpha = 0.4f)
            )
        }
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────

@Composable
private fun ChatTopBar(
    displayName: String,
    onBack: () -> Unit,
    onCall: () -> Unit = {},
    callEnabled: Boolean = true,
    isGroup: Boolean = false
) {
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
            if (isGroup) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = null,
                    tint = avatarColor,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = initial,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = avatarColor
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Text(
            text = displayName,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = OnNight,
            modifier = Modifier.weight(1f)
        )

        if (callEnabled) {
            IconButton(onClick = onCall) {
                Icon(
                    Icons.Default.Call,
                    contentDescription = "Call",
                    tint = BrandViolet
                )
            }
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
private fun MessageBubble(
    message: SmsMessage,
    displayZone: ZoneId,
    onImageClick: (String) -> Unit = {},
    onOpenUrl: (String) -> Unit = {}
) {
    val isOutgoing = message.direction == SmsDirection.OUTGOING
    val bubbleBg = if (isOutgoing) BrandViolet else Color(0xFF1E1E2E)
    val textColor = if (isOutgoing) Color.White else OnNight
    val linkColor = if (isOutgoing) Color(0xFFCDB8FF) else BrandViolet
    val alignment = if (isOutgoing) Alignment.End else Alignment.Start
    val shape = if (isOutgoing) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }

    val imageAttachments = message.attachments.filter { it.contentType.startsWith("image/") }
    val context = LocalContext.current

    // Build annotated string with URL spans
    val annotated = remember(message.body, linkColor) {
        buildAnnotatedString {
            var last = 0
            URL_REGEX.findAll(message.body).forEach { match ->
                append(message.body.substring(last, match.range.first))
                pushStringAnnotation("URL", match.value)
                pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                append(match.value)
                pop()
                pop()
                last = match.range.last + 1
            }
            if (last < message.body.length) append(message.body.substring(last))
        }
    }
    val hasLinks = URL_REGEX.containsMatchIn(message.body)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        // Image attachments (received MMS or sent MMS reflected back)
        imageAttachments.forEach { attachment ->
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(attachment.contentUri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Image",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .widthIn(max = 250.dp)
                    .height(200.dp)
                    .clip(shape)
                    .clickable { onImageClick(attachment.contentUri) }
            )
            Spacer(Modifier.height(2.dp))
        }

        // Text body — uses ClickableText when URLs are present
        if (message.body.isNotBlank()) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(shape)
                    .background(bubbleBg)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                if (hasLinks) {
                    ClickableText(
                        text = annotated,
                        style = TextStyle(
                            fontSize = 15.sp,
                            color = textColor,
                            lineHeight = 20.sp
                        ),
                        onClick = { offset: Int ->
                            annotated.getStringAnnotations("URL", offset, offset)
                                .firstOrNull()
                                ?.let { ann -> onOpenUrl(ann.item) }
                        }
                    )
                } else {
                    Text(
                        text = message.body,
                        fontSize = 15.sp,
                        color = textColor,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(1.dp))
        Text(
            text = MessageTimeFormat.formatBubble(message.date, displayZone),
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
    onAttach: () -> Unit,
    isSending: Boolean,
    tokensExhausted: Boolean,
    sendError: String?,
    attachedImageUri: String?,
    onClearAttachment: () -> Unit,
    isMms: Boolean,
    sendEnabled: Boolean = true
) {
    val canSend = (text.isNotBlank() || attachedImageUri != null) && !isSending && !tokensExhausted && sendEnabled
    val tokenCost = TokenEngine.calculateCost(
        if (isMms) ActionType.MMS_SENT else ActionType.SMS_SENT
    ).toString()
    val context = LocalContext.current

    // Use the union of IME and nav-bar insets so we pin the bar to whichever
    // is taller. When the keyboard is closed, nav-bar padding applies; when it
    // is open, the IME inset already covers the nav bar, so we don't stack
    // both and leave a gap between the bar and the keyboard.
    Column(
        modifier = Modifier.windowInsetsPadding(
            WindowInsets.ime.union(WindowInsets.navigationBars)
        )
    ) {
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
        sendError?.let { err ->
            Text(
                text = err,
                fontSize = 12.sp,
                color = WarningAmber,
                lineHeight = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Night)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // Attached image preview
        if (attachedImageUri != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF13131F))
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(attachedImageUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Attached image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "photo attached",
                    fontSize = 13.sp,
                    color = OnNight.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClearAttachment) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove attachment",
                        tint = OnNight.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF13131F))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Attach button
            IconButton(onClick = onAttach, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = "Attach image",
                    tint = if (attachedImageUri != null) BrandViolet else OnNight.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(4.dp))

            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = if (isMms) "add a caption…" else "message",
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

            // Token cost + send button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = tokenCost,
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

// ── Full-screen image viewer ──────────────────────────────────────────────────

@Composable
private fun FullScreenImageViewer(uri: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(uri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Full screen image",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

