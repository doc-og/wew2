package com.wew.launcher.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.wew.launcher.data.model.ActionType
import com.wew.launcher.data.model.LocationLog
import com.wew.launcher.data.repository.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class MapUiState(
    /** Null = still locating; non-null = ready to fire intent or show error */
    val pendingMapUri: String? = null,
    val error: String? = null
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = DeviceRepository(application)
    private val prefs = application.getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
    private val fusedClient = LocationServices.getFusedLocationProviderClient(application)

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        locateAndPrepare()
    }

    private fun locateAndPrepare() {
        viewModelScope.launch {
            runCatching {
                val cts = CancellationTokenSource()
                @Suppress("MissingPermission")
                val location = fusedClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cts.token
                ).await()

                if (location != null) {
                    // Log location + deduct tokens in background
                    logLocationAsync(location.latitude, location.longitude)

                    // geo URI — opens native Maps centred on the fix, zoom 16
                    val uri = "geo:${location.latitude},${location.longitude}?z=16"
                    _uiState.update { it.copy(pendingMapUri = uri) }
                } else {
                    // No fix — fall back to "my location" search in Maps
                    _uiState.update { it.copy(pendingMapUri = "geo:0,0?q=my+location") }
                }
            }.onFailure { e ->
                Log.e("MapVM", "locate failed: ${e.message}")
                _uiState.update { it.copy(error = "Couldn't get your location") }
            }
        }
    }

    private fun logLocationAsync(lat: Double, lng: Double) {
        val deviceId = prefs.getString("device_id", null) ?: return
        viewModelScope.launch {
            runCatching {
                repo.consumeTokens(
                    deviceId = deviceId,
                    amount = 5,
                    actionType = ActionType.CHECK_IN.value,
                    appName = "Map"
                )
                repo.logLocation(LocationLog(deviceId = deviceId, latitude = lat, longitude = lng))
            }.onFailure { Log.w("MapVM", "logLocation failed: ${it.message}") }
        }
    }

    fun clearUri() = _uiState.update { it.copy(pendingMapUri = null) }
}
