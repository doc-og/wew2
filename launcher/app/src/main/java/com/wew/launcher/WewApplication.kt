package com.wew.launcher

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.wew.launcher.data.SupabaseClient

class WewApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        SupabaseClient.initialize(
            url = BuildConfig.SUPABASE_URL,
            key = BuildConfig.SUPABASE_ANON_KEY
        )
        createNotificationChannels()
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
