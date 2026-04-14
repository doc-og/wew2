package com.wew.launcher.sms

import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * SmsRepository — reads and writes SMS/MMS via Android's Telephony ContentProvider.
 *
 * When WeW is the default SMS app it has full read/write access to content://sms
 * and content://mms. When it is NOT the default app it can still read (but not delete
 * or reliably write).
 *
 * Contact name resolution is done externally by the ViewModel using WewContact records
 * from Supabase. The repository works only with raw phone numbers.
 */
class SmsRepository(private val context: Context) {

    // ── Threads (conversation list) ───────────────────────────────────────────

    /**
     * Returns all SMS/MMS threads sorted by most-recent first.
     * Caller is responsible for filtering to approved contacts.
     */
    suspend fun getThreads(): List<SmsThread> = withContext(Dispatchers.IO) {
        val threads = mutableListOf<SmsThread>()
        val uri = Uri.parse("content://mms-sms/conversations?simple=true")
        val projection = arrayOf(
            Telephony.Threads._ID,
            Telephony.Threads.DATE,
            Telephony.Threads.MESSAGE_COUNT,
            Telephony.Threads.READ,
            Telephony.Threads.SNIPPET,
            Telephony.Threads.RECIPIENT_IDS
        )
        runCatching {
            context.contentResolver.query(
                uri, projection, null, null, "${Telephony.Threads.DATE} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val threadId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Threads._ID))
                    val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Threads.DATE))
                    val snippet = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Threads.SNIPPET)) ?: ""
                    val msgCount = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Threads.MESSAGE_COUNT))
                    val recipientIds = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Threads.RECIPIENT_IDS)) ?: ""

                    // Resolve recipient_ids → phone addresses
                    val address = resolveRecipientIds(recipientIds)
                    val unreadCount = getUnreadCountForThread(threadId)
                    val lastType = getLastMessageType(threadId)

                    threads += SmsThread(
                        threadId = threadId,
                        address = address,
                        displayName = address,   // caller resolves to contact name
                        snippet = snippet,
                        date = date,
                        unreadCount = unreadCount,
                        messageCount = msgCount,
                        lastMessageType = lastType
                    )
                }
            }
        }.onFailure { Log.e("SmsRepo", "getThreads failed: ${it.message}") }
        threads
    }

    // ── Messages (within a thread) ────────────────────────────────────────────

    /**
     * Returns all SMS and MMS messages for [threadId], sorted oldest-first.
     */
    suspend fun getMessages(threadId: Long): List<SmsMessage> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<SmsMessage>()
        messages += getSmsMessages(threadId)
        messages += getMmsMessages(threadId)
        messages.sortBy { it.date }
        messages
    }

    private fun getSmsMessages(threadId: Long): List<SmsMessage> {
        val list = mutableListOf<SmsMessage>()
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.THREAD_ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE,
                    Telephony.Sms.READ
                ),
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                    list += SmsMessage(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID)),
                        threadId = threadId,
                        address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: "",
                        body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: "",
                        date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)),
                        direction = if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) SmsDirection.INCOMING else SmsDirection.OUTGOING,
                        isRead = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1,
                        type = SmsMessageType.SMS
                    )
                }
            }
        }.onFailure { Log.e("SmsRepo", "getSmsMessages failed: ${it.message}") }
        return list
    }

    private fun getMmsMessages(threadId: Long): List<SmsMessage> {
        val list = mutableListOf<SmsMessage>()
        runCatching {
            context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(
                    Telephony.Mms._ID,
                    Telephony.Mms.THREAD_ID,
                    Telephony.Mms.DATE,
                    Telephony.Mms.MESSAGE_BOX,
                    Telephony.Mms.READ
                ),
                "${Telephony.Mms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Mms.DATE} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val mmsId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms._ID))
                    val box = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX))
                    val attachments = getMmsAttachments(mmsId)
                    val bodyPart = attachments.firstOrNull { it.contentType == "text/plain" }
                    val mediaType = attachments.firstOrNull { it.contentType.startsWith("image/") }
                        ?.let { SmsMessageType.MMS_IMAGE }
                        ?: attachments.firstOrNull { it.contentType.startsWith("video/") }
                            ?.let { SmsMessageType.MMS_VIDEO }
                        ?: attachments.firstOrNull { it.contentType.startsWith("audio/") }
                            ?.let { SmsMessageType.MMS_AUDIO }
                        ?: SmsMessageType.MMS_TEXT

                    list += SmsMessage(
                        id = mmsId,
                        threadId = threadId,
                        address = getMmsSenderAddress(mmsId),
                        body = bodyPart?.let { readMmsPartText(it.contentUri) } ?: "",
                        date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms.DATE)) * 1000L,
                        direction = if (box == Telephony.Mms.MESSAGE_BOX_INBOX) SmsDirection.INCOMING else SmsDirection.OUTGOING,
                        isRead = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Mms.READ)) == 1,
                        type = mediaType,
                        attachments = attachments.filter { it.contentType != "text/plain" && !it.contentType.contains("smil") }
                    )
                }
            }
        }.onFailure { Log.e("SmsRepo", "getMmsMessages failed: ${it.message}") }
        return list
    }

    private fun getMmsAttachments(mmsId: Long): List<MmsAttachment> {
        val parts = mutableListOf<MmsAttachment>()
        runCatching {
            context.contentResolver.query(
                Uri.parse("content://mms/$mmsId/part"),
                arrayOf("_id", "ct", "name"),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val partId = cursor.getLong(0)
                    val contentType = cursor.getString(1) ?: "application/octet-stream"
                    val name = cursor.getString(2)
                    parts += MmsAttachment(
                        partId = partId,
                        contentType = contentType,
                        name = name,
                        contentUri = "content://mms/part/$partId"
                    )
                }
            }
        }.onFailure { Log.e("SmsRepo", "getMmsAttachments failed: ${it.message}") }
        return parts
    }

    private fun readMmsPartText(contentUri: String): String {
        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(contentUri))
                ?.bufferedReader()
                ?.readText()
                ?: ""
        }.getOrElse { "" }
    }

    private fun getMmsSenderAddress(mmsId: Long): String {
        return runCatching {
            context.contentResolver.query(
                Uri.parse("content://mms/$mmsId/addr"),
                arrayOf("address", "type"),
                "type = 137",   // 137 = FROM
                null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else ""
            } ?: ""
        }.getOrElse { "" }
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    /**
     * Send a plain SMS. No token deduction here — caller (ViewModel) handles that.
     */
    @Suppress("DEPRECATION")
    fun sendSms(to: String, body: String) {
        runCatching {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(body)
            if (parts.size == 1) {
                smsManager.sendTextMessage(to, null, body, null, null)
            } else {
                smsManager.sendMultipartTextMessage(to, null, parts, null, null)
            }
        }.onFailure { Log.e("SmsRepo", "sendSms failed: ${it.message}") }
    }

    /**
     * Send an MMS with an optional single image attachment.
     *
     * Builds a WAP MMS m-send-req PDU via [MmsPduBuilder], writes it to a
     * cache temp file, and hands off to SmsManager which handles APN selection
     * and MMSC submission.
     *
     * @param imageUri content:// or file:// URI of the image to attach, or null for text-only MMS.
     * @param imageMimeType MIME type of the image (e.g. "image/jpeg").
     */
    @Suppress("DEPRECATION")
    suspend fun sendMms(
        to: String,
        body: String,
        imageUri: String? = null,
        imageMimeType: String = "image/jpeg"
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val imageBytes = imageUri?.let { readBytesFromUri(it) }
            val pdu = MmsPduBuilder.build(
                to = to,
                text = body.ifBlank { null },
                imageBytes = imageBytes,
                imageMimeType = imageMimeType
            )
            Log.d("SmsRepo", "sendMms: PDU ${pdu.size} bytes, imageBytes=${imageBytes?.size ?: 0}")

            val tempFile = java.io.File(context.cacheDir, "mms_out_${System.currentTimeMillis()}.pdu")
            tempFile.writeBytes(pdu)
            val tempUri = Uri.fromFile(tempFile)

            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
            smsManager.sendMultimediaMessage(context, tempUri, null, null, null)
        }.onFailure { Log.e("SmsRepo", "sendMms failed: ${it.message}") }
    }

    /**
     * Read all bytes from a content:// or file:// URI.
     * Returns null if the URI is unreadable.
     */
    fun readBytesFromUri(uriStr: String): ByteArray? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { it.readBytes() }
    }.onFailure { Log.e("SmsRepo", "readBytesFromUri failed: ${it.message}") }.getOrElse { null }

    // ── Thread management ─────────────────────────────────────────────────────

    fun markThreadRead(threadId: Long) {
        runCatching {
            val values = ContentValues().apply { put(Telephony.Sms.READ, 1) }
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString())
            )
            context.contentResolver.update(
                Telephony.Mms.CONTENT_URI,
                ContentValues().apply { put(Telephony.Mms.READ, 1) },
                "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.READ} = 0",
                arrayOf(threadId.toString())
            )
        }.onFailure { Log.e("SmsRepo", "markThreadRead failed: ${it.message}") }
    }

    fun deleteThread(threadId: Long) {
        runCatching {
            context.contentResolver.delete(
                Uri.parse("content://mms-sms/conversations/$threadId"),
                null, null
            )
        }.onFailure { Log.e("SmsRepo", "deleteThread failed: ${it.message}") }
    }

    /**
     * Find or return the thread ID for a given [address].
     * Returns -1 if not found (new conversation — thread_id created on first send).
     */
    fun getThreadIdForAddress(address: String): Long {
        return runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.THREAD_ID),
                "${Telephony.Sms.ADDRESS} = ?",
                arrayOf(address),
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else -1L
            } ?: -1L
        }.getOrElse { -1L }
    }

    // ── Live updates via ContentObserver ──────────────────────────────────────

    /**
     * Emits Unit whenever the SMS/MMS database changes.
     * Collect this in a ViewModel and re-fetch threads/messages on each emission.
     */
    fun observeSmsChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(
            Uri.parse("content://mms-sms/"), true, observer
        )
        awaitClose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun getUnreadCountForThread(threadId: Long): Int {
        return runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf("COUNT(*)"),
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString()),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            } ?: 0
        }.getOrElse { 0 }
    }

    private fun getLastMessageType(threadId: Long): SmsMessageType {
        return runCatching {
            context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms._ID),
                "${Telephony.Mms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Mms.DATE} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) SmsMessageType.MMS_IMAGE else SmsMessageType.SMS
            } ?: SmsMessageType.SMS
        }.getOrElse { SmsMessageType.SMS }
    }

    private fun resolveRecipientIds(recipientIds: String): String {
        if (recipientIds.isBlank()) return ""
        return recipientIds.trim().split(" ").mapNotNull { id ->
            runCatching {
                context.contentResolver.query(
                    Uri.parse("content://mms-sms/canonical-addresses"),
                    null,
                    "_id = ?",
                    arrayOf(id.trim()),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow("address")) else null
                }
            }.getOrNull()
        }.joinToString(", ")
    }
}
