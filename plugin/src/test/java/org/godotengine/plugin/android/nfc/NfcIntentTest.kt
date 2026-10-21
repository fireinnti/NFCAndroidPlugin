package org.godotengine.plugin.android.nfc

import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
class NfcIntentTest {
    @Test fun webAndCustomLinksNeedNoTagExtra() {
        val uri = "deadright://card/basic/red"
        assertEquals(listOf(uri), GodotAndroidNFCPlugin.decodeIntent(Intent(Intent.ACTION_VIEW, Uri.parse(uri))))
    }

    @Test fun ndefMessagesDecodeAllSupportedRecordsOnce() {
        val message = NdefMessage(arrayOf(
            NdefRecord.createTextRecord("en", "basic,red"),
            NdefRecord.createUri("deadright://card/token/hard"),
            NdefRecord.createApplicationRecord("com.nfc.testproject")
        ))
        val intent = Intent(NfcAdapter.ACTION_NDEF_DISCOVERED, Uri.parse("deadright://card/token/hard"))
            .putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, arrayOf(message))
        assertEquals(listOf("basic,red", "deadright://card/token/hard"), GodotAndroidNFCPlugin.decodeIntent(intent))
    }

    @Test fun emptyAndUnrelatedIntentsAreIgnored() {
        assertTrue(GodotAndroidNFCPlugin.decodeIntent(Intent(Intent.ACTION_MAIN)).isEmpty())
        assertTrue(GodotAndroidNFCPlugin.decodeIntent(Intent(NfcAdapter.ACTION_TAG_DISCOVERED)).isEmpty())
    }
}
