package com.wew.launcher.sms

import android.content.BroadcastReceiver
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
 * On receipt:
 * 1. Reads message PDUs from the intent
 * 2. Logs metadata to Supabase (MessageLog mirror) for parent visibility
 * 3. Logs activity for token accounting
 * 4. Broadcasts a local intent so any open ChatScreen can refresh
 *
 * Contact filtering (approved vs quarantine) is intentionally NOT done here —
 * the SMS is already written to the device DB by the time this receiver fires.
 * Filtering is applied in the ConversationList UI layer.
 */
class SmsReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val senderAddress = messages.first().displayOriginatingAddress ?: return
        val body = messages.joinToString("") { it.messageBody ?: "" }

        Log.d("SmsReceiver", "Incoming SMS from $senderAddress (${body.length} chars)")

        // Broadcast locally so any open UI can refresh its message list
        context.sendBroadcast(Intent(ACTION_SMS_RECEIVED).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_SENDER, senderAddress)
        })

        // Mirror metadata to Supabase async — fire and forget
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
                        tokensConsumed = 0   // incoming messages don't cost tokens
                    )
                )
                repo.logActivity(
                    ActivityLog(
                        deviceId = deviceId,
                        actionType = ActionType.SMS_SENT.value,   // reuse for received — parent sees both
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

    companion object {
        const val ACTION_SMS_RECEIVED = "com.wew.launcher.SMS_RECEIVED"
        const val EXTRA_SENDER = "sender_address"
    }
}
