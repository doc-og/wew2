package com.wew.launcher.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.wew.launcher.data.SupabaseClient
import com.wew.launcher.data.model.ActivityLog
import com.wew.launcher.data.model.ActionType
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class WewDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        val prefs = context.getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", null) ?: return

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                // Log tamper event — Supabase realtime will push alert to parent app
                SupabaseClient.client.postgrest["activity_log"].insert(
                    ActivityLog(
                        deviceId = deviceId,
                        actionType = ActionType.DEVICE_ADMIN_REVOKED.value,
                        creditsDeducted = 0
                    )
                )
                // Flag device as tampered so parent dashboard shows alert
                SupabaseClient.client.postgrest["devices"].update(
                    buildJsonObject { put("is_tampered", true) }
                ) { filter { eq("id", deviceId) } }
            }
        }

        // Enter emergency-only mode
        prefs.edit().putBoolean("emergency_mode", true).apply()

        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("emergency_mode", true)
            }
        if (launchIntent != null) context.startActivity(launchIntent)
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        val prefs = context.getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("device_admin_active", true).apply()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Removing wew device admin will lock the phone to emergency-only mode and alert your parent."
    }
}
