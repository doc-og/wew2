package com.wew.launcher.ui.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.wew.launcher.BuildConfig
import com.wew.launcher.data.SupabaseClient
import com.wew.launcher.data.model.ActionType
import com.wew.launcher.data.model.ActivityLog
import com.wew.launcher.data.repository.DeviceRepository
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class CheckInStep { NEEDS_PERMISSION, GETTING_LOCATION, CONFIRM, SUBMITTING, SUCCESS, ERROR }

data class CheckInUiState(
    val step: CheckInStep = CheckInStep.GETTING_LOCATION,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val message: String = "",
    val errorMessage: String? = null,
    val deviceId: String = ""
)

class CheckInViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = DeviceRepository(application)
    private val prefs = application.getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(CheckInUiState())
    val uiState: StateFlow<CheckInUiState> = _uiState.asStateFlow()

    private val httpClient = HttpClient(Android)

    init {
        val deviceId = prefs.getString("device_id", "") ?: ""
        _uiState.update { it.copy(deviceId = deviceId) }
        checkPermissionAndStart()
    }

    /** Checks if location permission is granted; shows permission gate if not. */
    fun checkPermissionAndStart() {
        val ctx = getApplication<Application>()
        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        if (granted) requestLocation() else {
            _uiState.update { it.copy(step = CheckInStep.NEEDS_PERMISSION) }
        }
    }

    /** Called by the UI after the system permission dialog grants location. */
    fun onPermissionGranted() {
        requestLocation()
    }

    /** Called by the UI when the user permanently denies location. */
    fun onPermissionDenied() {
        _uiState.update {
            it.copy(
                step = CheckInStep.ERROR,
                errorMessage = "Location permission is required for check-in.\n\nPlease enable it in Settings → Apps → wew → Permissions."
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocation() {
        _uiState.update { it.copy(step = CheckInStep.GETTING_LOCATION) }
        val ctx = getApplication<Application>()
        val fusedClient = LocationServices.getFusedLocationProviderClient(ctx)
        viewModelScope.launch {
            runCatching {
                // Try last known location first for speed
                val lastLocation = fusedClient.lastLocation.await()
                if (lastLocation != null) {
                    _uiState.update {
                        it.copy(
                            step = CheckInStep.CONFIRM,
                            latitude = lastLocation.latitude,
                            longitude = lastLocation.longitude,
                            accuracyMeters = lastLocation.accuracy
                        )
                    }
                } else {
                    // Request a fresh fix
                    val cancellationTokenSource = com.google.android.gms.tasks.CancellationTokenSource()
                    val freshLocation = fusedClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        cancellationTokenSource.token
                    ).await()
                    if (freshLocation != null) {
                        _uiState.update {
                            it.copy(
                                step = CheckInStep.CONFIRM,
                                latitude = freshLocation.latitude,
                                longitude = freshLocation.longitude,
                                accuracyMeters = freshLocation.accuracy
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                step = CheckInStep.ERROR,
                                errorMessage = "Could not get your location. Make sure location is enabled."
                            )
                        }
                    }
                }
            }.onFailure { e ->
                Log.e("CheckIn", "Location request failed: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        step = CheckInStep.ERROR,
                        errorMessage = "Location unavailable: ${e.message ?: "unknown error"}"
                    )
                }
            }
        }
    }

    fun onMessageChanged(text: String) {
        _uiState.update { it.copy(message = text) }
    }

    /** Called when the Check In screen is shown anew — resets state and re-checks permission. */
    fun reset() {
        val deviceId = prefs.getString("device_id", "") ?: ""
        _uiState.value = CheckInUiState(deviceId = deviceId)
        checkPermissionAndStart()
    }

    fun onRetry() {
        requestLocation()
    }

    fun onCheckIn(onClose: () -> Unit) {
        val state = _uiState.value
        val lat = state.latitude ?: return
        val lng = state.longitude ?: return
        val deviceId = state.deviceId
        if (deviceId.isEmpty()) return

        _uiState.update { it.copy(step = CheckInStep.SUBMITTING) }

        viewModelScope.launch {
            runCatching {
                // Insert into check_ins table
                SupabaseClient.client.postgrest["check_ins"].insert(
                    buildJsonObject {
                        put("device_id", deviceId)
                        put("latitude", lat)
                        put("longitude", lng)
                        state.accuracyMeters?.let { put("accuracy_meters", it) }
                        val msg = state.message.trim()
                        if (msg.isNotBlank()) put("message", msg)
                    }
                )

                // Log the activity (0 credits — check_in is free)
                runCatching {
                    repo.logActivity(
                        ActivityLog(
                            deviceId = deviceId,
                            actionType = ActionType.CHECK_IN.value,
                            creditsDeducted = 0
                        )
                    )
                }.onFailure { e ->
                    Log.e("CheckIn", "logActivity failed: ${e.message}", e)
                }

                // Call send-fcm-notification edge function
                runCatching {
                    val supabaseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
                    val anonKey = BuildConfig.SUPABASE_ANON_KEY
                    val msgBody = state.message.trim()
                    val payload = buildJsonObject {
                        put("deviceId", deviceId)
                        put("type", "check_in")
                        put("data", buildJsonObject {
                            if (msgBody.isNotBlank()) put("message", msgBody)
                        })
                    }
                    httpClient.post("$supabaseUrl/functions/v1/send-fcm-notification") {
                        header("apikey", anonKey)
                        header("Authorization", "Bearer $anonKey")
                        contentType(ContentType.Application.Json)
                        setBody(payload.toString())
                    }
                }.onFailure { e ->
                    Log.e("CheckIn", "send-fcm-notification failed: ${e.message}", e)
                }

                _uiState.update { it.copy(step = CheckInStep.SUCCESS) }
                delay(2000)
                onClose()
            }.onFailure { e ->
                Log.e("CheckIn", "checkIn insert failed: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        step = CheckInStep.ERROR,
                        errorMessage = "Could not check in: ${e.message ?: "unknown error"}"
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        httpClient.close()
    }
}
