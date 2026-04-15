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
    val pendingUrlCount: Int = 0,
    val pendingContactCount: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
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
                val pendingUrl = repo.getPendingUrlCount(device.id)
                val pendingContacts = repo.getPendingContactCount(device.id)
                _uiState.update {
                    it.copy(
                        device = device,
                        activityFeed = activity,
                        lastLocation = location,
                        pendingUrlCount = pendingUrl,
                        pendingContactCount = pendingContacts,
                        isLoading = false
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.toUserMessage("couldn't load dashboard — check your connection")
                    )
                }
            }
        }
    }

    fun addTokens(amount: Int) {
        val deviceId = _uiState.value.device?.id ?: return
        viewModelScope.launch {
            runCatching { repo.addTokens(deviceId, amount) }
                .onSuccess { loadDashboard() }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(errorMessage = e.toUserMessage("couldn't add tokens — please try again"))
                    }
                }
        }
    }

    fun removeTokens(amount: Int) {
        val deviceId = _uiState.value.device?.id ?: return
        viewModelScope.launch {
            runCatching { repo.removeTokens(deviceId, amount) }
                .onSuccess { loadDashboard() }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(errorMessage = e.toUserMessage("couldn't remove tokens — please try again"))
                    }
                }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
