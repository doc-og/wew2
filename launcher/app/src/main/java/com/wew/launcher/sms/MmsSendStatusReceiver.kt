package com.wew.launcher.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import java.io.File
import java.io.OutputStream

/**
 * Receives the PendingIntent fired by [android.telephony.SmsManager.sendMultimediaMessage]
 * once the system MMS service has attempted to transmit the PDU.
 *
 * Two responsibilities:
 *
 * 1. Log the transmission result. Without a [android.app.PendingIntent] argument to
 *    [android.telephony.SmsManager.sendMultimediaMessage], MMS failures are
 *    silent — the thread row gets created by the canonical-thread resolver but
 *    the actual transmission can fail (bad APN, payload unreadable, PDU
 *    malformed, size limits, etc.) with no callback. We surface the result code
 *    and clean up the temp PDU file.
 *
 * 2. Persist the sent MMS row to the local MMS provider on [Activity.RESULT_OK].
 *    When WeW is the default SMS app, [android.provider.Telephony]'s MmsService
 *    skips its internal `persistIfRequired` step and expects the default app to
 *    write the outgoing row. Without this, the message transmits fine but never
 *    appears in the chat view because there is no row in
 *    [android.provider.Telephony.Mms.CONTENT_URI].
 */
class MmsSendStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pduPath = intent.getStringExtra(EXTRA_PDU_PATH)
        val recipients = intent.getStringArrayExtra(EXTRA_RECIPIENTS)?.toList().orEmpty()
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
        val imageUriStr = intent.getStringExtra(EXTRA_IMAGE_URI)
        val imageMime = intent.getStringExtra(EXTRA_IMAGE_MIME) ?: "image/jpeg"

        val code = resultCode
        val status = when (code) {
            Activity.RESULT_OK -> "RESULT_OK"
            SmsManager.MMS_ERROR_UNSPECIFIED -> "MMS_ERROR_UNSPECIFIED"
            SmsManager.MMS_ERROR_INVALID_APN -> "MMS_ERROR_INVALID_APN"
            SmsManager.MMS_ERROR_UNABLE_CONNECT_MMS -> "MMS_ERROR_UNABLE_CONNECT_MMS"
            SmsManager.MMS_ERROR_HTTP_FAILURE -> "MMS_ERROR_HTTP_FAILURE"
            SmsManager.MMS_ERROR_IO_ERROR -> "MMS_ERROR_IO_ERROR"
            SmsManager.MMS_ERROR_RETRY -> "MMS_ERROR_RETRY"
            SmsManager.MMS_ERROR_CONFIGURATION_ERROR -> "MMS_ERROR_CONFIGURATION_ERROR"
            SmsManager.MMS_ERROR_NO_DATA_NETWORK -> "MMS_ERROR_NO_DATA_NETWORK"
            else -> "resultCode=$code"
        }
        Log.i(
            "MmsSend",
            "sendMultimediaMessage callback: $status pdu=$pduPath recipients=${recipients.size}"
        )

        if (code == Activity.RESULT_OK && recipients.isNotEmpty()) {
            val pending = goAsync()
            Thread {
                try {
                    persistSentMms(context, recipients, body, imageUriStr, imageMime)
                } catch (t: Throwable) {
                    Log.e("MmsSend", "persistSentMms failed: ${t.message}", t)
                } finally {
                    pduPath?.let { runCatching { File(it).delete() } }
                    pending.finish()
                }
            }.start()
        } else {
            pduPath?.let { runCatching { File(it).delete() } }
        }
    }

    /**
     * Write the outgoing MMS into [android.provider.Telephony.Mms.Sent.CONTENT_URI]
     * so our chat view — and any other SMS reader on the device — can see it.
     *
     * Writes three kinds of rows:
     *  - One MMS row in the `mms` table (msg_box = SENT, m_type = SEND_REQ).
     *  - One address row per recipient in `mms/ID/addr` (type = 151 TO), plus
     *    a stub FROM (137 = `insert-address-token`) so the telephony stack
     *    knows this is an outgoing message from our line.
     *  - One part row per payload piece in `mms/ID/part` (text-plain for the
     *    body, image-x for an optional attachment). Text is written inline
     *    via the `text` column; image bytes are streamed via openOutputStream.
     */
    private fun persistSentMms(
        context: Context,
        recipients: List<String>,
        body: String,
        imageUriStr: String?,
        imageMime: String
    ) {
        val resolver = context.contentResolver

        val threadIdBuilder = Uri.parse("content://mms-sms/threadID").buildUpon()
        for (r in recipients) threadIdBuilder.appendQueryParameter("recipient", r)
        val threadId: Long = runCatching {
            resolver.query(threadIdBuilder.build(), arrayOf("_id"), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else -1L
            } ?: -1L
        }.getOrElse { -1L }

        if (threadId == -1L) {
            Log.w("MmsSend", "persistSentMms: could not resolve thread id for ${recipients.size} recipients")
            return
        }

        val now = System.currentTimeMillis()
        val mmsValues = ContentValues().apply {
            put(Telephony.Mms.THREAD_ID, threadId)
            // Mms.DATE is in *seconds* whereas Sms.DATE is milliseconds — mirror AOSP.
            put(Telephony.Mms.DATE, now / 1000L)
            put(Telephony.Mms.DATE_SENT, now / 1000L)
            put(Telephony.Mms.READ, 1)
            put(Telephony.Mms.SEEN, 1)
            put(Telephony.Mms.MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_SEND_REQ)
            put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_SENT)
            put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
            put(Telephony.Mms.MMS_VERSION, PduHeaders.CURRENT_MMS_VERSION)
            put(Telephony.Mms.MESSAGE_CLASS, "personal")
            put(Telephony.Mms.PRIORITY, PduHeaders.PRIORITY_NORMAL)
            put(Telephony.Mms.RESPONSE_STATUS, PduHeaders.RESPONSE_STATUS_OK)
            put(Telephony.Mms.TRANSACTION_ID, "wew_${now}")
        }

        val mmsUri = resolver.insert(Telephony.Mms.Sent.CONTENT_URI, mmsValues)
        val mmsId = mmsUri?.lastPathSegment?.toLongOrNull()
        if (mmsId == null) {
            Log.w("MmsSend", "persistSentMms: insert returned null uri")
            return
        }

        val addrUri = Uri.parse("content://mms/$mmsId/addr")
        // FROM: use the telephony token rather than the raw MSISDN, matching the
        // outbound PDU's HDR_FROM. Display logic still classifies the row as
        // outgoing via msg_box = SENT.
        resolver.insert(
            addrUri,
            ContentValues().apply {
                put("msg_id", mmsId)
                put("address", "insert-address-token")
                put("type", PduHeaders.FROM)
                put("charset", CHARSET_UTF8)
            }
        )
        for (r in recipients) {
            resolver.insert(
                addrUri,
                ContentValues().apply {
                    put("msg_id", mmsId)
                    put("address", r)
                    put("type", PduHeaders.TO)
                    put("charset", CHARSET_UTF8)
                }
            )
        }

        // Parts table column for the parent MMS id is `mid` (AOSP PduPersister / MmsProvider),
        // *not* `msg_id` like the addresses table. Inserting via `content://mms/<id>/part`
        // also causes MmsProvider to auto-fill mid from the URI, but we set it explicitly
        // for defensiveness against OEM providers.
        val partUri = Uri.parse("content://mms/$mmsId/part")
        var seq = 0
        if (body.isNotBlank()) {
            val textCv = ContentValues().apply {
                put("mid", mmsId)
                put("seq", seq++)
                put("ct", "text/plain")
                put("chset", CHARSET_UTF8)
                put("cid", "<text_0>")
                put("cl", "text_0.txt")
                put("text", body)
            }
            val insertedText = resolver.insert(partUri, textCv)
            Log.i("MmsSend", "text part insert -> $insertedText (body=${body.length} chars)")
        }

        if (!imageUriStr.isNullOrBlank()) {
            val cv = ContentValues().apply {
                put("mid", mmsId)
                put("seq", seq++)
                put("ct", imageMime)
                put("cid", "<image_0>")
                put("cl", "image.${imageMime.substringAfter('/')}")
                put("name", "image.${imageMime.substringAfter('/')}")
            }
            val inserted = resolver.insert(partUri, cv)
            Log.i("MmsSend", "image part insert -> $inserted (mime=$imageMime)")
            if (inserted != null) {
                runCatching {
                    val input = resolver.openInputStream(Uri.parse(imageUriStr))
                    val output: OutputStream? = resolver.openOutputStream(inserted)
                    if (input != null && output != null) {
                        input.use { ins -> output.use { outs -> ins.copyTo(outs) } }
                    }
                }.onFailure { Log.w("MmsSend", "image part write failed: ${it.message}") }
            }
        }

        Log.i(
            "MmsSend",
            "persisted sent MMS id=$mmsId thread=$threadId recipients=${recipients.size} body=${body.length} chars"
        )
    }

    companion object {
        const val ACTION_MMS_SENT = "com.wew.launcher.action.MMS_SENT"
        const val EXTRA_PDU_PATH = "pdu_path"
        const val EXTRA_RECIPIENTS = "recipients"
        const val EXTRA_BODY = "body"
        const val EXTRA_IMAGE_URI = "image_uri"
        const val EXTRA_IMAGE_MIME = "image_mime"
        const val CHARSET_UTF8 = 106

        fun intent(
            context: Context,
            pduPath: String,
            recipients: List<String>,
            body: String,
            imageUri: String?,
            imageMime: String
        ): Intent =
            Intent(ACTION_MMS_SENT)
                .setPackage(context.packageName)
                .setClass(context, MmsSendStatusReceiver::class.java)
                .setData(Uri.parse("wew-mms://$pduPath"))
                .putExtra(EXTRA_PDU_PATH, pduPath)
                .putExtra(EXTRA_RECIPIENTS, recipients.toTypedArray())
                .putExtra(EXTRA_BODY, body)
                .putExtra(EXTRA_IMAGE_URI, imageUri)
                .putExtra(EXTRA_IMAGE_MIME, imageMime)
    }
}

/**
 * Minimal subset of OMA-MMS PDU header constants we need to persist a sent MMS.
 * Mirrored from `com.google.android.mms.pdu.PduHeaders` (hidden framework class)
 * so we don't rely on reflection.
 */
private object PduHeaders {
    const val CURRENT_MMS_VERSION = 0x12   // MMS 1.2
    const val MESSAGE_TYPE_SEND_REQ = 128
    const val PRIORITY_NORMAL = 129
    const val RESPONSE_STATUS_OK = 128
    const val FROM = 137
    const val TO = 151
}
