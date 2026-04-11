package com.wew.parent.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wew.parent.data.model.ActivityLogEntry
import com.wew.parent.data.model.Device
import com.wew.parent.data.model.LocationPoint
import com.wew.parent.data.repository.ParentRepository
import com.wew.parent.util.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val device: Device? = null,
    val activityFeed: List<ActivityLogEntry> = emptyList(),
    val lastLocation: LocationPoint? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val creditDialogOpen: Boolean = false
)

class DashboardViewModel : ViewModel() {

    private val repo = ParentRepository()

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val device = repo.getDeviceForParent()
                    ?: error("No device found. Pair a child device first.")
                val activity = repo.getActivityLog(device.id, limit = 20)
                val location = repo.getLocationHistory(device.id, limit = 1).firstOrNull()
                _uiState.update {
                    it.copy(
                        device = device,
                        activityFeed = activity,
                        lastLocation = location,
                        isLoading = false
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.toUserMessage("couldn't load dashboard — check your connection"))
                }
            }
        }
    }

    fun addCredits(amount: Int, note: String?) {
        val deviceId = _uiState.value.device?.id ?: return
        viewModelScope.launch {
            runCatching { repo.addCredits(deviceId, amount, note) }
                .onSuccess { loadDashboard() }
                .onFailure { e -> _uiState.update { it.copy(errorMessage = e.toUserMessage("couldn't add credits — please try again")) } }
        }
    }

    fun removeCredits(amount: Int, note: String?) {
        val deviceId = _uiState.value.device?.id ?: return
        viewModelScope.launch {
            runCatching { repo.removeCredits(deviceId, amount, note) }
                .onSuccess { loadDashboard() }
                .onFailure { e -> _uiState.update { it.copy(errorMessage = e.toUserMessage("couldn't remove credits — please try again")) } }
        }
    }

    fun remoteLock(locked: Boolean) {
        val deviceId = _uiState.value.device?.id ?: return
        viewModelScope.launch {
            runCatching { repo.remoteLockDevice(deviceId, locked) }
                .onSuccess { loadDashboard() }
                .onFailure { e -> _uiState.update { it.copy(errorMessage = e.toUserMessage("couldn't update lock status — please try again")) } }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
