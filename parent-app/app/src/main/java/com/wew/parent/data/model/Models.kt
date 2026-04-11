package com.wew.parent.data.model

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

@Serializable
data class ActivityLogEntry(
    val id: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("action_type") val actionType: String,
    @SerialName("app_package") val appPackage: String? = null,
    @SerialName("app_name") val appName: String? = null,
    @SerialName("credits_deducted") val creditsDeducted: Int = 0,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class CreditChange(
    val id: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("change_amount") val changeAmount: Int,
    @SerialName("balance_after") val balanceAfter: Int,
    val reason: String,
    @SerialName("parent_note") val parentNote: String? = null,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class LocationPoint(
    val id: String,
    @SerialName("device_id") val deviceId: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("accuracy_meters") val accuracyMeters: Float? = null,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class AppInfo(
    val id: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("app_name") val appName: String,
    @SerialName("is_whitelisted") val isWhitelisted: Boolean = false,
    @SerialName("is_system_app") val isSystemApp: Boolean = false,
    @SerialName("credit_cost") val creditCost: Int = 1
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

@Serializable
data class NotificationConfig(
    val id: String? = null,
    @SerialName("parent_user_id") val parentUserId: String,
    @SerialName("low_credit_threshold_pct") val lowCreditThresholdPct: Int = 20,
    @SerialName("daily_summary_enabled") val dailySummaryEnabled: Boolean = true,
    @SerialName("daily_summary_time") val dailySummaryTime: String = "20:00:00",
    @SerialName("notify_blocked_apps") val notifyBlockedApps: Boolean = true,
    @SerialName("notify_tamper_attempts") val notifyTamperAttempts: Boolean = true,
    @SerialName("notify_location_updates") val notifyLocationUpdates: Boolean = false
)
