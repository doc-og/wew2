package com.wew.launcher.sms

import java.io.ByteArrayOutputStream

/**
 * Minimal MMS m-send-req PDU encoder.
 *
 * Produces bytes suitable for SmsManager.sendMultimediaMessage().
 * Implements OMA-MMS-ENC-V1.3 headers over WAP-230-WSP binary encoding.
 *
 * Supports: text-only, image-only, or text+image payloads.
 * Carrier-edge cases (group MMS, video, large images) may need a
 * full library; this covers the common single-recipient photo+text case.
 */
object MmsPduBuilder {

    // ── MMS header field codes (X-Mms-* table) ───────────────────────────────
    private const val HDR_MESSAGE_TYPE    = 0x8C
    private const val HDR_TRANSACTION_ID  = 0x98
    private const val HDR_MMS_VERSION     = 0x8D
    private const val HDR_DATE            = 0x85
    private const val HDR_FROM            = 0x89
    private const val HDR_TO              = 0x97
    private const val HDR_MESSAGE_CLASS   = 0x8A
    private const val HDR_DELIVERY_REPORT = 0x86
    private const val HDR_CONTENT_TYPE    = 0x84

    // ── WSP well-known content-type short integers ────────────────────────────
    // From WAP-230-WSP Table 40. Short-int = code | 0x80.
    private const val CT_MULTIPART_MIXED = 0xA7  // application/vnd.wap.multipart.mixed (0x27)
    private const val CT_TEXT_PLAIN      = 0x83  // text/plain (0x03)

    // ── WSP part-header field codes ───────────────────────────────────────────
    // From WAP-230-WSP Table 39. Short-int = index | 0x80.
    private const val PART_CONTENT_TYPE = 0x91  // Content-Type (index 0x11)

    // ── WSP parameter token codes ─────────────────────────────────────────────
    private const val PARAM_CHARSET = 0x81  // Charset (index 0x01)
    private const val CHARSET_UTF8  = 0xEA  // UTF-8 well-known charset (106 | 0x80)

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Build a complete MMS m-send-req PDU.
     *
     * @param to            Recipient phone number.
     * @param text          Optional text body; null/blank → no text part.
     * @param imageBytes    Optional image bytes; null → no image part.
     * @param imageMimeType MIME type of [imageBytes] (e.g. "image/jpeg").
     */
    fun build(
        to: String,
        text: String?,
        imageBytes: ByteArray?,
        imageMimeType: String = "image/jpeg"
    ): ByteArray {
        val out = ByteArrayOutputStream()

        // ── MMS headers ───────────────────────────────────────────────────────

        out.byte(HDR_MESSAGE_TYPE); out.byte(0x80)  // m-send-req
        out.byte(HDR_TRANSACTION_ID)
        out.nullString("wew_${System.currentTimeMillis()}")

        out.byte(HDR_MMS_VERSION); out.byte(0x92)   // MMS 1.2

        out.byte(HDR_DATE)
        out.longInt(System.currentTimeMillis() / 1000L)

        // From: insert-address-token (length=1, 0x81 token)
        out.byte(HDR_FROM); out.byte(0x01); out.byte(0x81)

        out.byte(HDR_TO)
        out.nullString(plmn(to))

        out.byte(HDR_MESSAGE_CLASS); out.byte(0x80)  // Personal
        out.byte(HDR_DELIVERY_REPORT); out.byte(0x81)  // No

        // ── Parts ─────────────────────────────────────────────────────────────

        val parts = mutableListOf<ByteArray>()
        if (!text.isNullOrBlank()) parts += textPart(text)
        if (imageBytes != null && imageBytes.isNotEmpty()) parts += imagePart(imageBytes, imageMimeType)

        // Content-Type: application/vnd.wap.multipart.mixed (short-integer)
        out.byte(HDR_CONTENT_TYPE); out.byte(CT_MULTIPART_MIXED)

        // Body: nEntries then each part
        out.uintVar(parts.size)
        parts.forEach { out.write(it) }

        return out.toByteArray()
    }

    // ── Part builders ─────────────────────────────────────────────────────────

    /**
     * text/plain; charset=utf-8 part.
     *
     * Header encoding:
     *   0x91 (Content-Type field)
     *   0x03 (value-length = 3 bytes follow)
     *   0x83 (text/plain short-integer)
     *   0x81 (Charset param token)
     *   0xEA (UTF-8 well-known short-integer)
     */
    private fun textPart(text: String): ByteArray {
        val data = text.toByteArray(Charsets.UTF_8)
        val headers = byteArrayOf(
            PART_CONTENT_TYPE.b,
            0x03,                   // value-length
            CT_TEXT_PLAIN.b,        // text/plain
            PARAM_CHARSET.b,        // charset param
            CHARSET_UTF8.b          // UTF-8
        )
        return part(headers, data)
    }

    /**
     * Image part with MIME type as a WSP text-string (null-terminated ASCII).
     * Using text-string form is more portable than well-known codes for image types.
     */
    private fun imagePart(imageBytes: ByteArray, mimeType: String): ByteArray {
        // 0x91 + text-string (starts with ASCII char < 0x80, so parsed as text-string)
        val headers = byteArrayOf(PART_CONTENT_TYPE.b) +
            mimeType.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0x00)
        return part(headers, imageBytes)
    }

    /** Serialize one multipart part: headerLen, dataLen, headers, data. */
    private fun part(headers: ByteArray, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.uintVar(headers.size)
        out.uintVar(data.size)
        out.write(headers)
        out.write(data)
        return out.toByteArray()
    }

    // ── Address formatting ────────────────────────────────────────────────────

    /** Format a phone number as a PLMN address expected by MMS headers. */
    private fun plmn(number: String): String {
        val digits = number.replace(Regex("[^+\\d]"), "")
        return "$digits/TYPE=PLMN"
    }

    // ── WSP encoding helpers ──────────────────────────────────────────────────

    private fun ByteArrayOutputStream.byte(v: Int) = write(v)

    /** Null-terminated ASCII string (ISO-8859-1 bytes + 0x00). */
    private fun ByteArrayOutputStream.nullString(s: String) {
        write(s.toByteArray(Charsets.ISO_8859_1))
        write(0x00)
    }

    /**
     * WSP Long-integer: 1-byte count followed by big-endian bytes of the value.
     * Short-length (count) must be ≤ 30.
     */
    private fun ByteArrayOutputStream.longInt(value: Long) {
        var v = value
        val bytes = mutableListOf<Byte>()
        while (v > 0) {
            bytes.add(0, (v and 0xFF).toInt().b)
            v = v ushr 8
        }
        if (bytes.isEmpty()) bytes.add(0x00)
        write(bytes.size)
        bytes.forEach { write(it.toInt() and 0xFF) }
    }

    /**
     * WSP Uintvar (variable-length unsigned integer).
     * Each byte carries 7 bits of value; high bit set = more bytes follow.
     */
    private fun ByteArrayOutputStream.uintVar(value: Int) {
        var v = value
        val bytes = mutableListOf<Byte>()
        bytes.add((v and 0x7F).b)
        v = v ushr 7
        while (v > 0) {
            bytes.add(0, ((v and 0x7F) or 0x80).b)
            v = v ushr 7
        }
        bytes.forEach { write(it.toInt() and 0xFF) }
    }

    private val Int.b: Byte get() = toByte()
}
