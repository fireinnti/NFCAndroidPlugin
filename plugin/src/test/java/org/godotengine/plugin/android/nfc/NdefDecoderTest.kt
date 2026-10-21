package org.godotengine.plugin.android.nfc

import android.nfc.NdefRecord
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NdefDecoderTest {
    @Test fun textRecordRemovesLanguageHeader() {
        assertEquals("basic,red", NdefDecoder.decode(NdefRecord.createTextRecord("en", "basic,red")))
    }

    @Test fun utf16TextIsDecoded() {
        val payload = byteArrayOf(0x82.toByte(), 'e'.code.toByte(), 'n'.code.toByte()) +
            "token,hard".toByteArray(Charsets.UTF_16)
        val record = NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, byteArrayOf(), payload)
        assertEquals("token,hard", NdefDecoder.decode(record))
    }

    @Test fun uriPrefixIsExpanded() {
        for (uri in listOf("https://game.example/nfc/basic/red", "nfcgame://card/power/blue/skip")) {
            assertEquals(uri, NdefDecoder.decode(NdefRecord.createUri(uri)))
        }
    }

    @Test fun malformedTextDoesNotCrash() {
        for (payload in listOf(byteArrayOf(), byteArrayOf(63), byteArrayOf(2, 101))) {
            val record = NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, byteArrayOf(), payload)
            assertNull(NdefDecoder.decode(record))
        }
    }

    @Test fun mimeTextAndUnsupportedRecords() {
        assertEquals("basic,green", NdefDecoder.decode(NdefRecord.createMime("text/plain", "basic,green".toByteArray())))
        assertNull(NdefDecoder.decode(NdefRecord.createApplicationRecord("com.nfc.testproject")))
    }
}
