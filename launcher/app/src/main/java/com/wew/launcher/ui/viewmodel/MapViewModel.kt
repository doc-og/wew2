package com.wew.launcher.ui.viewmodel

import android.app.Application
import android.content.Context
import android.location.Geocoder
import android.os.Build
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

// ── UI state ──────────────────────────────────────────────────────────────────

data class MapUiState(
    val isLocating: Boolean = true,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val currentAddress: String = "Locating…",
    val destination: String = "",
    /** Geo URI to open in native Maps; observed by UI → fires Intent → cleared. */
    val pendingNavigationUri: String? = null,
    val shareStatus: ShareStatus = ShareStatus.IDLE,
    val currentTokens: Int = 10000,
    val dailyTokenBudget: Int = 10000,
    val tokensExhausted: Boolean = false
)

enum class ShareStatus { IDLE, SHARING, SHARED, FAILED }

// ── ViewModel ─────────────────────────────────────────────────────────────────

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = DeviceRepository(application)
    private val prefs = application.getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
    private val fusedClient = LocationServices.getFusedLocationProviderClient(application)

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        loadTokens()
        locateDevice()
    }

    // ── Location ──────────────────────────────────────────────────────────────

    fun locateDevice() {
        _uiState.update { it.copy(isLocating = true, currentAddress = "Locating…") }
        viewModelScope.launch {
            runCatching {
                val cts = CancellationTokenSource()
                @Suppress("MissingPermission")
                val location = fusedClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cts.token
                ).await()

                if (location != null) {
                    val address = reverseGeocode(location.latitude, location.longitude)
                    _uiState.update {
                        it.copy(
                            isLocating = false,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            currentAddress = address
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLocating = false, currentAddress = "Location unavailable") }
                }
            }.onFailure { e ->
                Log.e("MapVM", "locateDevice failed: ${e.message}")
                _uiState.update { it.copy(isLocating = false, currentAddress = "Location unavailable") }
            }
        }
    }

    private fun reverseGeocode(lat: Double, lng: Double): String {
        return runCatching {
            val geocoder = Geocoder(getApplication(), java.util.Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                var result = "Unknown location"
                geocoder.getFromLocation(lat, lng, 1) { addresses ->
                    result = addresses.firstOrNull()?.let { formatAddress(it) } ?: "Unknown location"
                }
                // Brief wait for async callback on API 33+
                Thread.sleep(500)
                result
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lng, 1)
                    ?.firstOrNull()
                    ?.let { formatAddress(it) }
                    ?: "Unknown location"
            }
        }.getOrElse { "Unknown location" }
    }

    private fun formatAddress(address: android.location.Address): String {
        val parts = listOfNotNull(
            address.thoroughfare,
            address.locality ?: address.subAdminArea,
            address.adminArea
        )
        return parts.joinToString(", ").ifBlank { address.getAddressLine(0) ?: "Unknown location" }
    }

    // ── Share location ────────────────────────────────────────────────────────

    fun shareLocation() {
        val state = _uiState.value
        val lat = state.latitude ?: return
        val lng = state.longitude ?: return
        val deviceId = prefs.getString("device_id", null) ?: return
        if (state.tokensExhausted) return

        _uiState.update { it.copy(shareStatus = ShareStatus.SHARING) }

        viewModelScope.launch {
            runCatching {
                repo.consumeTokens(
                    deviceId = deviceId,
                    amount = 10,
                    actionType = ActionType.CHECK_IN.value,
                    appName = "Map"
                )
                repo.logLocation(
                    LocationLog(
                        deviceId = deviceId,
                        latitude = lat,
                        longitude = lng
                    )
                )
                val device = repo.getDevice(deviceId)
                _uiState.update {
                    it.copy(
                        shareStatus = ShareStatus.SHARED,
                        currentTokens = device.currentTokens,
                        tokensExhausted = device.currentTokens <= 0
                    )
                }
            }.onFailure {
                Log.e("MapVM", "shareLocation failed: ${it.message}")
                _uiState.update { s -> s.copy(shareStatus = ShareStatus.FAILED) }
            }
        }
    }

    fun resetShareStatus() = _uiState.update { it.copy(shareStatus = ShareStatus.IDLE) }

    // ── Navigation ────────────────────────────────────────────────────────────

    fun onDestinationChange(text: String) = _uiState.update { it.copy(destination = text) }

    /**
     * Build a geo URI that the native Maps app understands, then set it as
     * pendingNavigationUri. The UI observes this and fires the Intent.
     */
    fun getDirections() {
        val state = _uiState.value
        val dest = state.destination.trim()
        if (dest.isBlank()) return

        val origin = if (state.latitude != null && state.longitude != null)
            "${state.latitude},${state.longitude}"
        else
            ""

        // geo:0,0?q=<destination> with optional saddr
        val uri = if (origin.isNotEmpty())
            "https://maps.google.com/maps?saddr=$origin&daddr=${dest.encodeUriComponent()}"
        else
            "https://maps.google.com/maps?daddr=${dest.encodeUriComponent()}"

        _uiState.update { it.copy(pendingNavigationUri = uri) }
    }

    fun clearPendingNavigation() = _uiState.update { it.copy(pendingNavigationUri = null) }

    // ── Tokens ────────────────────────────────────────────────────────────────

    private fun loadTokens() {
        val deviceId = prefs.getString("device_id", null) ?: return
        viewModelScope.launch {
            runCatching {
                val device = repo.getDevice(deviceId)
                _uiState.update {
                    it.copy(
                        currentTokens = device.currentTokens,
                        dailyTokenBudget = device.dailyTokenBudget,
                        tokensExhausted = device.currentTokens <= 0
                    )
                }
            }.onFailure { Log.w("MapVM", "loadTokens failed: ${it.message}") }
        }
    }
}

private fun String.encodeUriComponent(): String =
    java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")
