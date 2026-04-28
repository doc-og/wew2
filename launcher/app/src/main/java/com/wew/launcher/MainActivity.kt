package com.wew.launcher

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.wew.launcher.data.repository.DeviceRepository
import com.wew.launcher.sms.MessagingCapability
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wew.launcher.service.LauncherForegroundService
import com.wew.launcher.service.WewDeviceAdminReceiver
import com.wew.launcher.ui.screen.ChatScreen
import com.wew.launcher.ui.screen.CheckInScreen
import com.wew.launcher.ui.screen.ContactsScreen
import com.wew.launcher.ui.screen.ConversationListScreen
import com.wew.launcher.ui.screen.MapScreen
import com.wew.launcher.ui.screen.REQUIRED_ROLE_DEFAULT_SMS
import com.wew.launcher.ui.screen.RuntimePermissionGateScreen
import com.wew.launcher.ui.screen.ScheduleLockScreen
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
        val displayName: String,
        val isNewCompose: Boolean = false,
        /** Bumps each time user opens new compose so ChatViewModel state resets. */
        val composeSession: Int = 0,
        /** True when the thread has 2+ remote participants. */
        val isGroup: Boolean = false,
        /** Participant labels still awaiting parent approval; non-empty blocks replies. */
        val unapprovedParticipantLabels: List<String> = emptyList(),
        /** Raw phone addresses for every remote participant; used as reply targets. */
        val participantAddresses: List<String> = emptyList(),
        /** True when this thread is the WeW Parent thread (drives server system summaries). */
        val isParent: Boolean = false
    ) : WewScreen()
    data class Web(val url: String) : WewScreen()
    object Map : WewScreen()
}

// ── Activity ──────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {

    private val adminComponent by lazy {
        ComponentName(this, WewDeviceAdminReceiver::class.java)
    }

    /** Bumps when any setup step advances so Compose re-reads state. */
    internal val permissionTick = mutableIntStateOf(0)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (allRuntimePermissionsGranted()) onAllRuntimePermissionsHandled()
        permissionTick.intValue++
    }

    private val smsRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (allRuntimePermissionsGranted()) onAllRuntimePermissionsHandled()
        permissionTick.intValue++
    }

    private val adminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Regardless of result, re-evaluate — if still not active, onResume will retry
        permissionTick.intValue++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
        val hasDeviceId = !prefs.getString("device_id", null).isNullOrEmpty()
        if (!hasDeviceId) {
            startActivity(Intent(this, SetupActivity::class.java))
        }

        setContent {
            WewLauncherTheme {
                val activity = LocalContext.current as MainActivity
                @Suppress("UNUSED_VARIABLE")
                val _tick = activity.permissionTick.intValue

                val missing = activity.remainingRuntimePermissions()

                if (missing.isNotEmpty()) {
                    RuntimePermissionGateScreen(
                        missingPermissions = missing,
                        onPermissionItemClick = { item ->
                            activity.requestSpecificPermissions(item.permissions)
                        }
                    )
                } else {
                    val wewPrefs = LocalContext.current
                        .getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
                    val deviceId = wewPrefs.getString("device_id", "") ?: ""

                    var scheduleLocked by remember {
                        mutableStateOf(wewPrefs.getBoolean("schedule_locked", false))
                    }
                    var scheduleUnlockHint by remember {
                        mutableStateOf(wewPrefs.getString("schedule_unlock_hint", "") ?: "")
                    }
                    DisposableEffect(Unit) {
                        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                            when (key) {
                                "schedule_locked" -> scheduleLocked = sp.getBoolean(key, false)
                                "schedule_unlock_hint" ->
                                    scheduleUnlockHint = sp.getString(key, "") ?: ""
                            }
                        }
                        wewPrefs.registerOnSharedPreferenceChangeListener(listener)
                        onDispose { wewPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
                    }

                    var parentOverride by remember { mutableStateOf(false) }
                    DisposableEffect(scheduleLocked) {
                        if (!scheduleLocked) parentOverride = false
                        onDispose {}
                    }

                    if (scheduleLocked && !parentOverride) {
                        ScheduleLockScreen(
                            unlockTime = scheduleUnlockHint,
                            deviceId = deviceId,
                            onPasscodeUnlock = { parentOverride = true }
                        )
                    } else {
                        Box(Modifier.fillMaxSize()) {
                            val convListViewModel: ConversationListViewModel = viewModel()
                            val contactsViewModel: ContactsViewModel = viewModel()
                            val checkInViewModel: CheckInViewModel = viewModel()

                            var screen by remember { mutableStateOf<WewScreen>(WewScreen.ConversationList) }
                            var composeSession by remember { mutableIntStateOf(0) }
                            var showContacts by remember { mutableStateOf(false) }
                            var showCheckIn by remember { mutableStateOf(false) }

                            when (val s = screen) {
                                is WewScreen.ConversationList -> {
                                    ConversationListScreen(
                                        viewModel = convListViewModel,
                                        onOpenThread = { item ->
                                            convListViewModel.tryOpenChatEntry {
                                                screen = WewScreen.Chat(
                                                    threadId = item.thread.threadId,
                                                    address = item.thread.address,
                                                    displayName = item.resolvedName,
                                                    isNewCompose = false,
                                                    isGroup = item.isGroup,
                                                    unapprovedParticipantLabels = item.unapprovedParticipantLabels,
                                                    participantAddresses = item.participantAddresses,
                                                    isParent = item.isParent
                                                )
                                            }
                                        },
                                        onOpenNewCompose = {
                                            convListViewModel.tryOpenChatEntry {
                                                convListViewModel.load()
                                                composeSession++
                                                screen = WewScreen.Chat(
                                                    -1L,
                                                    "",
                                                    "new message",
                                                    isNewCompose = true,
                                                    composeSession = composeSession
                                                )
                                            }
                                        },
                                        onOpenContacts = {
                                            convListViewModel.tryOpenLauncherOverlay {
                                                showContacts = true
                                            }
                                        },
                                        onOpenCheckIn = {
                                            convListViewModel.tryOpenLauncherOverlay {
                                                checkInViewModel.reset()
                                                showCheckIn = true
                                            }
                                        },
                                        onOpenMap = {
                                            convListViewModel.tryOpenMapScreen {
                                                screen = WewScreen.Map
                                            }
                                        },
                                        onOpenCalendar = { pkg ->
                                            convListViewModel.tryOpenApprovedExternalApp(pkg) {
                                                val intent = packageManager.getLaunchIntentForPackage(pkg)
                                                    ?: Intent(Intent.ACTION_MAIN).apply {
                                                        addCategory(Intent.CATEGORY_APP_CALENDAR)
                                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                    }
                                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                startActivity(intent)
                                            }
                                        },
                                        onOpenWeather = { pkg ->
                                            val intent = packageManager.getLaunchIntentForPackage(pkg)
                                                ?: return@ConversationListScreen
                                            convListViewModel.tryOpenApprovedExternalApp(pkg) {
                                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                startActivity(intent)
                                            }
                                        },
                                        onOpenApp = { pkg ->
                                            val intent = packageManager.getLaunchIntentForPackage(pkg)
                                                ?: return@ConversationListScreen
                                            convListViewModel.tryOpenApprovedExternalApp(pkg) {
                                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                startActivity(intent)
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

                                is WewScreen.Chat -> {
                                    ChatScreen(
                                        threadId = s.threadId,
                                        recipientAddress = s.address,
                                        displayName = s.displayName,
                                        mergeSystemSummaries = !s.isNewCompose && s.isParent,
                                        composeSession = s.composeSession,
                                        conversationListViewModel = convListViewModel,
                                        isGroup = s.isGroup,
                                        unapprovedParticipantLabels = s.unapprovedParticipantLabels,
                                        participantAddresses = s.participantAddresses,
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
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", null)
        if (!deviceId.isNullOrEmpty()) {
            lifecycleScope.launch {
                val dr = DeviceRepository(this@MainActivity)
                runCatching { dr.syncMessagingHealth(deviceId) }
                runCatching { dr.syncAppListIfStale(deviceId, this@MainActivity) }
            }
        }
        if (allRuntimePermissionsGranted()) onAllRuntimePermissionsHandled()
        permissionTick.intValue++
    }

    internal fun ensureRuntimePermissionsFlow() {
        if (allRuntimePermissionsGranted()) {
            onAllRuntimePermissionsHandled()
            return
        }
        if (!hasFineOrCoarseLocation()) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }
        val missing = remainingRuntimePermissions()
        val manifestMissing = missing.filter { it != REQUIRED_ROLE_DEFAULT_SMS }
        if (manifestMissing.isNotEmpty()) {
            permissionLauncher.launch(manifestMissing.toTypedArray())
            return
        }
        if (missing.contains(REQUIRED_ROLE_DEFAULT_SMS)) {
            requestDefaultSmsApp()
            return
        }
        onAllRuntimePermissionsHandled()
    }

    internal fun requestSpecificPermissions(permissions: List<String>) {
        if (permissions.isEmpty()) return
        val uniquePermissions = permissions.distinct()
        if (uniquePermissions.any { it == REQUIRED_ROLE_DEFAULT_SMS }) {
            requestDefaultSmsApp()
            return
        }

        val requestsLocation = uniquePermissions.any {
            it == Manifest.permission.ACCESS_FINE_LOCATION ||
                it == Manifest.permission.ACCESS_COARSE_LOCATION
        }

        if (requestsLocation && !hasFineOrCoarseLocation()) {
            // Keep flow consistent: one prompt per tap.
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
            return
        }

        val pending = uniquePermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        val nextPermission = pending.firstOrNull()
        if (nextPermission != null) {
            permissionLauncher.launch(arrayOf(nextPermission))
        }
    }

    internal fun allRuntimePermissionsGranted(): Boolean =
        remainingRuntimePermissions().isEmpty()

    internal fun remainingRuntimePermissions(): List<String> {
        val needed = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        }
        if (!hasFineOrCoarseLocation()) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        return buildList {
            addAll(missing)
            if (!isWewDefaultSmsApp()) add(REQUIRED_ROLE_DEFAULT_SMS)
        }
    }

    private fun isWewDefaultSmsApp(): Boolean = MessagingCapability.isDefaultSmsApp(this)

    private fun requestDefaultSmsApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java) ?: return
            if (rm.isRoleHeld(RoleManager.ROLE_SMS)) {
                permissionTick.intValue++
                return
            }
            runCatching {
                smsRoleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS))
            }
        } else {
            if (Telephony.Sms.getDefaultSmsPackage(this) == packageName) {
                permissionTick.intValue++
                return
            }
            runCatching {
                smsRoleLauncher.launch(
                    Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                        putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                    }
                )
            }
        }
    }

    private fun hasFineOrCoarseLocation(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun onAllRuntimePermissionsHandled() {
        startForegroundService()
        // Do not auto-launch role/admin/overlay prompts from onResume; these can
        // repeatedly steal focus and interrupt normal navigation (e.g., opening Parent App).
        // Permission/role setup should be triggered from explicit user actions.
    }

    /** Silently activates device admin after permissions — no dedicated setup screen. */
    private fun maybeActivateDeviceAdmin() {
        val prefs = getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (dpm.isAdminActive(adminComponent)) {
            prefs.edit().putBoolean("wew_prompted_device_admin", true).apply()
            return
        }
        if (prefs.getBoolean("wew_prompted_device_admin", false)) return
        prefs.edit().putBoolean("wew_prompted_device_admin", true).apply()
        runCatching {
            adminLauncher.launch(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "wew needs device administrator access to protect your child's settings and keep them safe."
                    )
                }
            )
        }
    }

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
        runCatching { startActivity(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)) }
        return true
    }

    private fun maybeRequestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val prefs = getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("wew_prompted_overlay", false)) return
            prefs.edit().putBoolean("wew_prompted_overlay", true).apply()
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                        .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                )
            }
        }
    }

    private fun startForegroundService() {
        val serviceIntent = Intent(this, LauncherForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
        else startService(serviceIntent)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
    }
}
