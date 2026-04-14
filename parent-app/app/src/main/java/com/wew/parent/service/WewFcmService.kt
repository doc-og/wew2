package com.wew.parent.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.wew.parent.MainActivity
import com.wew.parent.WewParentApplication

class WewFcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Store token for potential use; parent app doesn't need to send token to device DB
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data["type"] ?: return
        val (title, body) = when (type) {
            "low_credits" -> {
                val remaining = message.data["remaining"] ?: "?"
                "credits running low" to "your child has $remaining credits left"
            }
            "credits_exhausted" -> {
                "credits used up" to "your child has run out of credits for today"
            }
            "blocked_app_attempt" -> {
                val app = message.data["app_name"] ?: "an app"
                "blocked access" to "your child tried to open $app, which isn't on the approved list"
            }
            "device_admin_revoked" -> {
                "security alert" to "wew device admin was disabled on your child's phone — phone is now in emergency-only mode"
            }
            "daily_summary" -> {
                val used = message.data["credits_used"] ?: "?"
                "daily summary" to "your child used $used credits today"
            }
            "location_update" -> {
                "location updated" to "your child's location has been refreshed"
            }
            "check_in" -> {
                val title = message.notification?.title ?: "📍 Child checked in"
                val body = message.notification?.body
                    ?: message.data["message"]?.takeIf { it.isNotBlank() }
                    ?: "your child shared their location"
                title to body
            }
            else -> return
        }
        showNotification(type.hashCode(), title, body)
    }

    private fun showNotification(id: Int, title: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, WewParentApplication.CHANNEL_ALERTS)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        getSystemService(NotificationManager::class.java).notify(id, notification)
    }
}
