package com.wew.parent.ui.screen

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wew.parent.data.model.ConversationMeta
import com.wew.parent.data.model.MessageLogEntry
import com.wew.parent.data.repository.ParentRepository
import com.wew.parent.ui.theme.BrandViolet
import com.wew.parent.ui.theme.ParentBackground
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(deviceId: String) {
    val repo = remember { ParentRepository() }
    val scope = rememberCoroutineScope()

    var conversations by remember { mutableStateOf<List<ConversationMeta>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedThread by remember { mutableStateOf<ConversationMeta?>(null) }

    fun load() {
        scope.launch {
            isLoading = true
            runCatching { conversations = repo.getConversations(deviceId) }
                .onFailure { Log.e("MessagesScreen", it.message ?: "error") }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    // Thread detail view
    selectedThread?.let { thread ->
        MessageThreadScreen(
            deviceId = deviceId,
            thread = thread,
            onBack = { selectedThread = null }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Message Log", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF1A1A2E)
                ),
                actions = {
                    IconButton(onClick = { load() }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = Color(0xFF6B6B8A))
                    }
                }
            )
        },
        containerColor = ParentBackground
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandViolet)
            }
            return@Scaffold
        }

        if (conversations.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No conversations logged yet.", color = Color(0xFF9999AA), fontSize = 15.sp)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(conversations, key = { it.id }) { conv ->
                ConversationRow(conv, onClick = { selectedThread = conv })
            }
        }
    }
}

@Composable
private fun ConversationRow(conv: ConversationMeta, onClick: () -> Unit) {
    val initial = conv.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(BrandViolet.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(initial, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = BrandViolet)
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    conv.displayName.ifBlank { "Unknown" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1A1A2E)
                )
                Text(
                    "Thread ${conv.threadId}",
                    fontSize = 12.sp,
                    color = Color(0xFF9999AA)
                )
            }

            Icon(
                Icons.Default.Message,
                null,
                tint = BrandViolet.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
        HorizontalDivider(color = Color(0xFFF0F0F5))
    }
}

// ── Thread detail ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageThreadScreen(
    deviceId: String,
    thread: ConversationMeta,
    onBack: () -> Unit
) {
    val repo = remember { ParentRepository() }
    val scope = rememberCoroutineScope()

    var messages by remember { mutableStateOf<List<MessageLogEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(thread.threadId) {
        isLoading = true
        runCatching { messages = repo.getMessagesForThread(deviceId, thread.threadId) }
            .onFailure { Log.e("MessageThread", it.message ?: "error") }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        thread.displayName.ifBlank { "Thread ${thread.threadId}" },
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF1A1A2E)
                )
            )
        },
        containerColor = ParentBackground
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandViolet)
            }
            return@Scaffold
        }

        if (messages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No messages logged for this thread.", color = Color(0xFF9999AA), fontSize = 15.sp)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            reverseLayout = true
        ) {
            items(messages, key = { it.id }) { msg ->
                MessageLogRow(msg)
            }
        }
    }
}

@Composable
private fun MessageLogRow(msg: MessageLogEntry) {
    val isChild = msg.senderType == "child"
    val bubbleColor = if (isChild) BrandViolet else Color(0xFFEEEEF5)
    val textColor   = if (isChild) Color.White else Color(0xFF1A1A2E)
    val alignment   = if (isChild) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .clip(
                    if (isChild) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
                    else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
                )
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (msg.hasMedia && !msg.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = msg.thumbnailUrl,
                    contentDescription = "Thumbnail",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else if (msg.hasMedia) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Image, null, tint = textColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Media attachment", fontSize = 14.sp, color = textColor)
                }
            } else {
                Text("${msg.messageType} message", fontSize = 14.sp, color = textColor)
            }
        }
        Text(
            formatLogTime(msg.createdAt),
            fontSize = 11.sp,
            color = Color(0xFF9999AA),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

private fun formatLogTime(iso: String): String = runCatching {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val out = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    out.format(sdf.parse(iso.take(19))!!)
}.getOrElse { iso }
