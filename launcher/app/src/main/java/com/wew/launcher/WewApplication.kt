package com.wew.launcher

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.wew.launcher.data.SupabaseClient
import com.wew.launcher.data.repository.DeviceRepository
import com.wew.launcher.telecom.WewCallManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WewApplication : Application() {

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        SupabaseClient.initialize(
            url = BuildConfig.SUPABASE_URL,
            key = BuildConfig.SUPABASE_ANON_KEY
        )
        WewCallManager.init(this)
        createNotificationChannels()

        val deviceId = getSharedPreferences("wew_prefs", MODE_PRIVATE).getString("device_id", null)
        if (!deviceId.isNullOrBlank()) {
            syncScope.launch {
                delay(1_500L)
                runCatching {
                    DeviceRepository(this@WewApplication).syncAppListIfStale(
                        deviceId = deviceId,
                        context = this@WewApplication,
                        force = true
                    )
                }.onFailure { Log.e("WewSync", "startup app inventory sync failed", it) }
            }
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "wew background service",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Keeps wew running in the background" }

        val alertChannel = NotificationChannel(
            CHANNEL_ALERTS,
            "wew alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Credit and lock alerts" }

        manager.createNotificationChannels(listOf(serviceChannel, alertChannel))
    }

    companion object {
        const val CHANNEL_SERVICE = "wew_service"
        const val CHANNEL_ALERTS = "wew_alerts"
    }
}
