package com.wew.parent

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wew.parent.data.repository.ParentRepository
import com.wew.parent.ui.theme.ParentBackground
import com.wew.parent.ui.screen.AppsScreen
import com.wew.parent.ui.screen.DashboardScreen
import com.wew.parent.ui.screen.LoginScreen
import com.wew.parent.ui.screen.RegisterDeviceScreen
import com.wew.parent.ui.screen.SettingsScreen
import com.wew.parent.ui.theme.BrandViolet
import com.wew.parent.ui.theme.WewParentTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WewParentTheme {
                WewParentApp()
            }
        }
    }
}

data class NavItem(val route: String, val label: String, val icon: ImageVector)

val navItems = listOf(
    NavItem("dashboard", "Dashboard", Icons.Default.Dashboard),
    NavItem("apps", "Apps", Icons.Default.Apps),
    NavItem("settings", "Settings", Icons.Default.Settings)
)

@Composable
fun WewParentApp() {
    val context = LocalContext.current
    val repo = remember { ParentRepository() }
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("wew_parent_prefs", Context.MODE_PRIVATE) }

    var refreshCount by remember { mutableStateOf(0) }
    var authChecked by remember { mutableStateOf(false) }
    var isAuthenticated by remember { mutableStateOf(false) }
    var hasDevice by remember { mutableStateOf<Boolean?>(null) }
    var deviceId by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf("") }

    // Inactivity / background-close tracking
    var lastInteractionMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var backgroundedAtMs by remember { mutableLongStateOf(0L) }

    fun performAutoLogout() {
        scope.launch {
            runCatching { repo.signOut() }
            isAuthenticated = false
            hasDevice = null
            authChecked = true
            lastInteractionMs = System.currentTimeMillis()
        }
    }

    // Lifecycle observer — detects app going background / returning foreground
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    backgroundedAtMs = System.currentTimeMillis()
                }
                Lifecycle.Event.ON_START -> {
                    if (isAuthenticated && backgroundedAtMs > 0L) {
                        val timeoutMins = prefs.getInt("auto_logout_timeout_mins", 2)
                        if (timeoutMins > 0) {
                            val timeoutMs = timeoutMins * 60_000L
                            val elapsed = System.currentTimeMillis() - backgroundedAtMs
                            if (elapsed >= timeoutMs) {
                                performAutoLogout()
                            }
                        }
                    }
                    backgroundedAtMs = 0L
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // In-app inactivity timer — polls every 15 s
    LaunchedEffect(isAuthenticated) {
        if (!isAuthenticated) return@LaunchedEffect
        while (true) {
            delay(15_000L)
            if (!isAuthenticated) break
            val timeoutMins = prefs.getInt("auto_logout_timeout_mins", 2)
            if (timeoutMins > 0) {
                val timeoutMs = timeoutMins * 60_000L
                val elapsed = System.currentTimeMillis() - lastInteractionMs
                if (elapsed >= timeoutMs) {
                    performAutoLogout()
                    break
                }
            }
        }
    }

    LaunchedEffect(refreshCount) {
        try {
            authChecked = false
            val uid = repo.currentUserId()
            if (uid == null) {
                isAuthenticated = false
                hasDevice = null
            } else {
                userId = uid
                isAuthenticated = true
                val device = runCatching { repo.getDeviceForParent() }.getOrNull()
                deviceId = device?.id ?: ""
                hasDevice = device != null
            }
        } catch (_: Exception) {
            isAuthenticated = false
            hasDevice = null
        } finally {
            authChecked = true
        }
    }

    // Touch interceptor — resets the inactivity clock on any user interaction
    val touchInterceptModifier = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                lastInteractionMs = System.currentTimeMillis()
            }
        }
    }

    if (!authChecked) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ParentBackground),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = BrandViolet)
        }
        return
    }

    if (!isAuthenticated) {
        Box(modifier = touchInterceptModifier.fillMaxSize()) {
            LoginScreen(onLoginSuccess = {
                lastInteractionMs = System.currentTimeMillis()
                refreshCount++
            })
        }
        return
    }

    if (hasDevice == null) return

    if (hasDevice == false) {
        Box(modifier = touchInterceptModifier.fillMaxSize()) {
            RegisterDeviceScreen(onDeviceRegistered = {
                lastInteractionMs = System.currentTimeMillis()
                refreshCount++
            })
        }
        return
    }

    val navController = rememberNavController()

    Box(modifier = touchInterceptModifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 0.dp
                ) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDest = navBackStackEntry?.destination
                    navItems.forEach { item ->
                        val selected = currentDest?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = {
                                Text(
                                    item.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BrandViolet,
                                selectedTextColor = BrandViolet,
                                unselectedIconColor = Color(0xFF9999AA),
                                unselectedTextColor = Color(0xFF9999AA),
                                indicatorColor = BrandViolet.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "dashboard",
                modifier = Modifier.padding(innerPadding)
            ) {
                composable("dashboard") {
                    DashboardScreen(
                        onAddCredits = { },
                        onRemoveCredits = { }
                    )
                }
                composable("apps") {
                    if (deviceId.isNotEmpty()) AppsScreen(deviceId = deviceId)
                }
                composable("settings") {
                    SettingsScreen(userId = userId, deviceId = deviceId)
                }
            }
        }
    }
}
