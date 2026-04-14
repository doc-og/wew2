package com.wew.launcher

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import com.wew.launcher.ui.screen.ChatScreen
import com.wew.launcher.ui.screen.CheckInScreen
import com.wew.launcher.ui.screen.ContactsScreen
import com.wew.launcher.ui.screen.ConversationListScreen
import com.wew.launcher.ui.screen.MapScreen
import com.wew.launcher.ui.screen.SetupActivity
import com.wew.launcher.ui.screen.WebViewScreen
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
        val displayName: String
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
        startForegroundService()
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

        requestPermissionsThenStartService()

        setContent {
            WewLauncherTheme {
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
                                screen = WewScreen.Chat(threadId, address, displayName)
                            },
                            onOpenContacts = { showContacts = true },
                            onOpenCheckIn = {
                                checkInViewModel.reset()
                                showCheckIn = true
                            },
                            onOpenMap = { screen = WewScreen.Map }
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
                        ChatScreen(
                            threadId = s.threadId,
                            recipientAddress = s.address,
                            displayName = s.displayName,
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
            }
        }
    }

    private fun requestPermissionsThenStartService() {
        val needed = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
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

        if (missing.isEmpty()) {
            startForegroundService()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
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
