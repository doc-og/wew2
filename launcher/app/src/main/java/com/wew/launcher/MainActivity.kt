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
import com.wew.launcher.ui.screen.CheckInScreen
import com.wew.launcher.ui.screen.ContactsScreen
import com.wew.launcher.ui.screen.HomeScreen
import com.wew.launcher.ui.screen.SetupActivity
import com.wew.launcher.ui.theme.WewLauncherTheme
import com.wew.launcher.ui.viewmodel.CheckInViewModel
import com.wew.launcher.ui.viewmodel.ContactsViewModel
import com.wew.launcher.ui.viewmodel.HomeViewModel

class MainActivity : ComponentActivity() {

    private val adminComponent by lazy {
        ComponentName(this, WewDeviceAdminReceiver::class.java)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Whether granted or not, start the service — it handles the partial state
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
                val viewModel: HomeViewModel = viewModel()
                val contactsViewModel: ContactsViewModel = viewModel()
                val checkInViewModel: CheckInViewModel = viewModel()
                // Keep a reference so onResume can trigger a whitelist refresh
                activeViewModel = viewModel

                var showContacts by remember { mutableStateOf(false) }
                var showCheckIn by remember { mutableStateOf(false) }

                Box(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(
                        viewModel = viewModel,
                        onSosClick = { launchEmergencyCall() },
                        onAppClick = { app ->
                            when (app.packageName) {
                                "com.wew.launcher.contacts" -> showContacts = true
                                "com.wew.launcher.checkin" -> {
                                    checkInViewModel.reset()
                                    showCheckIn = true
                                }
                                else -> {
                                    val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                                    if (launchIntent != null) startActivity(launchIntent)
                                }
                            }
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
            }
        }
    }

    private var activeViewModel: HomeViewModel? = null

    override fun onResume() {
        super.onResume()
        activeViewModel?.refreshApps()
    }

    private fun requestPermissionsThenStartService() {
        val needed = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
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

    // Prevent back press from leaving the launcher
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Intentionally swallowed — launcher must stay as home screen
    }

    private fun launchEmergencyCall() {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }
}
