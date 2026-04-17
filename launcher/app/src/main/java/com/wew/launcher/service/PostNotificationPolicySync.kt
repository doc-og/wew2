package com.wew.launcher.service

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.wew.launcher.data.model.AppRecord

/**
 * When this app is the **device owner**, Android allows granting or denying
 * [Manifest.permission.POST_NOTIFICATIONS] for other packages without opening system settings.
 *
 * **Device admin only** (non-owner provisioning) cannot set other apps’ notification permission;
 * this call is then a no-op. Silencing third-party notifications from the parent dashboard in
 * that mode would require capabilities Android does not expose without device owner or the
 * notification-listener access screen (which WeW does not send users to).
 */
object PostNotificationPolicySync {

    fun sync(context: Context, apps: List<AppRecord>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return
        if (!dpm.isDeviceOwnerApp(context.packageName)) return

        val admin = ComponentName(context, WewDeviceAdminReceiver::class.java)
        for (p in apps) {
            if (p.packageName == context.packageName) continue
            val allowed = p.isWhitelisted && p.notificationsEnabled
            val state = if (allowed) {
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            } else {
                DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
            }
            runCatching {
                dpm.setPermissionGrantState(
                    admin,
                    p.packageName,
                    Manifest.permission.POST_NOTIFICATIONS,
                    state
                )
            }.onFailure { Log.e("WewPostNotif", "${p.packageName}: ${it.message}", it) }
        }
    }
}
