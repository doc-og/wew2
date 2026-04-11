package com.wew.parent.data.repository

import com.wew.parent.data.SupabaseClient
import com.wew.parent.data.model.ActivityLogEntry
import com.wew.parent.data.model.AppInfo
import com.wew.parent.data.model.CreditChange
import com.wew.parent.data.model.Device
import com.wew.parent.data.model.LocationPoint
import com.wew.parent.data.model.NotificationConfig
import com.wew.parent.data.model.Schedule
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ParentRepository {

    private val supabase get() = SupabaseClient.client

    // Auth
    suspend fun signIn(email: String, password: String) {
        supabase.auth.signInWith(io.github.jan.supabase.gotrue.providers.builtin.Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signUp(email: String, password: String) {
        supabase.auth.signUpWith(io.github.jan.supabase.gotrue.providers.builtin.Email) {
            this.email = email
            this.password = password
        }
        // Immediately sign in to establish a session.
        // This works when email confirmation is disabled in Supabase (Settings → Auth → Email).
        // If confirmation is required, signIn will fail and we surface a clear message.
        runCatching {
            supabase.auth.signInWith(io.github.jan.supabase.gotrue.providers.builtin.Email) {
                this.email = email
                this.password = password
            }
        }
        if (currentUserId() == null) {
            error("Account created — please check your email to confirm it, then sign in.")
        }
    }

    suspend fun signOut() {
        supabase.auth.signOut()
    }

    fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id

    // Ensure a public.users profile row exists for the current auth user.
    // Needed when a user's profile was wiped but their auth account survived.
    suspend fun ensureUserProfile() {
        val user = supabase.auth.currentUserOrNull() ?: return
        supabase.postgrest["users"].upsert(
            buildJsonObject {
                put("id", user.id)
                put("email", user.email ?: "")
            }
        )
    }

    // Device
    suspend fun registerDevice(deviceName: String): Device {
        val uid = currentUserId() ?: error("Not authenticated")
        ensureUserProfile()
        // Insert without trying to decode the response (avoids RLS return=representation issues)
        supabase.postgrest["devices"].insert(
            buildJsonObject {
                put("parent_user_id", uid)
                put("device_name", deviceName)
                put("current_credits", 100)
                put("daily_credit_budget", 100)
            }
        )
        // Fetch the newly created device via a separate SELECT
        return supabase.postgrest["devices"]
            .select(Columns.ALL) {
                filter {
                    eq("parent_user_id", uid)
                    eq("device_name", deviceName)
                }
            }
            .decodeList<Device>()
            .firstOrNull() ?: error("Device was inserted but could not be retrieved")
    }

    suspend fun getDeviceForParent(): Device? {
        val uid = currentUserId() ?: return null
        return supabase.postgrest["devices"]
            .select(Columns.ALL) { filter { eq("parent_user_id", uid) } }
            .decodeList<Device>()
            .firstOrNull()
    }

    suspend fun remoteLockDevice(deviceId: String, locked: Boolean) {
        supabase.postgrest["devices"].update(
            buildJsonObject { put("is_locked", locked) }
        ) { filter { eq("id", deviceId) } }
    }

    // Activity log
    suspend fun getActivityLog(deviceId: String, limit: Int = 50): List<ActivityLogEntry> {
        return supabase.postgrest["activity_log"]
            .select(Columns.ALL) {
                filter { eq("device_id", deviceId) }
                order("created_at", Order.DESCENDING)
                limit(limit.toLong())
            }
            .decodeList()
    }

    // Credits — direct table ops instead of RPC to stay compatible with supabase-kt 2.1.x
    suspend fun addCredits(deviceId: String, amount: Int, note: String?) {
        val device = supabase.postgrest["devices"]
            .select(Columns.ALL) { filter { eq("id", deviceId) } }
            .decodeSingle<Device>()
        val newBalance = device.currentCredits + amount
        supabase.postgrest["credit_ledger"].insert(
            buildJsonObject {
                put("device_id", deviceId)
                put("change_amount", amount)
                put("balance_after", newBalance)
                put("reason", "parent_add")
                note?.let { put("parent_note", it) }
            }
        )
        supabase.postgrest["devices"].update(
            buildJsonObject { put("current_credits", newBalance) }
        ) { filter { eq("id", deviceId) } }
    }

    suspend fun removeCredits(deviceId: String, amount: Int, note: String?) {
        val device = supabase.postgrest["devices"]
            .select(Columns.ALL) { filter { eq("id", deviceId) } }
            .decodeSingle<Device>()
        val newBalance = maxOf(0, device.currentCredits - amount)
        supabase.postgrest["credit_ledger"].insert(
            buildJsonObject {
                put("device_id", deviceId)
                put("change_amount", -amount)
                put("balance_after", newBalance)
                put("reason", "parent_remove")
                note?.let { put("parent_note", it) }
            }
        )
        supabase.postgrest["devices"].update(
            buildJsonObject { put("current_credits", newBalance) }
        ) { filter { eq("id", deviceId) } }
    }

    suspend fun getCreditHistory(deviceId: String, limit: Int = 50): List<CreditChange> {
        return supabase.postgrest["credit_ledger"]
            .select(Columns.ALL) {
                filter { eq("device_id", deviceId) }
                order("created_at", Order.DESCENDING)
                limit(limit.toLong())
            }
            .decodeList()
    }

    // Location
    suspend fun getLocationHistory(deviceId: String, limit: Int = 50): List<LocationPoint> {
        return supabase.postgrest["location_log"]
            .select(Columns.ALL) {
                filter { eq("device_id", deviceId) }
                order("created_at", Order.DESCENDING)
                limit(limit.toLong())
            }
            .decodeList()
    }

    // Apps
    suspend fun getInstalledApps(deviceId: String): List<AppInfo> {
        return supabase.postgrest["apps"]
            .select(Columns.ALL) { filter { eq("device_id", deviceId) } }
            .decodeList()
    }

    suspend fun updateAppWhitelist(appId: String, isWhitelisted: Boolean) {
        supabase.postgrest["apps"].update(
            buildJsonObject { put("is_whitelisted", isWhitelisted) }
        ) { filter { eq("id", appId) } }
    }

    suspend fun addApp(deviceId: String, packageName: String, appName: String): AppInfo {
        supabase.postgrest["apps"].insert(
            buildJsonObject {
                put("device_id", deviceId)
                put("package_name", packageName)
                put("app_name", appName)
                put("is_whitelisted", true)
                put("is_system_app", false)
                put("credit_cost", 1)
            }
        )
        return supabase.postgrest["apps"]
            .select(Columns.ALL) {
                filter {
                    eq("device_id", deviceId)
                    eq("package_name", packageName)
                }
            }
            .decodeList<AppInfo>()
            .firstOrNull() ?: error("App was inserted but could not be retrieved")
    }

    // Schedules
    suspend fun getSchedules(deviceId: String): List<Schedule> {
        return supabase.postgrest["schedules"]
            .select(Columns.ALL) { filter { eq("device_id", deviceId) } }
            .decodeList()
    }

    suspend fun upsertSchedule(schedule: Schedule) {
        supabase.postgrest["schedules"].upsert(schedule, onConflict = "device_id,schedule_type")
    }

    // Notification config
    suspend fun getNotificationConfig(userId: String): NotificationConfig? {
        return supabase.postgrest["notifications_config"]
            .select(Columns.ALL) { filter { eq("parent_user_id", userId) } }
            .decodeList<NotificationConfig>()
            .firstOrNull()
    }

    suspend fun upsertNotificationConfig(config: NotificationConfig) {
        supabase.postgrest["notifications_config"].upsert(config, onConflict = "parent_user_id")
    }
}
