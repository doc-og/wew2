package com.wew.launcher.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wew.launcher.credit.CreditEngine
import com.wew.launcher.data.model.ActionType
import com.wew.launcher.data.model.ActivityLog
import com.wew.launcher.data.model.AppInfo
import com.wew.launcher.data.repository.DeviceRepository
import com.wew.launcher.service.LauncherForegroundService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import android.util.Log
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class HomeUiState(
    val apps: List<AppInfo> = emptyList(),
    val currentCredits: Int = 100,
    val dailyBudget: Int = 100,
    val isLocked: Boolean = false,
    val creditsExhausted: Boolean = false,
    val isLoading: Boolean = true,
    val deviceId: String = "",
    val appsWithNotifications: Set<String> = emptySet(),
    val pendingUnauthorizedApp: AppInfo? = null,
    val showPasscodeDialog: Boolean = false,
    val passcodeAttemptsLeft: Int = 3,
    val showTimeSelectionDialog: Boolean = false,
    val passcodeHash: String? = null,
    val showAccessDeniedSnackbar: Boolean = false,
    val activeTempAccess: Map<String, String> = emptyMap()
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = DeviceRepository(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val prefs = application.getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)

    init {
        startForegroundService()
        loadState()
    }

    private fun startForegroundService() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, LauncherForegroundService::class.java)
        ctx.startForegroundService(intent)
    }

    private fun loadState() {
        viewModelScope.launch {
            val deviceId = prefs.getString("device_id", null)
            if (deviceId == null) {
                loadLocalApps()
                return@launch
            }
            runCatching {
                repo.syncAppList(deviceId, getApplication())
                val device = repo.getDevice(deviceId)
                val whitelistedApps = repo.getWhitelistedApps(deviceId)
                val pm = getApplication<Application>().packageManager
                val appInfoList = whitelistedApps.map { record ->
                    val icon = runCatching {
                        pm.getApplicationIcon(record.packageName)
                    }.getOrNull()
                    AppInfo(
                        packageName = record.packageName,
                        appName = record.appName,
                        icon = icon,
                        isWhitelisted = true,
                        creditCost = record.creditCost
                    )
                }
                val passcode = repo.getDevicePasscode(deviceId)
                val tempAccess = repo.getActiveTempAccess(deviceId)
                val tempAccessMap = tempAccess.associate { it.packageName to it.expiresAt }
                val syntheticApps = listOf(
                    AppInfo("com.wew.launcher.contacts", "Contacts", null, true, 0),
                    AppInfo("com.wew.launcher.checkin", "Check In", null, true, 0)
                )
                _uiState.update {
                    it.copy(
                        apps = appInfoList + syntheticApps,
                        currentCredits = device.currentCredits,
                        dailyBudget = device.dailyCreditBudget,
                        isLocked = device.isLocked,
                        creditsExhausted = device.currentCredits <= 0,
                        isLoading = false,
                        deviceId = deviceId,
                        passcodeHash = passcode?.passcodeHash,
                        activeTempAccess = tempAccessMap
                    )
                }
                // Continuously poll for whitelist changes every 30 seconds so
                // apps enabled in the parent dashboard appear on this screen promptly.
                startPolling(deviceId)
            }.onFailure {
                Log.e("WewSync", "syncAppList failed: ${it.javaClass.simpleName}: ${it.message}", it)
                // Supabase unavailable — fall back to local apps so grid is never empty
                loadLocalApps()
            }
        }
    }

    private fun startPolling(deviceId: String) {
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                runCatching {
                    val whitelistedApps = repo.getWhitelistedApps(deviceId)
                    val device = repo.getDevice(deviceId)
                    val pm = getApplication<Application>().packageManager
                    val appInfoList = whitelistedApps.mapNotNull { record ->
                        val icon = runCatching { pm.getApplicationIcon(record.packageName) }.getOrNull()
                        AppInfo(
                            packageName = record.packageName,
                            appName = record.appName,
                            icon = icon,
                            isWhitelisted = true,
                            creditCost = record.creditCost
                        )
                    }
                    val tempAccess = repo.getActiveTempAccess(deviceId)
                    val tempAccessMap = tempAccess.associate { it.packageName to it.expiresAt }
                    val syntheticApps = listOf(
                        AppInfo("com.wew.launcher.contacts", "Contacts", null, true, 0),
                        AppInfo("com.wew.launcher.checkin", "Check In", null, true, 0)
                    )
                    _uiState.update {
                        it.copy(
                            apps = appInfoList + syntheticApps,
                            currentCredits = device.currentCredits,
                            dailyBudget = device.dailyCreditBudget,
                            isLocked = device.isLocked,
                            creditsExhausted = device.currentCredits <= 0,
                            activeTempAccess = tempAccessMap
                        )
                    }
                }.onFailure {
                    Log.w("WewSync", "poll failed: ${it.message}")
                }
            }
        }
    }

    private fun loadLocalApps() {
        val pm = getApplication<Application>().packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfoList = pm.queryIntentActivities(intent, 0)
        val selfPackage = getApplication<Application>().packageName
        val apps = resolveInfoList
            .filter { it.activityInfo.packageName != selfPackage }
            .map { ri ->
                val pkg = ri.activityInfo.packageName
                val icon = runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
                AppInfo(
                    packageName = pkg,
                    appName = ri.loadLabel(pm).toString(),
                    icon = icon,
                    isWhitelisted = DeviceRepository.DEFAULT_WHITELIST.contains(pkg),
                    creditCost = 1
                )
            }
            .sortedBy { it.appName.lowercase() }
        val syntheticApps = listOf(
            AppInfo("com.wew.launcher.contacts", "Contacts", null, true, 0),
            AppInfo("com.wew.launcher.checkin", "Check In", null, true, 0)
        )
        _uiState.update { it.copy(apps = apps + syntheticApps, isLoading = false) }
    }

    /** Called from MainActivity.onResume — re-fetches the whitelist so toggling an app
     *  in the parent dashboard is reflected immediately when the child returns home. */
    fun refreshApps() {
        val deviceId = prefs.getString("device_id", null) ?: return
        viewModelScope.launch {
            runCatching {
                val whitelistedApps = repo.getWhitelistedApps(deviceId)
                val pm = getApplication<Application>().packageManager
                val appInfoList = whitelistedApps.mapNotNull { record ->
                    val icon = runCatching { pm.getApplicationIcon(record.packageName) }.getOrNull()
                    AppInfo(
                        packageName = record.packageName,
                        appName = record.appName,
                        icon = icon,
                        isWhitelisted = true,
                        creditCost = record.creditCost
                    )
                }
                val syntheticApps = listOf(
                    AppInfo("com.wew.launcher.contacts", "Contacts", null, true, 0),
                    AppInfo("com.wew.launcher.checkin", "Check In", null, true, 0)
                )
                _uiState.update { it.copy(apps = appInfoList + syntheticApps) }
            }.onFailure {
                Log.e("WewSync", "refreshApps failed: ${it.message}")
            }
        }
    }

    fun onAppClicked(app: AppInfo) {
        val state = _uiState.value
        if (state.creditsExhausted) return

        // Check if app is whitelisted OR has active temp access
        val tempExpiry = state.activeTempAccess[app.packageName]
        val hasValidTempAccess = tempExpiry != null &&
            Instant.parse(tempExpiry).isAfter(Instant.now())

        if (!app.isWhitelisted && !hasValidTempAccess) {
            onUnauthorizedAppTapped(app)
            return
        }

        val actionType = resolveActionType(app.packageName)
        val result = CreditEngine.deduct(
            actionType = actionType,
            currentBalance = state.currentCredits,
            dailyBudget = state.dailyBudget
        )

        if (result.success) {
            hapticFeedback()
            _uiState.update {
                it.copy(
                    currentCredits = result.newBalance,
                    creditsExhausted = result.newBalance <= 0,
                    appsWithNotifications = it.appsWithNotifications - app.packageName
                )
            }
            viewModelScope.launch {
                repo.deductCredits(
                    deviceId = state.deviceId,
                    amount = result.cost,
                    actionType = actionType.value,
                    appPackage = app.packageName,
                    appName = app.appName
                )
            }
        }
    }

    fun onUnauthorizedAppTapped(app: AppInfo) {
        _uiState.update {
            it.copy(
                pendingUnauthorizedApp = app,
                showPasscodeDialog = true,
                passcodeAttemptsLeft = 3
            )
        }
    }

    fun onPasscodeSubmitted(pin: String) {
        val state = _uiState.value
        val deviceId = state.deviceId
        val storedHash = state.passcodeHash

        if (storedHash == null) {
            // No passcode configured — deny access
            _uiState.update {
                it.copy(
                    showPasscodeDialog = false,
                    pendingUnauthorizedApp = null,
                    showAccessDeniedSnackbar = true
                )
            }
            return
        }

        val computedHash = hashPin(deviceId, pin)
        if (computedHash == storedHash) {
            // Correct passcode — proceed to time selection
            _uiState.update {
                it.copy(
                    showPasscodeDialog = false,
                    showTimeSelectionDialog = true
                )
            }
        } else {
            val attemptsLeft = state.passcodeAttemptsLeft - 1
            if (attemptsLeft <= 0) {
                // All attempts exhausted
                val blockedApp = state.pendingUnauthorizedApp
                _uiState.update {
                    it.copy(
                        showPasscodeDialog = false,
                        pendingUnauthorizedApp = null,
                        passcodeAttemptsLeft = 3,
                        showAccessDeniedSnackbar = true
                    )
                }
                // Log the block event
                if (blockedApp != null && deviceId.isNotEmpty()) {
                    viewModelScope.launch {
                        runCatching {
                            repo.logActivity(
                                ActivityLog(
                                    deviceId = deviceId,
                                    actionType = ActionType.APP_BLOCKED.value,
                                    appPackage = blockedApp.packageName,
                                    appName = blockedApp.appName,
                                    creditsDeducted = 0
                                )
                            )
                        }.onFailure {
                            Log.e("WewSync", "logActivity app_blocked failed: ${it.message}")
                        }
                    }
                }
            } else {
                _uiState.update { it.copy(passcodeAttemptsLeft = attemptsLeft) }
            }
        }
    }

    fun onPasscodeDismissed() {
        _uiState.update {
            it.copy(
                showPasscodeDialog = false,
                showTimeSelectionDialog = false,
                pendingUnauthorizedApp = null,
                passcodeAttemptsLeft = 3
            )
        }
    }

    fun onAccessDeniedSnackbarShown() {
        _uiState.update { it.copy(showAccessDeniedSnackbar = false) }
    }

    fun onTimeSelected(durationMinutes: Int) {
        val state = _uiState.value
        val app = state.pendingUnauthorizedApp ?: return
        val deviceId = state.deviceId

        val expiresAt = if (durationMinutes == -1) {
            // Rest of day — use 11:59 PM today (simplified; production would check schedule)
            val endOfDay = java.time.LocalDate.now()
                .atTime(23, 59, 0)
                .toInstant(ZoneOffset.UTC)
            DateTimeFormatter.ISO_INSTANT.format(endOfDay)
        } else {
            val expiry = Instant.now().plusSeconds(durationMinutes * 60L)
            DateTimeFormatter.ISO_INSTANT.format(expiry)
        }

        _uiState.update {
            it.copy(
                showTimeSelectionDialog = false,
                pendingUnauthorizedApp = null,
                activeTempAccess = it.activeTempAccess + (app.packageName to expiresAt)
            )
        }

        viewModelScope.launch {
            runCatching {
                repo.grantTempAccess(deviceId, app.packageName, expiresAt)
                repo.deductCredits(
                    deviceId = deviceId,
                    amount = ActionType.TEMP_ACCESS_GRANTED.baseCost,
                    actionType = ActionType.TEMP_ACCESS_GRANTED.value,
                    appPackage = app.packageName,
                    appName = app.appName
                )
                _uiState.update {
                    it.copy(
                        currentCredits = maxOf(0, it.currentCredits - ActionType.TEMP_ACCESS_GRANTED.baseCost),
                        creditsExhausted = (it.currentCredits - ActionType.TEMP_ACCESS_GRANTED.baseCost) <= 0
                    )
                }
            }.onFailure {
                Log.e("WewSync", "grantTempAccess failed: ${it.message}")
            }
        }
    }

    /** For testing / FCM: mark an app as having a pending notification. */
    fun markAppNotification(packageName: String) {
        _uiState.update {
            it.copy(appsWithNotifications = it.appsWithNotifications + packageName)
        }
    }

    private fun hashPin(deviceId: String, pin: String): String {
        val input = "$deviceId$pin"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun resolveActionType(packageName: String): ActionType {
        return when {
            packageName.contains("dialer") || packageName.contains("phone") -> ActionType.CALL_MADE
            packageName.contains("mms") || packageName.contains("messaging") -> ActionType.MESSAGE_SENT
            packageName.contains("camera") -> ActionType.PHOTO_TAKEN
            else -> ActionType.APP_OPEN
        }
    }

    private fun hapticFeedback() {
        val ctx = getApplication<Application>()
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
