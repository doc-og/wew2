package com.wew.launcher.sms

import java.io.ByteArrayOutputStream

/**
 * Minimal OMA-MMS m-send-req PDU encoder.
 *
 * Produces bytes suitable for [android.telephony.SmsManager.sendMultimediaMessage].
 * Implements OMA-MMS-ENC-V1.3 headers over WAP-230-WSP binary encoding.
 *
 * What this builds (and why):
 *
 *  - **Content type:** `application/vnd.wap.multipart.related` with
 *    `type=application/smil` and `start=<smil>` parameters. Using
 *    `multipart.mixed` without a SMIL part causes many receiving clients —
 *    notably iOS and Verizon terminators — to present the message as a raw
 *    file attachment (`attachment.xml`, `text_0.txt`, etc.) instead of
 *    rendering the text/image inline. The SMIL presentation layer tells
 *    the receiver how to render the parts.
 *
 *  - **Entity header layout (WAP-230 §8.5.2):** Inside a WSP multipart,
 *    each entity's headers begin DIRECTLY with the Content-Type *value*
 *    (a short-integer, a text-string, or a Value-length+Media-type+params
 *    block). There is **no** `0x91` (Content-Type field-code) byte before
 *    it — that field code is only for the main MMS envelope. Subsequent
 *    headers (Content-Location, Content-ID, …) DO use WSP header field
 *    codes. Prefixing the entity Content-Type with `0x91` causes MMSCs
 *    and receiving clients to misparse the part and classify it as an
 *    unknown attachment.
 *
 *  - **Group MMS:** Emits multiple `HDR_TO` headers, one per recipient.
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

    // ── WSP well-known content-type short integers (WAP-230 Table 40) ────────
    // Short-int value is `code | 0x80`.
    private const val CT_MULTIPART_RELATED_SHORT = 0xB3 // 0x33 application/vnd.wap.multipart.related
    private const val CT_TEXT_PLAIN_SHORT        = 0x83 // 0x03 text/plain

    // ── WSP part-header field codes (WAP-230 Table 39, short-int = code|0x80) ─
    private const val PART_CONTENT_LOCATION = 0x8E // 0x0E Content-Location
    private const val PART_CONTENT_ID       = 0xC0 // 0x40 Content-ID

    // ── WSP parameter tokens (WAP-230 Table 38) ──────────────────────────────
    private const val PARAM_CHARSET = 0x81 // 0x01 charset
    private const val PARAM_TYPE    = 0x89 // 0x09 type (multipart/related root media type)
    private const val PARAM_START   = 0x8A // 0x0A start (multipart/related root part cid)

    // UTF-8 well-known charset (IANA MIBenum 106 | 0x80)
    private const val CHARSET_UTF8 = 0xEA

    // ── Well-known Content-IDs & filenames used in our SMIL ──────────────────
    private const val SMIL_CID  = "<smil>"
    private const val SMIL_CL   = "smil.xml"
    private const val TEXT_CID  = "<text_0>"
    private const val TEXT_CL   = "text_0.txt"
    private const val IMAGE_CID = "<image_0>"

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Build a complete MMS m-send-req PDU.
     *
     * @param to            Recipient phone numbers. Emits one HDR_TO per recipient;
     *                      >1 entries makes this a group MMS.
     * @param text          Optional text body; null/blank omits the text part.
     * @param imageBytes    Optional image bytes; null/empty omits the image part.
     * @param imageMimeType MIME type of [imageBytes] (e.g. `image/jpeg`).
     */
    fun build(
        to: List<String>,
        text: String?,
        imageBytes: ByteArray?,
        imageMimeType: String = "image/jpeg"
    ): ByteArray {
        require(to.isNotEmpty()) { "MMS requires at least one recipient" }

        val hasText = !text.isNullOrBlank()
        val hasImage = imageBytes != null && imageBytes.isNotEmpty()
        val imageName = "image.${imageMimeType.substringAfter('/', "jpg")}"

        val out = ByteArrayOutputStream()

        // ── MMS envelope headers ──────────────────────────────────────────────
        out.byte(HDR_MESSAGE_TYPE); out.byte(0x80) // m-send-req
        out.byte(HDR_TRANSACTION_ID); out.nullString("wew_${System.currentTimeMillis()}")
        out.byte(HDR_MMS_VERSION); out.byte(0x92) // MMS 1.2
        out.byte(HDR_DATE); out.longInt(System.currentTimeMillis() / 1000L)

        // From: insert-address-token (value-length=1, then 0x81 address-present-token
        // but length=1 with 0x81 means "insert-address-token" — let the MMSC fill in).
        out.byte(HDR_FROM); out.byte(0x01); out.byte(0x81)

        // To: one repeated header per recipient (group MMS if > 1).
        for (recipient in to) {
            out.byte(HDR_TO)
            out.nullString(plmn(recipient))
        }

        out.byte(HDR_MESSAGE_CLASS); out.byte(0x80)   // Personal
        out.byte(HDR_DELIVERY_REPORT); out.byte(0x81) // No

        // ── Content-Type (MMS envelope): multipart.related; type=..; start=.. ─
        // Uses general form: value-length Media-type Params…
        val envCtValue = ByteArrayOutputStream().apply {
            write(CT_MULTIPART_RELATED_SHORT)
            write(PARAM_TYPE); nullString("application/smil")
            write(PARAM_START); nullString(SMIL_CID)
        }.toByteArray()
        out.byte(HDR_CONTENT_TYPE)
        out.valueLength(envCtValue.size)
        out.write(envCtValue)

        // ── WSP multipart body: entry count + each entity ─────────────────────
        val smil = buildSmilDocument(hasText, hasImage, imageName)
        val entities = mutableListOf<ByteArray>()
        entities += smilEntity(smil)
        if (hasText) entities += textEntity(text!!)
        if (hasImage) entities += imageEntity(imageBytes!!, imageMimeType, imageName)

        out.uintVar(entities.size)
        entities.forEach { out.write(it) }

        return out.toByteArray()
    }

    // ── SMIL presentation ─────────────────────────────────────────────────────

    /**
     * Tiny SMIL document that binds the text/image payloads into one
     * presentation. Filenames must match each entity's Content-Location.
     * Image on top, text below — same layout AOSP's composer uses for
     * caption+photo.
     */
    private fun buildSmilDocument(hasText: Boolean, hasImage: Boolean, imageName: String): String {
        val layout = """
            <layout>
              <root-layout width="320px" height="480px"/>
              <region id="Image" top="0" left="0" height="60%" width="100%" fit="meet"/>
              <region id="Text" top="60%" left="0" height="40%" width="100%" fit="meet"/>
            </layout>
        """.trimIndent()

        val par = buildString {
            append("<par dur=\"8000ms\">")
            if (hasImage) append("<img src=\"$imageName\" region=\"Image\"/>")
            if (hasText) append("<text src=\"$TEXT_CL\" region=\"Text\"/>")
            append("</par>")
        }

        return "<smil><head>$layout</head><body>$par</body></smil>"
    }

    // ── Entity builders ───────────────────────────────────────────────────────
    //
    // Each entity's headers begin DIRECTLY with the Content-Type value.
    // No 0x91 prefix here — see class KDoc.

    private fun smilEntity(smil: String): ByteArray {
        // Content-Type value: text-string "application/smil".
        // Content-ID: <smil>
        // Content-Location: smil.xml
        val hdr = ByteArrayOutputStream().apply {
            writeAsciiZ("application/smil")
            write(PART_CONTENT_ID); nullString(SMIL_CID)
            write(PART_CONTENT_LOCATION); nullString(SMIL_CL)
        }.toByteArray()
        return entity(hdr, smil.toByteArray(Charsets.US_ASCII))
    }

    private fun textEntity(text: String): ByteArray {
        // Content-Type value: general form (has charset param).
        //   value-length + short-int text/plain + charset-token + UTF-8
        val ctValue = ByteArrayOutputStream().apply {
            write(CT_TEXT_PLAIN_SHORT)
            write(PARAM_CHARSET)
            write(CHARSET_UTF8)
        }.toByteArray()

        val hdr = ByteArrayOutputStream().apply {
            valueLength(ctValue.size)
            write(ctValue)
            write(PART_CONTENT_ID); nullString(TEXT_CID)
            write(PART_CONTENT_LOCATION); nullString(TEXT_CL)
        }.toByteArray()

        return entity(hdr, text.toByteArray(Charsets.UTF_8))
    }

    private fun imageEntity(imageBytes: ByteArray, mimeType: String, imageName: String): ByteArray {
        // Content-Type value: text-string form (mime as ASCII, null-terminated).
        val hdr = ByteArrayOutputStream().apply {
            writeAsciiZ(mimeType)
            write(PART_CONTENT_ID); nullString(IMAGE_CID)
            write(PART_CONTENT_LOCATION); nullString(imageName)
        }.toByteArray()
        return entity(hdr, imageBytes)
    }

    /**
     * Serialize one multipart entity per WAP-230 §8.5.2:
     *   Uintvar HeadersLen, Uintvar DataLen, HeadersBytes, DataBytes.
     */
    private fun entity(headers: ByteArray, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.uintVar(headers.size)
        out.uintVar(data.size)
        out.write(headers)
        out.write(data)
        return out.toByteArray()
    }

    // ── Address formatting ────────────────────────────────────────────────────

    /** Format a phone number as the PLMN address form MMS headers require. */
    private fun plmn(number: String): String {
        val digits = number.replace(Regex("[^+\\d]"), "")
        return "$digits/TYPE=PLMN"
    }

    // ── WSP encoding helpers ──────────────────────────────────────────────────

    private fun ByteArrayOutputStream.byte(v: Int) = write(v)

    private fun ByteArrayOutputStream.nullString(s: String) {
        write(s.toByteArray(Charsets.ISO_8859_1))
        write(0x00)
    }

    private fun ByteArrayOutputStream.writeAsciiZ(s: String) {
        write(s.toByteArray(Charsets.US_ASCII))
        write(0x00)
    }

    /**
     * WSP Value-length (WAP-230 §8.4.2.2):
     *   Short-length   = 0-30              (single byte)
     *   Length-quote   = 0x1F + Uintvar    (lengths ≥ 31)
     *
     * Different from Uintvar-integer — which is what part `HeadersLen`/`DataLen`
     * use. The Content-Type field value in general form must use this encoding.
     */
    private fun ByteArrayOutputStream.valueLength(size: Int) {
        if (size <= 30) {
            write(size)
        } else {
            write(0x1F)
            uintVar(size)
        }
    }

    /**
     * WSP Long-integer: 1-byte count then big-endian bytes of the value.
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
