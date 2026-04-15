package com.wew.launcher.service

import android.app.ActivityManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.wew.launcher.MainActivity
import com.wew.launcher.R
import com.wew.launcher.WewApplication
import com.wew.launcher.data.SupabaseClient
import com.wew.launcher.data.model.ActionType
import com.wew.launcher.data.model.ActivityLog
import com.wew.launcher.data.model.LocationLog
import com.wew.launcher.data.model.Schedule
import com.wew.launcher.data.repository.DeviceRepository
import com.wew.launcher.token.TokenEngine
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

class LauncherForegroundService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var repo: DeviceRepository

    private var scheduleCheckJob: Job? = null

    /** Foreground media session (package leaving wew home). */
    private var mediaSessionPkg: String? = null
    private var mediaSessionStart: Long = 0L
    private var mediaSessionAction: ActionType? = null

    private val fallbackMediaPackages: Map<String, ActionType> = mapOf(
        "com.google.android.youtube" to ActionType.VIDEO_WATCHED,
        "com.zhiliaoapp.musically" to ActionType.SOCIAL_SCROLL,
        "com.spotify.music" to ActionType.AUDIO_STREAMED,
        "com.google.android.apps.youtube.music" to ActionType.AUDIO_STREAMED
    )

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val prefs = getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
            val deviceId = prefs.getString("device_id", null) ?: return
            scope.launch {
                runCatching {
                    repo.logLocation(
                        LocationLog(
                            deviceId = deviceId,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracyMeters = location.accuracy
                        )
                    )
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repo = DeviceRepository(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // On API 34+, foreground service type "location" requires location permission
        // to be already granted. Start with or without location type accordingly.
        val hasLocation = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasLocation) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        if (hasLocation) startLocationUpdates()
        startScheduleChecker()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        scheduleCheckJob?.cancel()
        val deviceId = getSharedPreferences("wew_prefs", Context.MODE_PRIVATE).getString("device_id", null)
        if (deviceId != null) {
            runBlocking {
                runCatching { closeMediaSession(deviceId) }
            }
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            15 * 60 * 1000L // 15 minutes
        ).build()

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            // Location permission not granted
        }
    }

    private fun startScheduleChecker() {
        scheduleCheckJob = scope.launch {
            val prefs = getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
            while (isActive) {
                val deviceId = prefs.getString("device_id", null)
                if (deviceId != null) {
                    runCatching { checkScheduleLocks(deviceId) }
                    runCatching { tickForegroundMedia(deviceId) }
                }
                delay(60_000L) // check every minute
            }
        }
    }

    private fun foregroundPackage(): String? {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val procs = am.runningAppProcesses ?: return null
        for (p in procs) {
            if (p.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                return p.pkgList?.firstOrNull()
            }
        }
        return null
    }

    private suspend fun tickForegroundMedia(deviceId: String) {
        val fg = foregroundPackage() ?: return
        if (fg == packageName) {
            closeMediaSession(deviceId)
            return
        }
        val fromDb = repo.getAppMediaActionType(deviceId, fg)
        val action = fromDb?.let { ActionType.fromValue(it) }
            ?: fallbackMediaPackages[fg]
            ?: return

        if (fg != mediaSessionPkg) {
            closeMediaSession(deviceId)
            mediaSessionPkg = fg
            mediaSessionStart = System.currentTimeMillis()
            mediaSessionAction = action
        }
    }

    private suspend fun closeMediaSession(deviceId: String) {
        val pkg = mediaSessionPkg ?: return
        val action = mediaSessionAction ?: return
        val start = mediaSessionStart
        if (start <= 0L) {
            clearMediaSessionState()
            return
        }
        val elapsed = System.currentTimeMillis() - start
        if (elapsed < 30_000L) {
            clearMediaSessionState()
            return
        }
        val minutes = ceil(elapsed / 60_000.0).toInt().coerceAtLeast(1)
        val cost = TokenEngine.calculateCost(action, durationUnits = minutes)
        if (cost > 0) {
            repo.consumeTokens(
                deviceId = deviceId,
                amount = cost,
                actionType = action.value,
                appPackage = pkg,
                appName = null,
                contextMetadata = mapOf(
                    "duration_minutes" to minutes.toString(),
                    "app_package" to pkg
                )
            )
        }
        clearMediaSessionState()
    }

    private fun clearMediaSessionState() {
        mediaSessionPkg = null
        mediaSessionAction = null
        mediaSessionStart = 0L
    }

    private suspend fun checkScheduleLocks(deviceId: String) {
        val schedules = repo.getSchedules(deviceId)
        val now = LocalTime.now()
        val dayOfWeek = java.time.DayOfWeek.from(java.time.LocalDate.now()).value % 7 // 0=Sun

        val shouldLock = schedules.any { schedule ->
            if (!schedule.isEnabled) return@any false
            if (!schedule.daysOfWeek.contains(dayOfWeek)) return@any false
            val start = LocalTime.parse(schedule.startTime, DateTimeFormatter.ofPattern("HH:mm:ss"))
            val end = LocalTime.parse(schedule.endTime, DateTimeFormatter.ofPattern("HH:mm:ss"))
            if (start.isBefore(end)) {
                now.isAfter(start) && now.isBefore(end)
            } else {
                // Overnight schedule (e.g. 21:00–07:00)
                now.isAfter(start) || now.isBefore(end)
            }
        }

        // Update lock state in Supabase
        val prefs = getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
        val currentlyLocked = prefs.getBoolean("schedule_locked", false)
        if (shouldLock != currentlyLocked) {
            prefs.edit().putBoolean("schedule_locked", shouldLock).apply()
            SupabaseClient.client.postgrest["devices"].update(
                buildJsonObject { put("is_locked", shouldLock) }
            ) { filter { eq("id", deviceId) } }

            repo.logActivity(
                ActivityLog(
                    deviceId = deviceId,
                    actionType = if (shouldLock) ActionType.LOCK_ACTIVATED.value else ActionType.LOCK_DEACTIVATED.value
                )
            )
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, WewApplication.CHANNEL_SERVICE)
            .setContentTitle("wew is keeping you safe")
            .setContentText("Tap to open")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
