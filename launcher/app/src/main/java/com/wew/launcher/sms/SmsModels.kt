package com.wew.launcher.sms

/**
 * Local (on-device) SMS/MMS data models.
 * These are read from Android's SMS ContentProvider — they do NOT map to Supabase.
 * Only metadata is mirrored to Supabase (see MessageLog in Models.kt).
 */

/** A conversation thread — represents one contact or group in the conversation list. */
data class SmsThread(
    val threadId: Long,
    /** Comma-separated phone numbers for this thread. */
    val address: String,
    /** Resolved display name from WewContacts, or formatted phone number. */
    val displayName: String,
    val snippet: String,
    val date: Long,
    val unreadCount: Int,
    val messageCount: Int,
    val lastMessageType: SmsMessageType,
    /** True if any participant is an approved WewContact. */
    val isApproved: Boolean = false,
    /** True if at least one participant is the parent. */
    val isParent: Boolean = false,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false
)

/** A single SMS or MMS message within a thread. */
data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val direction: SmsDirection,
    val isRead: Boolean,
    val type: SmsMessageType,
    /** Populated for MMS messages. */
    val attachments: List<MmsAttachment> = emptyList(),
    val isSending: Boolean = false,
    val isFailed: Boolean = false
)

/** An attachment part within an MMS message. */
data class MmsAttachment(
    val partId: Long,
    val contentType: String,   // e.g. "image/jpeg", "video/mp4", "audio/aac"
    val name: String?,
    /** content:// URI pointing to this part on-device. */
    val contentUri: String
)

enum class SmsDirection { INCOMING, OUTGOING }

enum class SmsMessageType { SMS, MMS_IMAGE, MMS_VIDEO, MMS_AUDIO, MMS_TEXT, CALL_SUMMARY }

/** Pending outbound message — held in memory until delivery confirmed. */
data class PendingMessage(
    val to: String,
    val body: String,
    val attachmentUris: List<String> = emptyList()
)
