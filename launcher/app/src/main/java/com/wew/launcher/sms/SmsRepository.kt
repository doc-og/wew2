package com.wew.launcher.sms

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.FileProvider
import com.wew.launcher.telecom.PhoneMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File

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

        // Telephony's SNIPPET is often empty for media-only MMS, some group threads, or
        // right after a send before the provider refreshes. Fill from the last message.
        threads.map { thread ->
            if (thread.snippet.isNotBlank() || thread.messageCount <= 0) {
                thread
            } else {
                val last = getMessages(thread.threadId).maxByOrNull { it.date }
                val fill = last?.let { messagePreviewText(it) }.orEmpty()
                if (fill.isNotEmpty()) thread.copy(snippet = fill) else thread
            }
        }
    }

    // ── Messages (within a thread) ────────────────────────────────────────────

    /**
     * Returns all SMS and MMS messages for [threadId], sorted oldest-first.
     */
    suspend fun getMessages(threadId: Long): List<SmsMessage> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<SmsMessage>()
        messages += getSmsMessages(threadId)
        messages += getMmsMessages(threadId)
        messages.sortWith(compareBy({ it.date }, { it.type.ordinal }, { it.id }))
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

                    val rawMmsDate = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms.DATE))
                    // Provider contract is seconds since epoch; some OEMs store ms already.
                    val dateMs = if (rawMmsDate < 1_000_000_000_000L) {
                        rawMmsDate * 1000L
                    } else {
                        rawMmsDate
                    }
                    list += SmsMessage(
                        id = mmsId,
                        threadId = threadId,
                        address = getMmsSenderAddress(mmsId),
                        body = bodyPart?.let { readMmsPartText(it.contentUri) } ?: "",
                        date = dateMs,
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

    /**
     * MMS text parts can live in one of two places:
     *
     *  1. Inline in the `text` column of `content://mms/part/<id>` — this is how
     *     short bodies are typically stored (and how WeW persists outgoing sends;
     *     see [MmsSendStatusReceiver.persistSentMms]).
     *  2. In a backing file referenced by `_data`, accessed via `openInputStream`
     *     on the part URI — how AOSP's `PduPersister` tends to lay larger payloads
     *     and what `MmsReceiver` produces for inbound text.
     *
     * Prefer (1) because it's one query with no SELinux-sensitive open. Fall back
     * to (2) when the column is null/empty (e.g. for inbound bodies or when the
     * row was persisted by another app).
     */
    private fun readMmsPartText(contentUri: String): String {
        val partId = Uri.parse(contentUri).lastPathSegment?.toLongOrNull()
        if (partId != null) {
            val inline = runCatching {
                context.contentResolver.query(
                    Uri.parse("content://mms/part"),
                    arrayOf("text"),
                    "_id = ?",
                    arrayOf(partId.toString()),
                    null
                )?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull()
            if (!inline.isNullOrEmpty()) return inline
        }
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

    /**
     * All addresses on an MMS row, spanning FROM / TO / CC / BCC. Used by
     * [getParticipantAddressesForThread] so outgoing group MMS (where FROM is
     * the device itself) still contributes the recipient list.
     *
     * PDU address types (see OMA-MMS-ENC):
     *   130 = BCC, 137 = FROM, 151 = TO, 152 = CC
     */
    private fun getMmsAllAddresses(mmsId: Long): Set<String> {
        val out = linkedSetOf<String>()
        runCatching {
            context.contentResolver.query(
                Uri.parse("content://mms/$mmsId/addr"),
                arrayOf("address", "type"),
                "type IN (130, 137, 151, 152)",
                null, null
            )?.use { cursor ->
                val addrIdx = cursor.getColumnIndex("address")
                if (addrIdx >= 0) {
                    while (cursor.moveToNext()) {
                        cursor.getString(addrIdx)
                            ?.trim()
                            ?.takeIf { it.isNotBlank() && !it.equals("insert-address-token", ignoreCase = true) }
                            ?.let { out.add(it) }
                    }
                }
            }
        }.onFailure { Log.w("SmsRepo", "getMmsAllAddresses failed: ${it.message}") }
        return out
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    /**
     * Send a plain SMS. No token deduction here — caller (ViewModel) handles that.
     *
     * [SmsManager.sendTextMessage] transmits the message but does NOT write a row
     * to the SMS content provider — that's the default SMS app's responsibility.
     * After a successful dispatch we insert the outgoing row into
     * `content://sms/sent` so our chat view (and any other SMS reader on the
     * device) sees the sent message.
     */
    @Suppress("DEPRECATION")
    fun sendSms(to: String, body: String): Result<Unit> =
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
            insertSentSmsRow(to = to, body = body)
            Unit
        }.onFailure { Log.e("SmsRepo", "sendSms failed: ${it.message}") }

    /**
     * Writes an outgoing SMS row to the Sent folder. Only the default SMS app
     * can write here; silently logs and returns if we're not default.
     */
    private fun insertSentSmsRow(to: String, body: String) {
        runCatching {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, to)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.DATE_SENT, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            }
            context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
        }.onFailure {
            Log.w(
                "SmsRepo",
                "insertSentSmsRow failed (is WeW the default SMS app?): ${it.message}"
            )
        }
    }

    /**
     * Send an MMS with an optional single image attachment to one or more recipients.
     *
     * When [to] has more than one entry the PDU carries repeated HDR_TO fields,
     * which is how group MMS is signalled — the telephony stack will produce a
     * single MMS thread containing all recipients.
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
        to: List<String>,
        body: String,
        imageUri: String? = null,
        imageMimeType: String = "image/jpeg"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(to.isNotEmpty()) { "sendMms: at least one recipient required" }
            val imageBytes = imageUri?.let { readBytesFromUri(it) }
            val pdu = MmsPduBuilder.build(
                to = to,
                text = body.ifBlank { null },
                imageBytes = imageBytes,
                imageMimeType = imageMimeType
            )
            Log.d(
                "SmsRepo",
                "sendMms: PDU ${pdu.size} bytes, recipients=${to.size}, imageBytes=${imageBytes?.size ?: 0}"
            )

            // Write the PDU under cacheDir/mms_out/ so it matches the FileProvider
            // paths declared in res/xml/mms_file_paths.xml. The phone/MMS service
            // runs in a different UID and can only read this file via the
            // FileProvider content URI below.
            val outDir = File(context.cacheDir, "mms_out").apply { mkdirs() }
            val tempFile = File(outDir, "mms_${System.currentTimeMillis()}.pdu")
            tempFile.writeBytes(pdu)
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.mmsprovider",
                tempFile
            )

            // A PendingIntent that SmsManager fires once transmission completes.
            // Carries the PDU file path so the receiver can clean it up and log
            // the result code — without this, MMS failures are invisible.
            val flags = PendingIntent.FLAG_ONE_SHOT or
                PendingIntent.FLAG_UPDATE_CURRENT or
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
            val sentIntent = PendingIntent.getBroadcast(
                context,
                (tempFile.name.hashCode() and 0x7fffffff),
                MmsSendStatusReceiver.intent(
                    context = context,
                    pduPath = tempFile.absolutePath,
                    recipients = to,
                    body = body,
                    imageUri = imageUri,
                    imageMime = imageMimeType
                ),
                flags
            )

            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
            smsManager.sendMultimediaMessage(context, contentUri, null, null, sentIntent)
            Log.i("SmsRepo", "sendMms handed off to SmsManager: uri=$contentUri")
            Unit
        }.onFailure { Log.e("SmsRepo", "sendMms failed: ${it.message}", it) }
    }

    /** Single-recipient convenience overload kept for existing call sites. */
    suspend fun sendMms(
        to: String,
        body: String,
        imageUri: String? = null,
        imageMimeType: String = "image/jpeg"
    ): Result<Unit> = sendMms(listOf(to), body, imageUri, imageMimeType)

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

    /**
     * Marks the thread as unread by flipping READ=0 on the most recent incoming SMS
     * row. We intentionally flip only the latest received message so the unread
     * count rises to 1 (iOS-style) instead of re-surfacing every historical message.
     * No-op if the thread has no incoming SMS rows (e.g. MMS-only) — matches the
     * existing unread-count semantics which look only at SMS.
     */
    fun markThreadUnread(threadId: Long) {
        runCatching {
            val latestId = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.TYPE} = ?",
                arrayOf(threadId.toString(), Telephony.Sms.MESSAGE_TYPE_INBOX.toString()),
                "${Telephony.Sms.DATE} DESC LIMIT 1"
            )?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else null
            }
            if (latestId == null) {
                Log.i("SmsRepo", "markThreadUnread: no incoming sms row for thread $threadId")
                return@runCatching
            }
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                ContentValues().apply { put(Telephony.Sms.READ, 0) },
                "${Telephony.Sms._ID} = ?",
                arrayOf(latestId.toString())
            )
        }.onFailure { Log.e("SmsRepo", "markThreadUnread failed: ${it.message}") }
    }

    /**
     * Deletes all SMS and MMS for [threadId], then the conversation aggregate row.
     * Deletes by row id where needed — some OEMs ignore bulk THREAD_ID deletes.
     */
    suspend fun deleteThread(threadId: Long) = withContext(Dispatchers.IO) {
        val tid = threadId.toString()
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(tid),
                null
            )?.use { c ->
                val idx = c.getColumnIndexOrThrow(Telephony.Sms._ID)
                while (c.moveToNext()) {
                    val id = c.getLong(idx)
                    context.contentResolver.delete(
                        Uri.parse("${Telephony.Sms.CONTENT_URI}/$id"),
                        null,
                        null
                    )
                }
            }
        }.onFailure { Log.e("SmsRepo", "deleteThread sms by id: ${it.message}") }
        runCatching {
            context.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(tid)
            )
        }.onFailure { Log.e("SmsRepo", "deleteThread sms bulk: ${it.message}") }

        runCatching {
            context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms._ID),
                "${Telephony.Mms.THREAD_ID} = ?",
                arrayOf(tid),
                null
            )?.use { c ->
                val idx = c.getColumnIndexOrThrow(Telephony.Mms._ID)
                while (c.moveToNext()) {
                    val id = c.getLong(idx)
                    context.contentResolver.delete(
                        Uri.parse("${Telephony.Mms.CONTENT_URI}/$id"),
                        null,
                        null
                    )
                }
            }
        }.onFailure { Log.e("SmsRepo", "deleteThread mms by id: ${it.message}") }
        runCatching {
            context.contentResolver.delete(
                Telephony.Mms.CONTENT_URI,
                "${Telephony.Mms.THREAD_ID} = ?",
                arrayOf(tid)
            )
        }.onFailure { Log.e("SmsRepo", "deleteThread mms bulk: ${it.message}") }

        runCatching {
            context.contentResolver.delete(
                Uri.parse("content://mms-sms/conversations/$threadId"),
                null,
                null
            )
        }.onFailure { Log.e("SmsRepo", "deleteThread conv: ${it.message}") }
    }

    /**
     * Participant addresses for a thread from actual SMS/MMS rows (source of truth).
     * Conversation list metadata is wrong on some devices.
     */
    suspend fun getParticipantAddressesForThread(threadId: Long): Set<String> = withContext(Dispatchers.IO) {
        val out = linkedSetOf<String>()
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS),
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                null
            )?.use { c ->
                val idx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                if (idx >= 0) {
                    while (c.moveToNext()) {
                        c.getString(idx)?.takeIf { it.isNotBlank() }?.let { out.add(it) }
                    }
                }
            }
        }.onFailure { Log.e("SmsRepo", "getParticipantAddresses sms: ${it.message}") }
        runCatching {
            context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms._ID),
                "${Telephony.Mms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                null
            )?.use { c ->
                val idx = c.getColumnIndexOrThrow(Telephony.Mms._ID)
                while (c.moveToNext()) {
                    // FROM + TO + CC + BCC. Outgoing MMS only has FROM = own number
                    // plus TO/CC rows for the recipients; querying just FROM would
                    // drop the recipients of a group the child initiated.
                    out.addAll(getMmsAllAddresses(c.getLong(idx)))
                }
            }
        }.onFailure { Log.e("SmsRepo", "getParticipantAddresses mms: ${it.message}") }
        out
    }

    /**
     * Resolves the canonical thread_id for a recipient set via
     * `content://mms-sms/threadID?recipient=…&recipient=…`. This is how Android
     * identifies a group thread independent of which message went through first.
     * Returns -1 when the provider can't resolve (older OS / empty list).
     */
    suspend fun resolveThreadIdForRecipients(phones: List<String>): Long = withContext(Dispatchers.IO) {
        val clean = phones.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return@withContext -1L
        val builder = Uri.parse("content://mms-sms/threadID").buildUpon()
        for (p in clean) builder.appendQueryParameter("recipient", p)
        runCatching {
            context.contentResolver.query(builder.build(), arrayOf("_id"), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else -1L
            } ?: -1L
        }.getOrElse {
            Log.w("SmsRepo", "resolveThreadIdForRecipients failed: ${it.message}")
            -1L
        }
    }

    /**
     * Resolves an existing SMS thread for [phone], trying common address formats and
     * falling back to scanning [getThreads] when the provider stores numbers differently.
     */
    suspend fun resolveThreadIdForContact(phone: String): Long = withContext(Dispatchers.IO) {
        val trimmed = phone.trim()
        if (trimmed.isEmpty()) return@withContext -1L
        val candidates = buildSet {
            add(trimmed)
            add(
                trimmed.replace(" ", "")
                    .replace("-", "")
                    .replace("(", "")
                    .replace(")", "")
            )
            val d = PhoneMatch.digitsOnly(trimmed)
            when {
                d.length == 10 -> {
                    add(d)
                    add("1$d")
                    add("+1$d")
                }
                d.length == 11 && d.startsWith("1") -> {
                    add(d)
                    add("+$d")
                    add(d.drop(1))
                }
                d.isNotEmpty() -> add(d)
            }
        }
        for (c in candidates) {
            if (c.isBlank()) continue
            val tid = getThreadIdForAddress(c)
            if (tid != -1L) return@withContext tid
        }
        val threads = getThreads()
        for (t in threads) {
            val addrs = t.address.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (addrs.any { PhoneMatch.sameSubscriber(it, trimmed) }) {
                return@withContext t.threadId
            }
        }
        -1L
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

    /**
     * One-line list preview when [Telephony.Threads.SNIPPET] is blank but a body or
     * attachment exists (e.g. image MMS with no caption).
     */
    private fun messagePreviewText(m: SmsMessage): String {
        val t = m.body.trim()
        if (t.isNotEmpty()) return t
        return when (m.type) {
            SmsMessageType.SMS, SmsMessageType.MMS_TEXT -> "Message"
            SmsMessageType.MMS_IMAGE -> "Photo"
            SmsMessageType.MMS_VIDEO -> "Video"
            SmsMessageType.MMS_AUDIO -> "Audio"
            SmsMessageType.CALL_SUMMARY -> "Call"
        }
    }

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
