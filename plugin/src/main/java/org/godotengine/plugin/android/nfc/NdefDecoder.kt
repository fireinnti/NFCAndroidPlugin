package org.godotengine.plugin.android.nfc

import android.nfc.NdefRecord

/** Decode NDEF types explicitly: Text and URI payload headers are not game data. */
internal object NdefDecoder {
    fun decode(record: NdefRecord): String? {
        if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT)) {
            val payload = record.payload
            if (payload.isEmpty()) return null
            val status = payload[0].toInt() and 0xff
            if (status and 0x40 != 0) return null
            val offset = 1 + (status and 0x3f)
            if (offset > payload.size) return null
            val charset = if (status and 0x80 != 0) Charsets.UTF_16 else Charsets.UTF_8
            return String(payload, offset, payload.size - offset, charset)
        }
        // Includes compressed URI prefixes and Smart Poster records.
        if (record.tnf == NdefRecord.TNF_ABSOLUTE_URI ||
            (record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                (record.type.contentEquals(NdefRecord.RTD_URI) || record.type.contentEquals(NdefRecord.RTD_SMART_POSTER)))) {
            record.toUri()?.let { return it.toString() }
        }
        if (record.tnf == NdefRecord.TNF_MIME_MEDIA &&
            String(record.type, Charsets.US_ASCII).equals("text/plain", ignoreCase = true)) {
            return String(record.payload, Charsets.UTF_8)
        }
        return null
    }
}
