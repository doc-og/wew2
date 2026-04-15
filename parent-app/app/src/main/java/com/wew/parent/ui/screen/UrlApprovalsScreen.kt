package com.wew.parent.ui.screen

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
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
import com.wew.parent.data.model.UrlAccessRequest
import com.wew.parent.data.repository.ParentRepository
import com.wew.parent.ui.theme.BrandViolet
import com.wew.parent.ui.theme.EmergencyRed
import com.wew.parent.ui.theme.ParentBackground
import com.wew.parent.ui.theme.SafetyGreen
import kotlinx.coroutines.launch
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlApprovalsScreen(deviceId: String) {
    val repo = remember { ParentRepository() }
    val scope = rememberCoroutineScope()

    var requests by remember { mutableStateOf<List<UrlAccessRequest>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    fun load() {
        scope.launch {
            isLoading = true
            runCatching { requests = repo.getUrlAccessRequests(deviceId) }
                .onFailure { Log.e("UrlApprovals", it.message ?: "error") }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("URL Approvals", fontWeight = FontWeight.SemiBold)
                        val pending = requests.count { it.status == "pending" }
                        if (pending > 0) {
                            Text("$pending pending", fontSize = 12.sp, color = Color(0xFFFF9800))
                        }
                    }
                },
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandViolet)
                }
                return@Scaffold
            }

            if (requests.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No URL requests yet.", color = Color(0xFF9999AA), fontSize = 15.sp)
                }
                return@Scaffold
            }

            val pending  = requests.filter { it.status == "pending" }
            val resolved = requests.filter { it.status != "pending" }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (pending.isNotEmpty()) {
                    item { SectionLabel("Pending", pending.size, Color(0xFFFF9800)) }
                    items(pending, key = { it.id }) { req ->
                        UrlRequestRow(
                            request = req,
                            onApprove = {
                                scope.launch {
                                    runCatching {
                                        repo.approveUrlAndAddFilter(deviceId, req.id, req.url)
                                        load()
                                    }
                                }
                            },
                            onDeny = {
                                scope.launch {
                                    runCatching {
                                        repo.updateUrlRequestStatus(req.id, "denied")
                                        load()
                                    }
                                }
                            }
                        )
                    }
                }

                if (resolved.isNotEmpty()) {
                    item { SectionLabel("Resolved", resolved.size, Color(0xFF9999AA)) }
                    items(resolved, key = { it.id }) { req ->
                        UrlRequestRow(request = req)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(label: String, count: Int, color: Color) {
    Text(
        text = "$label  ($count)",
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun UrlRequestRow(
    request: UrlAccessRequest,
    onApprove: (() -> Unit)? = null,
    onDeny: (() -> Unit)? = null
) {
    val host = remember(request.url) {
        runCatching { URI(request.url).host?.removePrefix("www.") ?: request.url }.getOrElse { request.url }
    }
    val statusColor = when (request.status) {
        "approved" -> SafetyGreen
        "denied"   -> EmergencyRed
        else       -> Color(0xFFFF9800)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BrandViolet.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Link, null, tint = BrandViolet, modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = host,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1A1A2E)
                )
                if (!request.pageTitle.isNullOrBlank()) {
                    Text(
                        text = request.pageTitle,
                        fontSize = 12.sp,
                        color = Color(0xFF9999AA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = request.url,
                    fontSize = 11.sp,
                    color = Color(0xFFBBBBCC),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (request.status == "pending" && onApprove != null && onDeny != null) {
                IconButton(onClick = onApprove) {
                    Icon(Icons.Default.Check, "Approve", tint = SafetyGreen)
                }
                IconButton(onClick = onDeny) {
                    Icon(Icons.Default.Block, "Deny", tint = EmergencyRed)
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        request.status.replaceFirstChar { it.uppercase() },
                        fontSize = 12.sp,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        HorizontalDivider(color = Color(0xFFF0F0F5))
    }
}
