package com.wew.parent

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.wew.parent.data.SupabaseClient

class WewParentApplication : Application() {
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
        val alertChannel = NotificationChannel(
            CHANNEL_ALERTS,
            "wew parent alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Alerts from your child's device" }
        manager.createNotificationChannel(alertChannel)
    }

    companion object {
        const val CHANNEL_ALERTS = "wew_parent_alerts"
    }
}
