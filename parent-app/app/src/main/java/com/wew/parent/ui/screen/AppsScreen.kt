package com.wew.parent.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wew.parent.data.model.AppInfo
import com.wew.parent.data.repository.ParentRepository
import com.wew.parent.ui.theme.BrandViolet
import com.wew.parent.ui.theme.ParentBackground
import com.wew.parent.ui.theme.SafetyGreen
import kotlinx.coroutines.launch

@Composable
fun AppsScreen(deviceId: String) {
    val repo = remember { ParentRepository() }
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(deviceId) {
        isLoading = true
        apps = runCatching { repo.getInstalledApps(deviceId) }.getOrDefault(emptyList())
        isLoading = false
    }

    val filtered = if (searchQuery.isBlank()) apps
    else apps.filter {
        it.appName.contains(searchQuery, ignoreCase = true) ||
        it.packageName.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ParentBackground)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("app whitelist", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1A1A2E))
                Text(
                    "${apps.count { it.isWhitelisted }} of ${apps.size} apps allowed",
                    fontSize = 14.sp,
                    color = Color(0xFF3D3D5C)
                )
            }
            IconButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(BrandViolet)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add app", tint = Color.White)
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("search apps", color = Color(0xFF9999AA)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF9999AA)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandViolet)
                }
            }
            apps.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text("📱", fontSize = 48.sp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "no apps synced yet",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1A1A2E),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "to see apps here:\n\n1. Make sure the Wew launcher is installed on your child's device\n2. Open the launcher — it will automatically sync all installed apps\n3. Come back here to approve or block them\n\nYou can also tap + to manually add an app.",
                            fontSize = 14.sp,
                            color = Color(0xFF3D3D5C),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
            filtered.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "no apps match \"$searchQuery\"",
                        fontSize = 14.sp,
                        color = Color(0xFF3D3D5C),
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        filtered.sortedWith(compareByDescending<AppInfo> { it.isWhitelisted }.thenBy { it.appName.lowercase() }),
                        key = { it.id }
                    ) { app ->
                        AppGridCell(
                            app = app,
                            onToggle = {
                                val newState = !app.isWhitelisted
                                apps = apps.map { if (it.id == app.id) it.copy(isWhitelisted = newState) else it }
                                scope.launch {
                                    runCatching { repo.updateAppWhitelist(app.id, newState) }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddAppDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { appName, packageName ->
                showAddDialog = false
                scope.launch {
                    val newApp = runCatching {
                        repo.addApp(deviceId, packageName.trim(), appName.trim())
                    }.getOrNull()
                    if (newApp != null) {
                        apps = apps + newApp
                    }
                }
            }
        )
    }
}

@Composable
private fun AddAppDialog(
    onDismiss: () -> Unit,
    onConfirm: (appName: String, packageName: String) -> Unit
) {
    var appName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text("add app", fontWeight = FontWeight.Medium, color = Color(0xFF1A1A2E))
        },
        text = {
            Column {
                Text(
                    "Manually add an app to the whitelist. The package name is the app's unique ID (e.g. com.spotify.music).",
                    fontSize = 13.sp,
                    color = Color(0xFF3D3D5C),
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text("app name") },
                    placeholder = { Text("e.g. Spotify") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("package name") },
                    placeholder = { Text("e.g. com.spotify.music") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (appName.isNotBlank() && packageName.isNotBlank()) onConfirm(appName, packageName) },
                colors = ButtonDefaults.textButtonColors(contentColor = BrandViolet),
                enabled = appName.isNotBlank() && packageName.isNotBlank()
            ) {
                Text("add", fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF3D3D5C))) {
                Text("cancel")
            }
        }
    )
}

@Composable
private fun AppGridCell(app: AppInfo, onToggle: () -> Unit) {
    val borderColor = if (app.isWhitelisted) SafetyGreen else Color(0xFFDDDDDD)
    val bgColor = if (app.isWhitelisted) SafetyGreen.copy(alpha = 0.08f) else Color(0xFFF5F5F5)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { onToggle() }
            .padding(8.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BrandViolet.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = app.appName.firstOrNull()?.uppercase() ?: "?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandViolet
                )
            }
            if (app.isWhitelisted) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(SafetyGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = app.appName,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF1A1A2E),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
