package com.wew.launcher.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.wew.launcher.data.SupabaseClient
import com.wew.launcher.data.model.ActivityLog
import com.wew.launcher.data.model.AppRecord
import com.wew.launcher.data.model.AppSyncRecord
import com.wew.launcher.data.model.ContactAuthRequest
import com.wew.launcher.data.model.CreditLedger
import com.wew.launcher.data.model.Device
import com.wew.launcher.data.model.DevicePasscodeRecord
import com.wew.launcher.data.model.LocationLog
import com.wew.launcher.data.model.Schedule
import com.wew.launcher.data.model.TempAppAccess
import com.wew.launcher.data.model.WewContact
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DeviceRepository(private val context: Context) {

    private val supabase get() = SupabaseClient.client

    suspend fun getDevice(deviceId: String): Device {
        return supabase.postgrest["devices"]
            .select(Columns.ALL) { filter { eq("id", deviceId) } }
            .decodeSingle()
    }

    suspend fun updateFcmToken(deviceId: String, token: String) {
        supabase.postgrest["devices"].update(
            buildJsonObject { put("fcm_token", token) }
        ) { filter { eq("id", deviceId) } }
    }

    suspend fun updateLastSeen(deviceId: String) {
        supabase.postgrest["devices"].update(
            buildJsonObject { put("last_seen_at", "now()") }
        ) { filter { eq("id", deviceId) } }
    }

    /**
     * Deducts credits by inserting a ledger entry and updating device balance directly.
     * The deduct_credits DB function is called via the activity_log trigger on the backend.
     */
    suspend fun deductCredits(
        deviceId: String,
        amount: Int,
        actionType: String,
        appPackage: String? = null,
        appName: String? = null
    ): Result<Int> = runCatching {
        // Fetch current balance
        val device = getDevice(deviceId)
        val newBalance = maxOf(0, device.currentCredits - amount)

        // Write ledger entry
        supabase.postgrest["credit_ledger"].insert(
            CreditLedger(
                deviceId = deviceId,
                changeAmount = -amount,
                balanceAfter = newBalance,
                reason = actionType
            )
        )

        // Update device balance
        supabase.postgrest["devices"].update(
            buildJsonObject { put("current_credits", newBalance) }
        ) { filter { eq("id", deviceId) } }

        // Log activity
        supabase.postgrest["activity_log"].insert(
            ActivityLog(
                deviceId = deviceId,
                actionType = actionType,
                appPackage = appPackage,
                appName = appName,
                creditsDeducted = amount
            )
        )

        newBalance
    }

    suspend fun logActivity(log: ActivityLog) {
        supabase.postgrest["activity_log"].insert(log)
    }

    suspend fun logLocation(log: LocationLog) {
        supabase.postgrest["location_log"].insert(log)
    }

    suspend fun getWhitelistedApps(deviceId: String): List<AppRecord> {
        return supabase.postgrest["apps"]
            .select(Columns.ALL) {
                filter {
                    eq("device_id", deviceId)
                    eq("is_whitelisted", true)
                }
            }
            .decodeList()
    }

    suspend fun syncAppList(deviceId: String, context: Context) {
        val pm = context.packageManager
        val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }

        // Build JsonObject per record so PostgREST gets a uniform key set for every row.
        // Using typed serialisation here has repeatedly produced "All object keys must match"
        // errors even with identical model classes — building JSON manually is the safe path.
        val jsonRecords = installedApps.map { appInfo ->
            buildJsonObject {
                put("device_id", deviceId)
                put("package_name", appInfo.packageName)
                put("app_name", pm.getApplicationLabel(appInfo).toString())
                put("is_whitelisted", DEFAULT_WHITELIST.contains(appInfo.packageName))
                put("is_system_app", (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0)
                put("credit_cost", 1)
            }
        }

        jsonRecords.chunked(50).forEach { chunk ->
            supabase.postgrest["apps"].upsert(chunk, onConflict = "device_id,package_name")
        }
    }

    suspend fun getSchedules(deviceId: String): List<Schedule> {
        return supabase.postgrest["schedules"]
            .select(Columns.ALL) {
                filter {
                    eq("device_id", deviceId)
                    eq("is_enabled", true)
                }
            }
            .decodeList()
    }

    fun observeDevice(deviceId: String): Flow<Device> = flow {
        val channel = supabase.realtime.channel("device-$deviceId")
        channel.subscribe()
        emit(getDevice(deviceId))
    }

    suspend fun getDevicePasscode(deviceId: String): DevicePasscodeRecord? {
        return runCatching {
            supabase.postgrest["device_passcode"]
                .select(Columns.ALL) { filter { eq("device_id", deviceId) } }
                .decodeList<DevicePasscodeRecord>()
                .firstOrNull()
        }.getOrElse { Log.e("WewSync", "getDevicePasscode failed: ${it.message}"); null }
    }

    suspend fun grantTempAccess(deviceId: String, packageName: String, expiresAt: String) {
        supabase.postgrest["temporary_app_access"].upsert(
            buildJsonObject {
                put("device_id", deviceId)
                put("package_name", packageName)
                put("expires_at", expiresAt)
                put("granted_by", "passcode")
            },
            onConflict = "device_id,package_name"
        )
    }

    suspend fun getActiveTempAccess(deviceId: String): List<TempAppAccess> {
        return runCatching {
            supabase.postgrest["temporary_app_access"]
                .select(Columns.ALL) { filter { eq("device_id", deviceId) } }
                .decodeList<TempAppAccess>()
        }.getOrElse { Log.e("WewSync", "getTempAccess failed: ${it.message}"); emptyList() }
    }

    // ── Contacts ──────────────────────────────────────────────────────────────────

    suspend fun getContacts(deviceId: String): List<WewContact> {
        return runCatching {
            supabase.postgrest["contacts"]
                .select(Columns.ALL) { filter { eq("device_id", deviceId) } }
                .decodeList()
        }.getOrElse { Log.e("WewContacts", "getContacts failed: ${it.message}"); emptyList() }
    }

    suspend fun createContact(
        deviceId: String,
        name: String,
        phone: String?,
        email: String?,
        address: String?,
        photoUrl: String?,
        notes: String?
    ): String? {
        return runCatching {
            supabase.postgrest["contacts"].insert(
                buildJsonObject {
                    put("device_id", deviceId)
                    put("name", name)
                    phone?.let { put("phone", it) }
                    email?.let { put("email", it) }
                    address?.let { put("address", it) }
                    photoUrl?.let { put("photo_url", it) }
                    notes?.let { put("notes", it) }
                    put("is_authorized", false)
                }
            )
            // Fetch the contact we just created to get its id
            supabase.postgrest["contacts"]
                .select(Columns.ALL) {
                    filter {
                        eq("device_id", deviceId)
                        eq("name", name)
                    }
                }
                .decodeList<WewContact>()
                .maxByOrNull { it.createdAt ?: "" }
                ?.id
        }.getOrElse { Log.e("WewContacts", "createContact failed: ${it.message}"); null }
    }

    suspend fun requestContactAuthorization(deviceId: String, contactId: String) {
        runCatching {
            supabase.postgrest["contact_auth_requests"].upsert(
                buildJsonObject {
                    put("device_id", deviceId)
                    put("contact_id", contactId)
                    put("status", "pending")
                },
                onConflict = "device_id,contact_id"
            )
        }.onFailure { Log.e("WewContacts", "requestAuth failed: ${it.message}") }
    }

    suspend fun getAuthRequests(deviceId: String): List<ContactAuthRequest> {
        return runCatching {
            supabase.postgrest["contact_auth_requests"]
                .select(Columns.ALL) { filter { eq("device_id", deviceId) } }
                .decodeList()
        }.getOrElse { Log.e("WewContacts", "getAuthRequests failed: ${it.message}"); emptyList() }
    }

    companion object {
        val DEFAULT_WHITELIST = setOf(
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.mms",
            "com.google.android.apps.messaging",
            "com.android.contacts",
            "com.google.android.contacts",
            "com.google.android.apps.maps",
            "com.wew.parent",
            "com.wew.launcher.contacts"
        )
    }
}
