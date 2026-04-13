package com.wew.launcher.data.model

import android.graphics.drawable.Drawable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val id: String,
    @SerialName("parent_user_id") val parentUserId: String,
    @SerialName("device_name") val deviceName: String,
    @SerialName("fcm_token") val fcmToken: String? = null,
    @SerialName("is_locked") val isLocked: Boolean = false,
    @SerialName("current_credits") val currentCredits: Int = 100,
    @SerialName("daily_credit_budget") val dailyCreditBudget: Int = 100,
    @SerialName("credits_reset_time") val creditsResetTime: String = "07:00:00",
    @SerialName("last_seen_at") val lastSeenAt: String? = null
)

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isWhitelisted: Boolean,
    val creditCost: Int = 1,
    val isSystemDefault: Boolean = false
)

@Serializable
data class ActivityLog(
    val id: String? = null,
    @SerialName("device_id") val deviceId: String,
    @SerialName("action_type") val actionType: String,
    @SerialName("app_package") val appPackage: String? = null,
    @SerialName("app_name") val appName: String? = null,
    @SerialName("credits_deducted") val creditsDeducted: Int = 0,
    val metadata: Map<String, String> = emptyMap(),
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class CreditLedger(
    val id: String? = null,
    @SerialName("device_id") val deviceId: String,
    @SerialName("change_amount") val changeAmount: Int,
    @SerialName("balance_after") val balanceAfter: Int,
    val reason: String,
    @SerialName("action_type") val actionType: String? = null,
    @SerialName("parent_note") val parentNote: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class LocationLog(
    val id: String? = null,
    @SerialName("device_id") val deviceId: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("accuracy_meters") val accuracyMeters: Float? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Schedule(
    val id: String? = null,
    @SerialName("device_id") val deviceId: String,
    @SerialName("schedule_type") val scheduleType: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("days_of_week") val daysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5),
    @SerialName("is_enabled") val isEnabled: Boolean = true
)

// AppRecord is used when reading back apps (includes server-generated id).
@Serializable
data class AppRecord(
    val id: String? = null,
    @SerialName("device_id") val deviceId: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("app_name") val appName: String,
    @SerialName("is_whitelisted") val isWhitelisted: Boolean = false,
    @SerialName("is_system_app") val isSystemApp: Boolean = false,
    @SerialName("credit_cost") val creditCost: Int = 1
)

// AppSyncRecord is used ONLY for the batch upsert from the child device.
// It intentionally omits `id` so every object in the JSON array has identical keys,
// which PostgREST requires for batch upserts ("All object keys must match").
@Serializable
data class AppSyncRecord(
    @SerialName("device_id") val deviceId: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("app_name") val appName: String,
    @SerialName("is_whitelisted") val isWhitelisted: Boolean = false,
    @SerialName("is_system_app") val isSystemApp: Boolean = false,
    @SerialName("credit_cost") val creditCost: Int = 1
)

enum class ActionType(val value: String, val baseCost: Int) {
    APP_OPEN("app_open", 1),
    MESSAGE_SENT("message_sent", 1),
    CALL_MADE("call_made", 2),
    CALL_RECEIVED("call_received", 2),
    PHOTO_TAKEN("photo_taken", 2),
    PHOTO_SHARED("photo_shared", 5),
    WEB_LINK_OPENED("web_link_opened", 2),
    APP_BLOCKED("app_blocked", 0),
    SETTINGS_TAMPER("settings_tamper", 0),
    DEVICE_ADMIN_REVOKED("device_admin_revoked", 0),
    LOCK_ACTIVATED("lock_activated", 0),
    LOCK_DEACTIVATED("lock_deactivated", 0),
    CREDIT_EXHAUSTED("credit_exhausted", 0),
    TEMP_ACCESS_GRANTED("temp_access_granted", 2),
    CHECK_IN("check_in", 0),
    CONTACT_REQUESTED("contact_requested", 0);

    companion object {
        fun fromValue(value: String) = entries.firstOrNull { it.value == value }
    }
}

@Serializable
data class DevicePasscodeRecord(
    @SerialName("device_id") val deviceId: String,
    @SerialName("passcode_hash") val passcodeHash: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class TempAppAccess(
    val id: String? = null,
    @SerialName("device_id") val deviceId: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("granted_by") val grantedBy: String = "passcode",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class WewContact(
    val id: String? = null,
    @SerialName("device_id") val deviceId: String,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("is_authorized") val isAuthorized: Boolean = false,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class ContactAuthRequest(
    val id: String? = null,
    @SerialName("device_id") val deviceId: String,
    @SerialName("contact_id") val contactId: String,
    val status: String = "pending",
    @SerialName("created_at") val createdAt: String? = null
)
