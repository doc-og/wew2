package com.wew.launcher.service

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.wew.launcher.WewApplication
import com.wew.launcher.data.SupabaseClient
import com.wew.launcher.data.repository.DeviceRepository
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class WewFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val prefs = getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()
        val deviceId = prefs.getString("device_id", null) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                SupabaseClient.client.postgrest["devices"].update(
                    buildJsonObject { put("fcm_token", token) }
                ) { filter { eq("id", deviceId) } }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        when (message.data["type"]) {
            "remote_lock" -> handleRemoteLock(message.data["locked"]?.toBoolean() ?: true)
            "add_credits" -> handleCreditsUpdated(message.data["new_balance"]?.toIntOrNull() ?: 0)
            "schedule_update" -> {
                // Reload schedules on next check cycle
                val prefs = getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("schedules_stale", true).apply()
            }
        }
    }

    private fun handleRemoteLock(locked: Boolean) {
        val prefs = getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("remote_locked", locked).apply()
        showNotification(
            title = if (locked) "phone locked" else "phone unlocked",
            body = if (locked) "your parent has locked the phone" else "your parent has unlocked the phone"
        )
    }

    private fun handleCreditsUpdated(newBalance: Int) {
        val prefs = getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("cached_credits", newBalance).apply()
        showNotification(
            title = "credits updated",
            body = "you now have $newBalance credits"
        )
    }

    private fun showNotification(title: String, body: String) {
        val notification = NotificationCompat.Builder(this, WewApplication.CHANNEL_ALERTS)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
