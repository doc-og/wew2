package com.wew.launcher.data.model

import android.graphics.drawable.Drawable
import com.wew.launcher.telecom.PhoneMatch
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────────────
// Device
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class Device(
    val id: String,
    @SerialName("parent_user_id")    val parentUserId: String,
    @SerialName("device_name")       val deviceName: String,
    @SerialName("fcm_token")         val fcmToken: String?       = null,
    @SerialName("is_locked")         val isLocked: Boolean       = false,
    // Token system (active)
    @SerialName("current_tokens")    val currentTokens: Int      = 10000,
    @SerialName("daily_token_budget") val dailyTokenBudget: Int  = 10000,
    @SerialName("tokens_reset_time") val tokensResetTime: String = "00:00:00",
    /** Parent phone for SOS / WeW Parent thread (E.164 recommended). Set from parent app. */
    @SerialName("parent_phone") val parentPhone: String? = null,
    @SerialName("parent_display_name") val parentDisplayName: String? = null,
    /** Android SMS thread_id for parent conversation — synced from launcher for daily summaries. */
    @SerialName("parent_sms_thread_id") val parentSmsThreadId: String? = null,
    @SerialName("timezone") val timezone: String = "America/Los_Angeles",
    // Legacy credit fields — kept so the DB column is still readable; no longer driven by app
    @SerialName("current_credits")      val currentCredits: Int      = 0,
    @SerialName("daily_credit_budget")  val dailyCreditBudget: Int   = 0,
    @SerialName("last_seen_at")      val lastSeenAt: String?     = null,
    /** Pushed from launcher: child is default SMS app (parent dashboard). */
    @SerialName("child_default_sms_app") val childDefaultSmsApp: Boolean = false,
    /** Pushed from launcher: READ/RECEIVE/SEND SMS granted on child. */
    @SerialName("child_sms_permissions_ok") val childSmsPermissionsOk: Boolean = false,
    @SerialName("child_messaging_health_at") val childMessagingHealthAt: String? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// App models
// ─────────────────────────────────────────────────────────────────────────────

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isWhitelisted: Boolean,
    val tokenCost: Int = 5,       // base token cost for opening this app (ActionType.APP_OPEN default)
    val isSystemDefault: Boolean = false
)

// AppRecord: used when reading back app rows from Supabase (includes server-generated id).
@Serializable
data class AppRecord(
    val id: String? = null,
    @SerialName("device_id")    val deviceId: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("app_name")     val appName: String,
    @SerialName("is_whitelisted") val isWhitelisted: Boolean = false,
    @SerialName("notifications_enabled") val notificationsEnabled: Boolean = false,
    @SerialName("is_system_app")  val isSystemApp: Boolean   = false,
    @SerialName("credit_cost")    val creditCost: Int        = 1,   // legacy column; token cost comes from TokenEngine
    /** When set, foreground time in this app bills as this media action (see TOKEN_SYSTEM.md). */
    @SerialName("media_action_type") val mediaActionType: String? = null
)

// AppSyncRecord: ONLY for batch upsert from child device (no id field — PostgREST requirement).
@Serializable
data class AppSyncRecord(
    @SerialName("device_id")    val deviceId: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("app_name")     val appName: String,
    @SerialName("is_whitelisted") val isWhitelisted: Boolean = false,
    @SerialName("is_system_app")  val isSystemApp: Boolean   = false,
    @SerialName("credit_cost")    val creditCost: Int        = 1
)

// ─────────────────────────────────────────────────────────────────────────────
// Action types
// ─────────────────────────────────────────────────────────────────────────────

enum class ActionType(val value: String) {
    // Messaging
    SMS_SENT("sms_sent"),
    SMS_RECEIVED("sms_received"),
    MMS_SENT("mms_sent"),
    // Calls
    CALL_MADE("call_made"),
    CALL_RECEIVED("call_received"),
    VIDEO_CALL_MADE("video_call_made"),
    // Media / attention-heavy foreground sessions (per TOKEN_SYSTEM.md)
    PHOTO_TAKEN("photo_taken"),
    VIDEO_WATCHED("video_watched"),
    GAME_SESSION("game_session"),
    SOCIAL_SCROLL("social_scroll"),
    AUDIO_STREAMED("audio_streamed"),
    // Web
    WEB_SESSION("web_session"),
    URL_BLOCKED("url_blocked"),
    // Apps
    APP_OPEN("app_open"),
    APP_BLOCKED("app_blocked"),
    TEMP_ACCESS_GRANTED("temp_access_granted"),
    // Location
    CHECK_IN("check_in"),
    // Tokens
    TOKEN_REQUEST("token_request"),
    TOKEN_EXHAUSTED("token_exhausted"),
    // Contacts
    CONTACT_REQUESTED("contact_requested"),
    CONTACT_QUARANTINED("contact_quarantined"),
    // System / security
    SETTINGS_TAMPER("settings_tamper"),
    DEVICE_ADMIN_REVOKED("device_admin_revoked"),
    LOCK_ACTIVATED("lock_activated"),
    LOCK_DEACTIVATED("lock_deactivated");

    companion object {
        fun fromValue(value: String) = entries.firstOrNull { it.value == value }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Supabase messages (system summaries merged into parent chat on device)
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class SupabaseSystemMessage(
    val id: String,
    val body: String? = null,
    @SerialName("message_type") val messageType: String = "daily_summary",
    @SerialName("created_at") val createdAt: String
)

// ─────────────────────────────────────────────────────────────────────────────
// Activity log
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class ActivityLog(
    val id: String? = null,
    @SerialName("device_id")       val deviceId: String,
    @SerialName("action_type")     val actionType: String,
    @SerialName("app_package")     val appPackage: String?  = null,
    @SerialName("app_name")        val appName: String?     = null,
    @SerialName("credits_deducted") val creditsDeducted: Int = 0,   // legacy; kept for DB compat
    @SerialName("tokens_consumed") val tokensConsumed: Int  = 0,
    val metadata: Map<String, String> = emptyMap(),
    @SerialName("created_at")      val createdAt: String?   = null
)

// ─────────────────────────────────────────────────────────────────────────────
// Token system
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class TokenLedger(
    val id: String? = null,
    @SerialName("device_id")        val deviceId: String,
    @SerialName("action_type")      val actionType: String,
    @SerialName("tokens_consumed")  val tokensConsumed: Int,
    @SerialName("context_metadata") val contextMetadata: Map<String, String> = emptyMap(),
    @SerialName("balance_after")    val balanceAfter: Int,
    @SerialName("created_at")       val createdAt: String? = null
)

@Serializable
data class TokenBudget(
    @SerialName("device_id")       val deviceId: String,
    @SerialName("daily_limit")     val dailyLimit: Int     = 10000,
    @SerialName("rollover_enabled") val rolloverEnabled: Boolean = false,
    @SerialName("created_at")      val createdAt: String?  = null,
    @SerialName("updated_at")      val updatedAt: String?  = null
)

@Serializable
data class TokenActionCost(
    val id: String? = null,
    @SerialName("device_id")      val deviceId: String,
    @SerialName("action_type")    val actionType: String,
    @SerialName("base_cost")      val baseCost: Int      = 0,
    @SerialName("cost_per_unit")  val costPerUnit: Int   = 0,
    @SerialName("unit_type")      val unitType: String?  = null
)

@Serializable
data class TokenRequest(
    val id: String? = null,
    @SerialName("device_id")        val deviceId: String,
    @SerialName("app_package")      val appPackage: String?  = null,
    @SerialName("app_name")         val appName: String?     = null,
    @SerialName("tokens_requested") val tokensRequested: Int = 1000,
    val reason: String?             = null,
    val status: String              = "pending",
    @SerialName("parent_note")      val parentNote: String?  = null,
    @SerialName("created_at")       val createdAt: String?   = null,
    @SerialName("updated_at")       val updatedAt: String?   = null
)

// ─────────────────────────────────────────────────────────────────────────────
// Legacy credit ledger — kept so existing DB writes don't break during rollout
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class CreditLedger(
    val id: String? = null,
    @SerialName("device_id")     val deviceId: String,
    @SerialName("change_amount") val changeAmount: Int,
    @SerialName("balance_after") val balanceAfter: Int,
    val reason: String,
    @SerialName("action_type")   val actionType: String? = null,
    @SerialName("parent_note")   val parentNote: String? = null,
    @SerialName("created_at")    val createdAt: String?  = null
)

// ─────────────────────────────────────────────────────────────────────────────
// Location
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class LocationLog(
    val id: String? = null,
    @SerialName("device_id")        val deviceId: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("accuracy_meters") val accuracyMeters: Float? = null,
    @SerialName("created_at")       val createdAt: String?    = null
)

// ─────────────────────────────────────────────────────────────────────────────
// Access schedule (per-day phone hours set by parent)
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class AccessScheduleDay(
    val id: String? = null,
    @SerialName("device_id")      val deviceId: String,
    /** 0 = Sunday, 1 = Monday, …, 6 = Saturday (matches Java DayOfWeek.value % 7) */
    @SerialName("day_of_week")    val dayOfWeek: Int,
    @SerialName("is_enabled")     val isEnabled: Boolean = true,
    /** "HH:mm:ss" 24-hour time */
    @SerialName("allowed_start")  val allowedStart: String = "07:00:00",
    @SerialName("allowed_end")    val allowedEnd: String   = "21:00:00"
)

// ─────────────────────────────────────────────────────────────────────────────
// Schedule
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class Schedule(
    val id: String? = null,
    @SerialName("device_id")    val deviceId: String,
    @SerialName("schedule_type") val scheduleType: String,
    @SerialName("start_time")   val startTime: String,
    @SerialName("end_time")     val endTime: String,
    @SerialName("days_of_week") val daysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5),
    @SerialName("is_enabled")   val isEnabled: Boolean    = true
)

// ─────────────────────────────────────────────────────────────────────────────
// Passcode + temp access
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class DevicePasscodeRecord(
    @SerialName("device_id")     val deviceId: String,
    @SerialName("passcode_hash") val passcodeHash: String,
    @SerialName("created_at")    val createdAt: String? = null
)

@Serializable
data class TempAppAccess(
    val id: String? = null,
    @SerialName("device_id")    val deviceId: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("expires_at")   val expiresAt: String,
    @SerialName("granted_by")   val grantedBy: String  = "passcode",
    @SerialName("created_at")   val createdAt: String? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// Contacts
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class WewContact(
    val id: String? = null,
    @SerialName("device_id")    val deviceId: String,
    val name: String,
    @SerialName("first_name")   val firstName: String?     = null,
    @SerialName("last_name")    val lastName: String?      = null,
    val nickname: String?       = null,
    val phone: String?          = null,
    val email: String?          = null,
    val relationship: String?   = null,
    val address: String?        = null,
    @SerialName("photo_url")    val photoUrl: String?      = null,
    @SerialName("is_authorized") val isAuthorized: Boolean = false,
    /** Parent app: requested | approved | blocked (optional on older rows). */
    val status: String?         = null,
    val notes: String?          = null,
    @SerialName("created_at")   val createdAt: String?     = null,
    @SerialName("updated_at")   val updatedAt: String?     = null
)

/** Display line for UI — mirrors parent app [Contact.displayName]. */
fun WewContact.composedDisplayName(): String =
    listOfNotNull(
        firstName?.trim()?.takeIf { it.isNotEmpty() },
        lastName?.trim()?.takeIf { it.isNotEmpty() }
    ).joinToString(" ")
        .ifBlank {
            nickname?.trim()?.takeIf { it.isNotEmpty() } ?: name.trim()
        }

private fun WewContact.contactSearchHaystack(): String {
    val parts = listOfNotNull(
        name.takeIf { it.isNotBlank() },
        firstName?.takeIf { !it.isNullOrBlank() },
        lastName?.takeIf { !it.isNullOrBlank() },
        nickname?.takeIf { !it.isNullOrBlank() },
        phone?.takeIf { !it.isNullOrBlank() },
        relationship?.takeIf { !it.isNullOrBlank() },
        email?.takeIf { !it.isNullOrBlank() },
        address?.takeIf { !it.isNullOrBlank() },
        notes?.takeIf { !it.isNullOrBlank() },
    )
    return parts.joinToString(" \n ").lowercase()
}

/** Case-insensitive substring match on structured contact fields; phone also matches digit-only query. */
fun WewContact.matchesContactSearch(rawQuery: String): Boolean {
    val q = rawQuery.trim().lowercase()
    if (q.isEmpty()) return false
    if (contactSearchHaystack().contains(q)) return true
    val qDigits = PhoneMatch.digitsOnly(q)
    if (qDigits.isNotEmpty()) {
        val pDigits = PhoneMatch.digitsOnly(phone.orEmpty())
        if (pDigits.contains(qDigits)) return true
    }
    return false
}

/** Parent has approved this person for calls/SMS (and we have a phone to match threads). */
fun WewContact.isApprovedForComms(): Boolean {
    if (phone.isNullOrBlank()) return false
    return when (status?.lowercase()) {
        "blocked", "denied" -> false
        "requested" -> false
        "approved" -> true
        null, "" -> isAuthorized
        else -> isAuthorized
    }
}

@Serializable
data class ContactAuthRequest(
    val id: String? = null,
    @SerialName("device_id")  val deviceId: String,
    @SerialName("contact_id") val contactId: String,
    val status: String        = "pending",
    @SerialName("created_at") val createdAt: String? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// Chat / SMS infrastructure
// ─────────────────────────────────────────────────────────────────────────────

/** Metadata for an SMS/MMS thread stored in Supabase (content stays on device). */
@Serializable
data class ConversationMeta(
    val id: String? = null,
    @SerialName("device_id")    val deviceId: String,
    @SerialName("thread_id")    val threadId: String,       // Android SMS thread_id
    @SerialName("display_name") val displayName: String?    = null,
    @SerialName("is_pinned")    val isPinned: Boolean       = false,
    @SerialName("is_muted")     val isMuted: Boolean        = false,
    @SerialName("created_at")   val createdAt: String?      = null,
    @SerialName("updated_at")   val updatedAt: String?      = null
)

/** Metadata mirror of a sent/received message logged to Supabase for parent visibility. */
@Serializable
data class MessageLog(
    val id: String? = null,
    @SerialName("device_id")       val deviceId: String,
    @SerialName("thread_id")       val threadId: String,
    @SerialName("sender_address")  val senderAddress: String,
    @SerialName("sender_type")     val senderType: String,        // child | contact | parent | system
    @SerialName("message_type")    val messageType: String,       // text | mms_image | mms_video | mms_audio | location | call_summary
    @SerialName("has_media")       val hasMedia: Boolean          = false,
    @SerialName("thumbnail_url")   val thumbnailUrl: String?      = null,
    @SerialName("tokens_consumed") val tokensConsumed: Int        = 0,
    /**
     * Plain-text body of the message, if any. Mirrored so a row in content://sms
     * that later gets lost/wiped can still be recovered (or at least read by the
     * parent dashboard). Null for media-only MMS and for system rows that use
     * [body] via the system-messages path.
     */
    val body: String? = null,
    @SerialName("created_at")      val createdAt: String?         = null
)

// ─────────────────────────────────────────────────────────────────────────────
// URL filtering
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class UrlFilter(
    val id: String? = null,
    @SerialName("device_id")    val deviceId: String?   = null,
    @SerialName("parent_id")    val parentId: String?   = null,
    @SerialName("url_pattern")  val urlPattern: String,
    @SerialName("filter_type")  val filterType: String,          // allow | block
    @SerialName("is_global")    val isGlobal: Boolean   = false,
    @SerialName("created_by")   val createdBy: String   = "parent",
    @SerialName("created_at")   val createdAt: String?  = null
)

@Serializable
data class UrlAccessRequest(
    val id: String? = null,
    @SerialName("device_id")   val deviceId: String,
    val url: String,
    @SerialName("page_title")  val pageTitle: String?  = null,
    val status: String         = "pending",
    @SerialName("created_at")  val createdAt: String?  = null
)
