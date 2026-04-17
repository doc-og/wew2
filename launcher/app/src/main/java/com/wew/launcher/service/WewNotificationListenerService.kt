package com.wew.launcher.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat

class WewNotificationListenerService : NotificationListenerService() {

    private val policyRefreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            runCatching { syncAllBadges() }
                .onFailure { Log.w("WewNotifListener", "policy refresh failed: ${it.message}", it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            policyRefreshReceiver,
            IntentFilter(NotificationPolicyStore.ACTION_POLICY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        runCatching { syncAllBadges() }
            .onFailure { Log.w("WewNotifListener", "initial sync failed: ${it.message}", it) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        val pkg = sbn.packageName ?: return

        if (!NotificationPolicyStore.isNotificationAllowed(this, pkg)) {
            runCatching {
                cancelNotification(sbn.key)
                NotificationPolicyStore.setBadgeVisible(this, pkg, visible = false)
            }.onFailure { Log.w("WewNotifListener", "cancel failed for $pkg: ${it.message}", it) }
            return
        }

        NotificationPolicyStore.setBadgeVisible(this, pkg, visible = true)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        val pkg = sbn.packageName ?: return
        runCatching { syncBadgeForPackage(pkg) }
            .onFailure { Log.w("WewNotifListener", "remove sync failed for $pkg: ${it.message}", it) }
    }

    private fun syncAllBadges() {
        activeNotifications
            ?.forEach { sbn ->
                val pkg = sbn.packageName ?: return@forEach
                if (!NotificationPolicyStore.isNotificationAllowed(this, pkg)) {
                    runCatching { cancelNotification(sbn.key) }
                        .onFailure { Log.w("WewNotifListener", "policy cancel failed for $pkg: ${it.message}", it) }
                }
            }

        activeNotifications
            ?.mapNotNull { it.packageName }
            ?.distinct()
            ?.forEach { syncBadgeForPackage(it) }
    }

    private fun syncBadgeForPackage(packageName: String) {
        if (!NotificationPolicyStore.isNotificationAllowed(this, packageName)) {
            NotificationPolicyStore.setBadgeVisible(this, packageName, visible = false)
            return
        }

        val hasVisibleNotification = activeNotifications
            ?.any { it.packageName == packageName }
            ?: false
        NotificationPolicyStore.setBadgeVisible(this, packageName, visible = hasVisibleNotification)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(policyRefreshReceiver) }
        super.onDestroy()
    }
}
