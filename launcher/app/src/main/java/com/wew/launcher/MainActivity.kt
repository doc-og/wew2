package com.wew.launcher

import android.Manifest
import android.app.role.RoleManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wew.launcher.service.LauncherForegroundService
import com.wew.launcher.service.WewDeviceAdminReceiver
import com.wew.launcher.service.WewNotificationListenerService
import com.wew.launcher.ui.screen.ChatScreen
import com.wew.launcher.ui.screen.CheckInScreen
import com.wew.launcher.ui.screen.ContactsScreen
import com.wew.launcher.ui.screen.ConversationListScreen
import com.wew.launcher.ui.screen.MapScreen
import com.wew.launcher.ui.screen.SetupActivity
import com.wew.launcher.ui.screen.WebViewScreen
import com.wew.launcher.ui.screen.WewInCallOverlay
import com.wew.launcher.ui.theme.WewLauncherTheme
import com.wew.launcher.ui.viewmodel.CheckInViewModel
import com.wew.launcher.ui.viewmodel.ContactsViewModel
import com.wew.launcher.ui.viewmodel.ConversationListViewModel

// ── Navigation state ──────────────────────────────────────────────────────────

private sealed class WewScreen {
    object ConversationList : WewScreen()
    data class Chat(
        val threadId: Long,
        val address: String,
        val displayName: String,
        val isNewCompose: Boolean = false
    ) : WewScreen()
    data class Web(val url: String) : WewScreen()
    object Map : WewScreen()
}

// ── Activity ──────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {

    private val adminComponent by lazy {
        ComponentName(this, WewDeviceAdminReceiver::class.java)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        ensureRuntimePermissionsFlow()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check Device Admin — prompt setup if not active
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val prefs = getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
        val adminNotActive = !dpm.isAdminActive(adminComponent)
        val hasDeviceId = !prefs.getString("device_id", null).isNullOrEmpty()
        if (adminNotActive || !hasDeviceId) {
            startActivity(Intent(this, SetupActivity::class.java))
        }

        setContent {
            WewLauncherTheme {
                Box(Modifier.fillMaxSize()) {
                val convListViewModel: ConversationListViewModel = viewModel()
                val contactsViewModel: ContactsViewModel = viewModel()
                val checkInViewModel: CheckInViewModel = viewModel()

                var screen by remember { mutableStateOf<WewScreen>(WewScreen.ConversationList) }
                var showContacts by remember { mutableStateOf(false) }
                var showCheckIn by remember { mutableStateOf(false) }

                when (val s = screen) {
                    is WewScreen.ConversationList -> {
                        ConversationListScreen(
                            viewModel = convListViewModel,
                            onOpenThread = { threadId, address, displayName ->
                                screen = WewScreen.Chat(threadId, address, displayName, isNewCompose = false)
                            },
                            onOpenNewCompose = {
                                screen = WewScreen.Chat(-1L, "", "new message", isNewCompose = true)
                            },
                            onOpenContacts = { showContacts = true },
                            onOpenCheckIn = {
                                checkInViewModel.reset()
                                showCheckIn = true
                            },
                            onOpenMap = { screen = WewScreen.Map },
                            onOpenCalendar = { pkg ->
                                val intent = packageManager.getLaunchIntentForPackage(pkg)
                                    ?: Intent(Intent.ACTION_MAIN).apply {
                                        addCategory(Intent.CATEGORY_APP_CALENDAR)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                startActivity(intent)
                            },
                            onOpenWeather = { pkg ->
                                val intent = packageManager.getLaunchIntentForPackage(pkg)
                                    ?: return@ConversationListScreen
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                startActivity(intent)
                            }
                        )

                        if (showContacts) {
                            ContactsScreen(
                                viewModel = contactsViewModel,
                                onBack = { showContacts = false }
                            )
                        }

                        if (showCheckIn) {
                            CheckInScreen(
                                viewModel = checkInViewModel,
                                onClose = { showCheckIn = false }
                            )
                        }
                    }

                    is WewScreen.Chat -> {
                        val approved = convListViewModel.uiState.value.approvedContacts
                        ChatScreen(
                            threadId = s.threadId,
                            recipientAddress = s.address,
                            displayName = s.displayName,
                            mergeSystemSummaries = !s.isNewCompose && s.displayName == "WeW Parent",
                            initialRecipients = emptyList(),
                            approvedContacts = if (s.isNewCompose) approved else emptyList(),
                            onBack = { screen = WewScreen.ConversationList },
                            onOpenUrl = { url -> screen = WewScreen.Web(url) }
                        )
                    }

                    is WewScreen.Web -> {
                        WebViewScreen(
                            initialUrl = s.url,
                            onBack = { screen = WewScreen.ConversationList }
                        )
                    }

                    WewScreen.Map -> {
                        MapScreen(onBack = { screen = WewScreen.ConversationList })
                    }
                }

                WewInCallOverlay()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ensureRuntimePermissionsFlow()
    }

    /**
     * Ask for location first (foreground), then the rest, so the system location prompt
     * shows as soon as the launcher is foregrounded — including after setup or revoking in Settings.
     */
    private fun ensureRuntimePermissionsFlow() {
        if (!hasFineOrCoarseLocation()) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        val needed = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.MANAGE_OWN_CALLS,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        }

        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
            return
        }

        onAllRuntimePermissionsHandled()
    }

    private fun hasFineOrCoarseLocation(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun onAllRuntimePermissionsHandled() {
        startForegroundService()
        if (!maybeRequestCallScreeningRole()) {
            maybeRequestNotificationListenerAccess()
        }
    }

    /** One-time prompt: set WeW as call screening app so unknown numbers are blocked + parent notified. */
    private fun maybeRequestCallScreeningRole(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val prefs = getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("wew_prompted_call_screening", false)) return false
        val rm = getSystemService(RoleManager::class.java) ?: return false
        if (rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            prefs.edit().putBoolean("wew_prompted_call_screening", true).apply()
            return false
        }
        prefs.edit().putBoolean("wew_prompted_call_screening", true).apply()
        runCatching {
            startActivity(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        }
        return true
    }

    private fun maybeRequestNotificationListenerAccess() {
        val prefs = getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("wew_prompted_notification_listener", false)) return
        if (isNotificationListenerEnabled()) {
            prefs.edit().putBoolean("wew_prompted_notification_listener", true).apply()
            return
        }
        prefs.edit().putBoolean("wew_prompted_notification_listener", true).apply()
        runCatching {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val listenerComponent = ComponentName(this, WewNotificationListenerService::class.java)
        return enabledListeners
            .split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it == listenerComponent }
    }

    private fun startForegroundService() {
        val serviceIntent = Intent(this, LauncherForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    // Prevent back press from leaving the launcher entirely.
    // Within-app navigation is handled by BackHandler in each screen composable.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Swallowed — BackHandler composables intercept first; this is the final stop.
    }
}
