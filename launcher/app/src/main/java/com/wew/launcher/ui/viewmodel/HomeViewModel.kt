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

data class HomeUiState(
    val apps: List<AppInfo> = emptyList(),
    val currentCredits: Int = 100,
    val dailyBudget: Int = 100,
    val isLocked: Boolean = false,
    val creditsExhausted: Boolean = false,
    val isLoading: Boolean = true,
    val deviceId: String = ""
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
                _uiState.update {
                    it.copy(
                        apps = appInfoList,
                        currentCredits = device.currentCredits,
                        dailyBudget = device.dailyCreditBudget,
                        isLocked = device.isLocked,
                        creditsExhausted = device.currentCredits <= 0,
                        isLoading = false,
                        deviceId = deviceId
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
                    _uiState.update {
                        it.copy(
                            apps = appInfoList,
                            currentCredits = device.currentCredits,
                            dailyBudget = device.dailyCreditBudget,
                            isLocked = device.isLocked,
                            creditsExhausted = device.currentCredits <= 0
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
        _uiState.update { it.copy(apps = apps, isLoading = false) }
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
                _uiState.update { it.copy(apps = appInfoList) }
            }.onFailure {
                Log.e("WewSync", "refreshApps failed: ${it.message}")
            }
        }
    }

    fun onAppClicked(app: AppInfo) {
        val state = _uiState.value
        if (state.creditsExhausted) return

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
                    creditsExhausted = result.newBalance <= 0
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
