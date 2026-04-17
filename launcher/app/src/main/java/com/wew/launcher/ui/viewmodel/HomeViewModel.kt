package com.wew.launcher.ui.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wew.launcher.data.model.ActionType
import com.wew.launcher.data.model.ActivityLog
import com.wew.launcher.data.model.AppInfo
import com.wew.launcher.data.repository.DeviceRepository
import com.wew.launcher.service.LauncherForegroundService
import com.wew.launcher.service.NotificationPolicyStore
import com.wew.launcher.token.TokenEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class HomeUiState(
    val apps: List<AppInfo> = emptyList(),
    val currentTokens: Int = 10000,
    val dailyTokenBudget: Int = 10000,
    val isLocked: Boolean = false,
    val tokensExhausted: Boolean = false,
    val isLoading: Boolean = true,
    val deviceId: String = "",
    val appsWithNotifications: Set<String> = emptySet(),
    val pendingUnauthorizedApp: AppInfo? = null,
    val showPasscodeDialog: Boolean = false,
    val passcodeAttemptsLeft: Int = 3,
    val showTimeSelectionDialog: Boolean = false,
    val passcodeHash: String? = null,
    val showAccessDeniedSnackbar: Boolean = false,
    val activeTempAccess: Map<String, String> = emptyMap(),
    // Token cost overrides fetched from Supabase (empty = use engine defaults)
    val tokenCostOverrides: Map<String, Int> = emptyMap()
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = DeviceRepository(application)
    private val appContext = application.applicationContext

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val prefs = application.getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
    private val badgeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            _uiState.update {
                it.copy(appsWithNotifications = NotificationPolicyStore.getBadgePackages(appContext))
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            appContext,
            badgeReceiver,
            IntentFilter(NotificationPolicyStore.ACTION_BADGES_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        startForegroundService()
        loadState()
    }

    private fun startForegroundService() {
        val ctx = getApplication<Application>()
        ctx.startForegroundService(Intent(ctx, LauncherForegroundService::class.java))
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
                val appPolicies = repo.getAppPolicies(deviceId)
                NotificationPolicyStore.writePolicies(appContext, appPolicies)
                val appInfoList = buildWhitelistedAppList(appPolicies)
                val passcode = repo.getDevicePasscode(deviceId)
                val tempAccess = repo.getActiveTempAccess(deviceId)
                val tempAccessMap = tempAccess.associate { it.packageName to it.expiresAt }
                val costOverrides = repo.getTokenCostOverrides(deviceId)

                _uiState.update {
                    it.copy(
                        apps = appInfoList,
                        currentTokens = device.currentTokens,
                        dailyTokenBudget = device.dailyTokenBudget,
                        isLocked = device.isLocked,
                        tokensExhausted = device.currentTokens <= 0,
                        isLoading = false,
                        deviceId = deviceId,
                        appsWithNotifications = NotificationPolicyStore.getBadgePackages(appContext),
                        passcodeHash = passcode?.passcodeHash,
                        activeTempAccess = tempAccessMap,
                        tokenCostOverrides = costOverrides
                    )
                }
                startPolling(deviceId)
            }.onFailure {
                Log.e("WewSync", "loadState failed: ${it.javaClass.simpleName}: ${it.message}", it)
                loadLocalApps()
            }
        }
    }

    private fun startPolling(deviceId: String) {
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                runCatching {
                    val appPolicies = repo.getAppPolicies(deviceId)
                    NotificationPolicyStore.writePolicies(appContext, appPolicies)
                    val device = repo.getDevice(deviceId)
                    val appInfoList = buildWhitelistedAppList(appPolicies)
                    val tempAccess = repo.getActiveTempAccess(deviceId)
                    val tempAccessMap = tempAccess.associate { it.packageName to it.expiresAt }
                    _uiState.update {
                        it.copy(
                            apps = appInfoList,
                            currentTokens = device.currentTokens,
                            dailyTokenBudget = device.dailyTokenBudget,
                            isLocked = device.isLocked,
                            tokensExhausted = device.currentTokens <= 0,
                            appsWithNotifications = NotificationPolicyStore.getBadgePackages(appContext),
                            activeTempAccess = tempAccessMap
                        )
                    }
                }.onFailure { Log.w("WewSync", "poll failed: ${it.message}") }
            }
        }
    }

    private fun loadLocalApps() {
        val pm = getApplication<Application>().packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val resolveInfoList = pm.queryIntentActivities(intent, 0)
        val selfPackage = getApplication<Application>().packageName
        val apps = resolveInfoList
            .filter { it.activityInfo.packageName != selfPackage }
            .map { ri ->
                val pkg = ri.activityInfo.packageName
                AppInfo(
                    packageName = pkg,
                    appName = ri.loadLabel(pm).toString(),
                    icon = runCatching { pm.getApplicationIcon(pkg) }.getOrNull(),
                    isWhitelisted = DeviceRepository.DEFAULT_WHITELIST.contains(pkg),
                    tokenCost = TokenEngine.defaults[ActionType.APP_OPEN]?.baseTokens ?: 5
                )
            }
            .sortedBy { it.appName.lowercase() }
        _uiState.update { it.copy(apps = apps, isLoading = false) }
    }

    /** Called from MainActivity.onResume — re-fetches whitelist to reflect parent dashboard changes. */
    fun refreshApps() {
        val deviceId = prefs.getString("device_id", null) ?: return
        viewModelScope.launch {
            runCatching {
                val appPolicies = repo.getAppPolicies(deviceId)
                NotificationPolicyStore.writePolicies(appContext, appPolicies)
                _uiState.update {
                    it.copy(
                        apps = buildWhitelistedAppList(appPolicies),
                        appsWithNotifications = NotificationPolicyStore.getBadgePackages(appContext)
                    )
                }
            }.onFailure { Log.e("WewSync", "refreshApps failed: ${it.message}") }
        }
    }

    fun onAppClicked(app: AppInfo) {
        val state = _uiState.value
        if (state.tokensExhausted) return

        val tempExpiry = state.activeTempAccess[app.packageName]
        val hasValidTempAccess = tempExpiry != null &&
            Instant.parse(tempExpiry).isAfter(Instant.now())

        if (!app.isWhitelisted && !hasValidTempAccess) {
            onUnauthorizedAppTapped(app)
            return
        }

        val actionType = resolveActionType(app.packageName)
        val result = TokenEngine.consume(
            actionType = actionType,
            currentBalance = state.currentTokens,
            overrides = state.tokenCostOverrides
        )

        if (result.success) {
            hapticFeedback()
            NotificationPolicyStore.clearBadge(appContext, app.packageName)
            _uiState.update {
                it.copy(
                    currentTokens = result.newBalance,
                    tokensExhausted = result.newBalance <= 0,
                    appsWithNotifications = it.appsWithNotifications - app.packageName
                )
            }
            viewModelScope.launch {
                repo.consumeTokens(
                    deviceId = state.deviceId,
                    amount = result.cost,
                    actionType = actionType.value,
                    appPackage = app.packageName,
                    appName = app.appName
                )
            }
        } else {
            // Insufficient tokens — prompt child to request more
            viewModelScope.launch {
                repo.logActivity(
                    ActivityLog(
                        deviceId = state.deviceId,
                        actionType = ActionType.TOKEN_EXHAUSTED.value,
                        appPackage = app.packageName,
                        appName = app.appName
                    )
                )
            }
            _uiState.update { it.copy(tokensExhausted = true) }
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
            _uiState.update {
                it.copy(showPasscodeDialog = false, showTimeSelectionDialog = true)
            }
        } else {
            val attemptsLeft = state.passcodeAttemptsLeft - 1
            if (attemptsLeft <= 0) {
                val blockedApp = state.pendingUnauthorizedApp
                _uiState.update {
                    it.copy(
                        showPasscodeDialog = false,
                        pendingUnauthorizedApp = null,
                        passcodeAttemptsLeft = 3,
                        showAccessDeniedSnackbar = true
                    )
                }
                if (blockedApp != null && deviceId.isNotEmpty()) {
                    viewModelScope.launch {
                        runCatching {
                            repo.logActivity(
                                ActivityLog(
                                    deviceId = deviceId,
                                    actionType = ActionType.APP_BLOCKED.value,
                                    appPackage = blockedApp.packageName,
                                    appName = blockedApp.appName
                                )
                            )
                        }.onFailure { Log.e("WewSync", "logActivity app_blocked failed: ${it.message}") }
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
            val endOfDay = java.time.LocalDate.now()
                .atTime(23, 59, 0)
                .toInstant(ZoneOffset.UTC)
            DateTimeFormatter.ISO_INSTANT.format(endOfDay)
        } else {
            DateTimeFormatter.ISO_INSTANT.format(
                Instant.now().plusSeconds(durationMinutes * 60L)
            )
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
                val result = TokenEngine.consume(
                    actionType = ActionType.TEMP_ACCESS_GRANTED,
                    currentBalance = state.currentTokens,
                    overrides = state.tokenCostOverrides
                )
                if (result.success) {
                    repo.consumeTokens(
                        deviceId = deviceId,
                        amount = result.cost,
                        actionType = ActionType.TEMP_ACCESS_GRANTED.value,
                        appPackage = app.packageName,
                        appName = app.appName
                    )
                    _uiState.update {
                        it.copy(
                            currentTokens = result.newBalance,
                            tokensExhausted = result.newBalance <= 0
                        )
                    }
                }
            }.onFailure { Log.e("WewSync", "grantTempAccess failed: ${it.message}") }
        }
    }

    /** Submit a request to the parent for more tokens for a specific app. */
    fun requestMoreTokens(app: AppInfo?, reason: String?) {
        val state = _uiState.value
        if (state.deviceId.isEmpty()) return
        viewModelScope.launch {
            repo.submitTokenRequest(
                com.wew.launcher.data.model.TokenRequest(
                    deviceId = state.deviceId,
                    appPackage = app?.packageName,
                    appName = app?.appName,
                    tokensRequested = 1000,
                    reason = reason
                )
            )
            repo.logActivity(
                ActivityLog(
                    deviceId = state.deviceId,
                    actionType = ActionType.TOKEN_REQUEST.value,
                    appPackage = app?.packageName,
                    appName = app?.appName
                )
            )
        }
    }

    fun markAppNotification(packageName: String) {
        NotificationPolicyStore.setBadgeVisible(appContext, packageName, visible = true)
        _uiState.update {
            it.copy(appsWithNotifications = it.appsWithNotifications + packageName)
        }
    }

    private fun buildWhitelistedAppList(appPolicies: List<com.wew.launcher.data.model.AppRecord>): List<AppInfo> {
        val pm = getApplication<Application>().packageManager
        return appPolicies
            .asSequence()
            .filter { it.isWhitelisted }
            .map { record ->
                val icon = runCatching { pm.getApplicationIcon(record.packageName) }.getOrNull()
                AppInfo(
                    packageName = record.packageName,
                    appName = record.appName,
                    icon = icon,
                    isWhitelisted = true,
                    tokenCost = TokenEngine.defaults[ActionType.APP_OPEN]?.baseTokens ?: 5
                )
            }
            .toList()
    }

    private fun hashPin(deviceId: String, pin: String): String {
        val input = "$deviceId$pin"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun resolveActionType(packageName: String): ActionType = when {
        packageName.contains("dialer") || packageName.contains("phone") -> ActionType.CALL_MADE
        packageName.contains("mms") || packageName.contains("messaging") -> ActionType.SMS_SENT
        packageName.contains("camera") -> ActionType.PHOTO_TAKEN
        else -> ActionType.APP_OPEN
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

    override fun onCleared() {
        runCatching { appContext.unregisterReceiver(badgeReceiver) }
        super.onCleared()
    }
}
