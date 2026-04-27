package com.wew.launcher.sms

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.wew.launcher.data.model.ActionType
import com.wew.launcher.data.model.ActivityLog
import com.wew.launcher.data.model.MessageLog
import com.wew.launcher.data.repository.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * SmsReceiver — handles incoming SMS when WeW is the default messaging app.
 *
 * Registered for android.provider.Telephony.SMS_DELIVER (not SMS_RECEIVED),
 * which is only delivered to the default SMS app. This prevents duplicate
 * processing by other apps.
 *
 * IMPORTANT: When an app is the default SMS app, Android does NOT auto-persist
 * the incoming message to the SMS Provider — that is the default app's job.
 * If we don't insert the row here, the message never appears in any SMS reader
 * (including our own ConversationList / ChatScreen, which both query
 * content://sms via [SmsRepository] and refresh on a ContentObserver).
 *
 * On receipt:
 * 1. Reads message PDUs from the intent
 * 2. Inserts the message into Telephony.Sms.Inbox (the canonical store)
 * 3. Mirrors metadata to Supabase (MessageLog) for parent visibility
 * 4. Logs activity for parent timeline
 *
 * Contact filtering (approved vs quarantine) is intentionally NOT done here —
 * filtering happens in the ConversationList UI layer once the row is visible.
 */
class SmsReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val senderAddress = messages.first().displayOriginatingAddress ?: return
        val body = messages.joinToString("") { it.messageBody ?: "" }
        // Wall-clock instant when the device handled delivery — matches the conversation
        // list and avoids SMSC/PDU timestamps that skew chat order and bubble labels.
        val deliveredAtMs = System.currentTimeMillis()

        Log.d("SmsReceiver", "Incoming SMS from $senderAddress (${body.length} chars)")

        // Persist to the SMS Provider so the conversation list / chat / any other
        // SMS reader on the device can see it. The ContentObserver in
        // SmsRepository.observeSmsChanges() will fire on this insert and the UI
        // will refresh automatically. thread_id is auto-resolved by the provider
        // from `address`.
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, senderAddress)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, deliveredAtMs)
            put(Telephony.Sms.DATE_SENT, deliveredAtMs)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        }
        val inboxUri = runCatching {
            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        }.onFailure {
            Log.e("SmsReceiver", "Failed to insert inbox row (is WeW default SMS app?): ${it.message}")
        }.getOrNull()

        // Some providers rewrite DATE/DATE_SENT from the PDU after insert; re-apply
        // delivery time so chat reads the same instant as the list.
        if (inboxUri != null) {
            val fix = ContentValues().apply {
                put(Telephony.Sms.DATE, deliveredAtMs)
                put(Telephony.Sms.DATE_SENT, deliveredAtMs)
            }
            runCatching {
                context.contentResolver.update(inboxUri, fix, null, null)
            }.onFailure {
                Log.w("SmsReceiver", "post-insert DATE fix failed: ${it.message}")
            }
        }

        if (inboxUri == null) {
            // Without the inbox row the message is lost to the UI; bail before
            // logging anything that would make it look like we received it.
            return
        }

        // Mirror metadata to Supabase async — fire and forget.
        val prefs = context.getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", null) ?: return

        scope.launch {
            runCatching {
                val repo = DeviceRepository(context)
                repo.logMessageMirror(
                    MessageLog(
                        deviceId = deviceId,
                        threadId = resolveThreadId(context, senderAddress),
                        senderAddress = senderAddress,
                        senderType = "contact",
                        messageType = "text",
                        hasMedia = false,
                        tokensConsumed = 0,   // incoming messages don't cost tokens
                        body = body.ifBlank { null }
                    )
                )
                repo.logActivity(
                    ActivityLog(
                        deviceId = deviceId,
                        actionType = ActionType.SMS_RECEIVED.value,
                        appPackage = null,
                        appName = null,
                        tokensConsumed = 0
                    )
                )
            }.onFailure { Log.e("SmsReceiver", "Supabase log failed: ${it.message}") }
        }
    }

    private fun resolveThreadId(context: Context, address: String): String {
        return SmsRepository(context).getThreadIdForAddress(address).toString()
    }
}
