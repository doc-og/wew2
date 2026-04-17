package com.wew.launcher.service

import android.content.Context
import android.content.Intent
import com.wew.launcher.data.model.AppRecord

object NotificationPolicyStore {
    const val ACTION_BADGES_CHANGED = "com.wew.launcher.NOTIFICATION_BADGES_CHANGED"
    const val ACTION_POLICY_CHANGED = "com.wew.launcher.NOTIFICATION_POLICY_CHANGED"

    private const val PREFS_NAME = "wew_prefs"
    private const val KEY_ALLOWED_PACKAGES = "notification_allowed_packages"
    private const val KEY_BADGE_PACKAGES = "notification_badge_packages"

    fun writePolicies(context: Context, apps: List<AppRecord>) {
        val allowedPackages = apps
            .filter { it.isWhitelisted && it.notificationsEnabled }
            .mapTo(linkedSetOf()) { it.packageName }
        val visibleBadges = getBadgePackages(context).intersect(allowedPackages)

        prefs(context)
            .edit()
            .putStringSet(KEY_ALLOWED_PACKAGES, allowedPackages)
            .putStringSet(KEY_BADGE_PACKAGES, visibleBadges)
            .apply()

        notifyBadgesChanged(context)
        context.sendBroadcast(Intent(ACTION_POLICY_CHANGED).setPackage(context.packageName))
        PostNotificationPolicySync.sync(context, apps)
    }

    fun isNotificationAllowed(context: Context, packageName: String): Boolean {
        if (packageName == context.packageName) return true
        return prefs(context).getStringSet(KEY_ALLOWED_PACKAGES, emptySet()).orEmpty().contains(packageName)
    }

    fun getBadgePackages(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_BADGE_PACKAGES, emptySet()).orEmpty()
    }

    fun clearBadge(context: Context, packageName: String) {
        val updated = getBadgePackages(context).toMutableSet().apply {
            remove(packageName)
        }
        prefs(context).edit().putStringSet(KEY_BADGE_PACKAGES, updated).apply()
        notifyBadgesChanged(context)
    }

    fun setBadgeVisible(context: Context, packageName: String, visible: Boolean) {
        if (packageName == context.packageName) return

        val updated = getBadgePackages(context).toMutableSet().apply {
            if (visible) add(packageName) else remove(packageName)
        }
        prefs(context).edit().putStringSet(KEY_BADGE_PACKAGES, updated).apply()
        notifyBadgesChanged(context)
    }

    private fun notifyBadgesChanged(context: Context) {
        context.sendBroadcast(Intent(ACTION_BADGES_CHANGED).setPackage(context.packageName))
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
