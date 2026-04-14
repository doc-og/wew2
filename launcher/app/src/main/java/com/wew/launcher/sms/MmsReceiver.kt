package com.wew.launcher.sms

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.wew.launcher.data.model.ActionType
import com.wew.launcher.data.model.MessageLog
import com.wew.launcher.data.repository.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * MmsReceiver — handles incoming MMS WAP push notifications when WeW is the
 * default messaging app.
 *
 * Uses Android's built-in SmsManager.downloadMultimediaMessage() (API 21+)
 * to pull the MMS content from the MMSC, then stores it in the device's
 * MMS ContentProvider via a temp file.
 */
class MmsReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WAP_PUSH_DELIVER) return

        val data = intent.byteArrayExtra ?: return
        Log.d("MmsReceiver", "Incoming MMS WAP push (${data.size} bytes)")

        scope.launch {
            runCatching {
                downloadMms(context, data)

                // Notify any open UI to refresh
                context.sendBroadcast(Intent(ACTION_MMS_RECEIVED).apply {
                    setPackage(context.packageName)
                })

                // Mirror metadata to Supabase
                val prefs = context.getSharedPreferences("wew_prefs", Context.MODE_PRIVATE)
                val deviceId = prefs.getString("device_id", null) ?: return@runCatching
                DeviceRepository(context).logMessageMirror(
                    MessageLog(
                        deviceId = deviceId,
                        threadId = "",        // resolved after download
                        senderAddress = "unknown",
                        senderType = "contact",
                        messageType = "mms_image",
                        hasMedia = true,
                        tokensConsumed = 0
                    )
                )
            }.onFailure { Log.e("MmsReceiver", "MMS handling failed: ${it.message}") }
        }
    }

    @Suppress("DEPRECATION")
    private fun downloadMms(context: Context, pushData: ByteArray) {
        // Extract content-location from WAP push PDU
        val contentLocation = extractContentLocation(pushData) ?: run {
            Log.w("MmsReceiver", "Could not extract content-location from WAP push")
            return
        }
        Log.d("MmsReceiver", "Downloading MMS from $contentLocation")

        // Write MMS to a temp file; SmsManager writes the PDU there
        val tempFile = File(context.cacheDir, "mms_${System.currentTimeMillis()}.pdu")
        val tempUri = Uri.fromFile(tempFile)

        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }

        smsManager.downloadMultimediaMessage(
            context,
            contentLocation,
            tempUri,
            null,   // configOverrides — use device APN settings
            null    // downloadedIntent — we handle via ContentObserver instead
        )
    }

    /**
     * Minimal WAP push PDU parser to extract the X-Mms-Content-Location header.
     * WAP Binary XML is complex; this covers the common case used by carriers.
     */
    private fun extractContentLocation(data: ByteArray): String? {
        return runCatching {
            val str = String(data, Charsets.ISO_8859_1)
            // Content-location usually appears as a null-terminated ASCII string in the PDU
            val idx = str.indexOf("http")
            if (idx >= 0) {
                val end = str.indexOf('\u0000', idx).takeIf { it > 0 } ?: str.length
                str.substring(idx, end).trim()
            } else null
        }.getOrElse {
            Log.e("MmsReceiver", "Failed to parse WAP push PDU: ${it.message}")
            null
        }
    }

    companion object {
        const val WAP_PUSH_DELIVER = "android.provider.Telephony.WAP_PUSH_DELIVER"
        const val ACTION_MMS_RECEIVED = "com.wew.launcher.MMS_RECEIVED"
    }
}

private val Intent.byteArrayExtra: ByteArray?
    get() = getByteArrayExtra("data")
