package com.wew.parent.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import com.wew.parent.data.model.AppInfo
import com.wew.parent.data.repository.ParentRepository
import com.wew.parent.ui.theme.BrandViolet
import com.wew.parent.ui.theme.ElectricViolet
import com.wew.parent.ui.theme.EmergencyRed
import com.wew.parent.ui.theme.ParentBackground
import com.wew.parent.ui.theme.SafetyGreen
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Filter tabs
// ---------------------------------------------------------------------------

private enum class AppFilter(val label: String) {
    ALL("All"),
    ALLOWED("Allowed"),
    BLOCKED("Blocked")
}

// ---------------------------------------------------------------------------
// Screen root
// ---------------------------------------------------------------------------

@Composable
fun AppsScreen(deviceId: String) {
    val repo = remember { ParentRepository() }
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(AppFilter.ALL) }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(deviceId) {
        isLoading = true
        apps = runCatching { repo.getInstalledApps(deviceId) }.getOrDefault(emptyList())
        isLoading = false
    }

    val sorted = apps.sortedWith(
        compareByDescending<AppInfo> { it.isWhitelisted }.thenBy { it.appName.lowercase() }
    )

    val filtered = sorted
        .filter { app ->
            when (activeFilter) {
                AppFilter.ALL -> true
                AppFilter.ALLOWED -> app.isWhitelisted
                AppFilter.BLOCKED -> !app.isWhitelisted
            }
        }
        .filter { app ->
            searchQuery.isBlank() ||
                app.appName.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
        }

    val allowedCount = apps.count { it.isWhitelisted }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ParentBackground),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {

        // ── Gradient hero header ─────────────────────────────────────────
        item {
            AppsHeroCard(
                totalApps = apps.size,
                allowedCount = allowedCount,
                onAddClick = { showAddDialog = true }
            )
        }

        // ── Search bar ───────────────────────────────────────────────────
        item {
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        // ── Filter tabs ──────────────────────────────────────────────────
        item {
            FilterTabRow(
                active = activeFilter,
                blockedCount = apps.size - allowedCount,
                allowedCount = allowedCount,
                totalCount = apps.size,
                onSelect = { activeFilter = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
            )
        }

        // ── States ───────────────────────────────────────────────────────
        when {
            isLoading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = BrandViolet)
                    }
                }
            }

            apps.isEmpty() -> {
                item { EmptyDeviceState() }
            }

            filtered.isEmpty() -> {
                item {
                    EmptySearchState(
                        query = searchQuery,
                        filter = activeFilter
                    )
                }
            }

            else -> {
                appListItems(
                    apps = filtered,
                    onToggle = { app ->
                        val newState = !app.isWhitelisted
                        apps = apps.map {
                            if (it.id == app.id) it.copy(isWhitelisted = newState) else it
                        }
                        scope.launch {
                            runCatching { repo.updateAppWhitelist(app.id, newState) }
                            // Maps enabled → request location sharing from device
                            if (newState && app.packageName == "com.google.android.apps.maps") {
                                runCatching { repo.requestLocationSharing(deviceId) }
                            }
                        }
                    },
                    onToggleNotifications = { app ->
                        val newState = !app.notificationsEnabled
                        apps = apps.map {
                            if (it.id == app.id) it.copy(notificationsEnabled = newState) else it
                        }
                        scope.launch {
                            runCatching { repo.updateAppNotifications(app.id, newState) }
                        }
                    }
                )
            }
        }
    }

    // ── Add app dialog ───────────────────────────────────────────────────
    if (showAddDialog) {
        AddAppDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { appName, packageName ->
                showAddDialog = false
                scope.launch {
                    val newApp = runCatching {
                        repo.addApp(deviceId, packageName.trim(), appName.trim())
                    }.getOrNull()
                    if (newApp != null) apps = apps + newApp
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Hero card
// ---------------------------------------------------------------------------

@Composable
private fun AppsHeroCard(
    totalApps: Int,
    allowedCount: Int,
    onAddClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(BrandViolet, ElectricViolet)
                )
            )
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp, bottom = 28.dp)
    ) {
        // Add button — 48dp touch target top-right
        IconButton(
            onClick = onAddClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
                .semantics { contentDescription = "Add app manually" }
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        Column {
            Text(
                text = "App Whitelist",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Control which apps your child can use",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.72f)
            )

            Spacer(Modifier.height(20.dp))

            // Stat pills
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill(
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF86EFAC), modifier = Modifier.size(14.dp)) },
                    label = "$allowedCount Allowed"
                )
                StatPill(
                    icon = { Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(14.dp)) },
                    label = "${totalApps - allowedCount} Blocked"
                )
                StatPill(
                    icon = { Icon(Icons.Default.Apps, contentDescription = null, tint = Color.White.copy(alpha = 0.70f), modifier = Modifier.size(14.dp)) },
                    label = "$totalApps Total"
                )
            }
        }
    }
}

@Composable
private fun StatPill(
    icon: @Composable () -> Unit,
    label: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        icon()
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

// ---------------------------------------------------------------------------
// Search bar
// ---------------------------------------------------------------------------

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                "Search apps…",
                color = Color(0xFF9999AA),
                fontSize = 15.sp
            )
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = Color(0xFF9999AA),
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            AnimatedVisibility(
                visible = query.isNotBlank(),
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150))
            ) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = Color(0xFF9999AA),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedTextColor = Color(0xFF1A1A2E),
            unfocusedTextColor = Color(0xFF1A1A2E)
        ),
        modifier = modifier
            .height(54.dp)
            .semantics { contentDescription = "Search apps" }
    )
}

// ---------------------------------------------------------------------------
// Filter tab row
// ---------------------------------------------------------------------------

@Composable
private fun FilterTabRow(
    active: AppFilter,
    allowedCount: Int,
    blockedCount: Int,
    totalCount: Int,
    onSelect: (AppFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                AppFilter.ALL to totalCount,
                AppFilter.ALLOWED to allowedCount,
                AppFilter.BLOCKED to blockedCount
            ).forEach { (filter, count) ->
                val isActive = active == filter
                val bgColor by animateColorAsState(
                    targetValue = if (isActive) BrandViolet else Color.Transparent,
                    animationSpec = tween(200),
                    label = "tabBg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isActive) Color.White else Color(0xFF6B6B8A),
                    animationSpec = tween(200),
                    label = "tabText"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgColor)
                        .clickable(onClickLabel = "Filter by ${filter.label}") { onSelect(filter) }
                        .semantics { contentDescription = "${filter.label}: $count apps" },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = filter.label,
                            fontSize = 13.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// App list items (LazyListScope extension)
// ---------------------------------------------------------------------------

private fun LazyListScope.appListItems(
    apps: List<AppInfo>,
    onToggle: (AppInfo) -> Unit,
    onToggleNotifications: (AppInfo) -> Unit = {}
) {
    // Group into Allowed then Blocked for visual clarity
    val allowed = apps.filter { it.isWhitelisted }
    val blocked = apps.filter { !it.isWhitelisted }

    if (allowed.isNotEmpty()) {
        item(key = "header_allowed") {
            SectionHeader(
                title = "ALLOWED",
                count = allowed.size,
                color = SafetyGreen,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
            )
        }

        item(key = "card_allowed") {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                allowed.forEachIndexed { index, app ->
                    AppRow(
                        app = app,
                        onToggle = { onToggle(app) },
                        onToggleNotifications = { onToggleNotifications(app) }
                    )
                    if (index < allowed.lastIndex) {
                        Divider(
                            color = Color(0xFFF0F0F8),
                            modifier = Modifier.padding(start = 72.dp)
                        )
                    }
                }
            }
        }
    }

    if (blocked.isNotEmpty()) {
        item(key = "header_blocked") {
            SectionHeader(
                title = "BLOCKED",
                count = blocked.size,
                color = Color(0xFF9999AA),
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
            )
        }

        item(key = "card_blocked") {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                blocked.forEachIndexed { index, app ->
                    AppRow(
                        app = app,
                        onToggle = { onToggle(app) },
                        onToggleNotifications = { onToggleNotifications(app) }
                    )
                    if (index < blocked.lastIndex) {
                        Divider(
                            color = Color(0xFFF0F0F8),
                            modifier = Modifier.padding(start = 72.dp)
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Section header
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            letterSpacing = 1.sp
        )
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f))
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text(
                text = count.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Individual app row
// ---------------------------------------------------------------------------

@Composable
private fun AppRow(
    app: AppInfo,
    onToggle: () -> Unit,
    onToggleNotifications: () -> Unit = {}
) {
    val avatarColor = avatarColorFor(app.appName)
    val switchAction = if (app.isWhitelisted) "Block ${app.appName}" else "Allow ${app.appName}"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "${app.appName}, ${if (app.isWhitelisted) "allowed" else "blocked"}."
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = app.appName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1A1A2E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    fontSize = 12.sp,
                    color = Color(0xFF9999AA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            // Notifications bell — only shown when app is allowed
            if (app.isWhitelisted) {
                IconButton(
                    onClick = onToggleNotifications,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (app.notificationsEnabled)
                            Icons.Default.Notifications
                        else
                            Icons.Default.NotificationsOff,
                        contentDescription = if (app.notificationsEnabled)
                            "Disable notifications for ${app.appName}"
                        else
                            "Enable notifications for ${app.appName}",
                        tint = if (app.notificationsEnabled) BrandViolet else Color(0xFFCCCCDD),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Whitelist toggle
            Switch(
                checked = app.isWhitelisted,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SafetyGreen,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFCCCCDD)
                ),
                modifier = Modifier.semantics { contentDescription = switchAction }
            )
        }

        // Maps location note
        if (app.packageName == "com.google.android.apps.maps" && app.isWhitelisted) {
            Text(
                text = "Enabling Maps will request location sharing from the device.",
                fontSize = 11.sp,
                color = Color(0xFF9999AA),
                modifier = Modifier.padding(start = 58.dp, top = 2.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Empty states
// ---------------------------------------------------------------------------

@Composable
private fun EmptyDeviceState() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(BrandViolet.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = null,
                    tint = BrandViolet,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "No apps synced yet",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1A1A2E),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Install the WeW Child app on your child's device and open it once — all installed apps will sync here automatically.",
                fontSize = 14.sp,
                color = Color(0xFF6B6B8A),
                textAlign = TextAlign.Center,
                lineHeight = 21.sp
            )
        }
    }
}

@Composable
private fun EmptySearchState(query: String, filter: AppFilter) {
    val message = when {
        query.isNotBlank() -> "No apps match \"$query\""
        filter == AppFilter.ALLOWED -> "No apps are allowed yet"
        filter == AppFilter.BLOCKED -> "All apps are allowed"
        else -> "No apps found"
    }
    val sub = when {
        query.isNotBlank() -> "Try a different search term"
        filter == AppFilter.ALLOWED -> "Toggle an app's switch to allow access"
        filter == AppFilter.BLOCKED -> "Toggle a switch to block access"
        else -> ""
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF0F0F8)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color(0xFFCCCCDD),
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = message,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1A1A2E),
                textAlign = TextAlign.Center
            )
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = sub,
                    fontSize = 13.sp,
                    color = Color(0xFF9999AA),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Add app dialog
// ---------------------------------------------------------------------------

@Composable
private fun AddAppDialog(
    onDismiss: () -> Unit,
    onConfirm: (appName: String, packageName: String) -> Unit
) {
    var appName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    val isValid = appName.isNotBlank() && packageName.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(BrandViolet.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = BrandViolet,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Add App",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1A1A2E)
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Manually add an app by entering its name and package ID (e.g. com.spotify.music).",
                    fontSize = 13.sp,
                    color = Color(0xFF6B6B8A),
                    lineHeight = 19.sp
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text("App name") },
                    placeholder = { Text("e.g. Spotify") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandViolet,
                        unfocusedBorderColor = Color(0xFFDDDDEE)
                    ),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("Package name") },
                    placeholder = { Text("e.g. com.spotify.music") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandViolet,
                        unfocusedBorderColor = Color(0xFFDDDDEE)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isValid) onConfirm(appName, packageName)
                },
                enabled = isValid,
                colors = ButtonDefaults.textButtonColors(contentColor = BrandViolet)
            ) {
                Text("Add App", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF6B6B8A))
            ) {
                Text("Cancel")
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Deterministic pastel avatar color derived from the app name. */
private fun avatarColorFor(name: String): Color {
    val palette = listOf(
        Color(0xFF6C5CE7), // violet
        Color(0xFF0984E3), // blue
        Color(0xFF00B894), // teal
        Color(0xFFE17055), // coral
        Color(0xFFD63031), // red
        Color(0xFFE84393), // pink
        Color(0xFF00CEC9), // cyan
        Color(0xFFFDAB29), // amber
        Color(0xFF6AB04C), // green
        Color(0xFF4A90E2), // sky
    )
    val index = (name.firstOrNull()?.code ?: 0) % palette.size
    return palette[index]
}
