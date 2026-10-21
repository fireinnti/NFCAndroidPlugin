package org.godotengine.plugin.android.nfc

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Build
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot

class GodotAndroidNFCPlugin(godot: Godot) : GodotPlugin(godot) {
    private var enabled = false
    private var resumed = false
    private var dispatchEnabled = false
    private val adapter: NfcAdapter?
        get() = activity?.let { NfcAdapter.getDefaultAdapter(it) }

    init { instance = this }

    override fun getPluginName() = BuildConfig.GODOT_PLUGIN_NAME

    override fun getPluginSignals() = setOf(
        SignalInfo("tag_read", String::class.java),
        // Compatibility with existing projects; bytes now contain decoded UTF-8.
        SignalInfo("tag_readed", ByteArray::class.java),
        SignalInfo("nfc_error", String::class.java)
    )

    @UsedByGodot fun isNFCAvailable(): Boolean = adapter != null
    @UsedByGodot fun isNFCEnabled(): Boolean = adapter?.isEnabled == true

    /** Connect signals before calling this; queued launch tags are delivered here. */
    @UsedByGodot fun enableNFC() = runOnHostThread {
        enabled = true
        updateForegroundDispatch()
        flushPending()
    }

    @UsedByGodot fun disableNFC() = runOnHostThread {
        enabled = false
        updateForegroundDispatch()
    }

    override fun onMainResume() {
        resumed = true
        updateForegroundDispatch()
    }

    override fun onMainPause() {
        resumed = false
        updateForegroundDispatch()
    }

    private fun updateForegroundDispatch() {
        val host = activity ?: return
        val nfc = adapter ?: return
        try {
            if (enabled && resumed && nfc.isEnabled) {
                val intent = Intent(host, GodotAndroidNFCActivityV2::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                nfc.enableForegroundDispatch(host, PendingIntent.getActivity(host, 0, intent, flags), null, null)
                dispatchEnabled = true
            } else if (dispatchEnabled) {
                nfc.disableForegroundDispatch(host)
                dispatchEnabled = false
            }
        } catch (error: IllegalStateException) {
            dispatchEnabled = false
            emitSignal("nfc_error", error.message ?: "Unable to change NFC foreground dispatch")
        } catch (error: SecurityException) {
            emitSignal("nfc_error", error.message ?: "NFC permission missing")
        }
    }

    private fun flushPending() {
        if (!enabled) return
        while (true) {
            val value = synchronized(pending) {
                if (pending.isEmpty()) null else pending.removeFirst()
            } ?: break
            emitSignal("tag_read", value)
            emitSignal("tag_readed", value.toByteArray(Charsets.UTF_8))
        }
    }

    override fun onMainDestroy() {
        enabled = false
        resumed = false
        updateForegroundDispatch()
        if (instance === this) instance = null
        super.onMainDestroy()
    }

    companion object {
        @Volatile private var instance: GodotAndroidNFCPlugin? = null
        private val pending = ArrayDeque<String>()

        /** The bridge queues before starting Godot, so a cold launch cannot lose the tag. */
        internal fun acceptIntent(intent: Intent): Boolean {
            val values = decodeIntent(intent)
            if (values.isEmpty()) return false
            synchronized(pending) {
                for (value in values) {
                    if (pending.size >= 32) pending.removeFirst()
                    pending.addLast(value)
                }
            }
            instance?.let { plugin -> plugin.runOnHostThread { plugin.flushPending() } }
            return true
        }

        @Suppress("DEPRECATION")
        internal fun decodeIntent(intent: Intent): List<String> {
            if (intent.action == Intent.ACTION_VIEW) {
                return listOfNotNull(intent.dataString)
            }
            if (intent.action !in setOf(NfcAdapter.ACTION_NDEF_DISCOVERED,
                    NfcAdapter.ACTION_TECH_DISCOVERED, NfcAdapter.ACTION_TAG_DISCOVERED)) return emptyList()
            val messages = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
            } else {
                intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            }
            val values = messages.orEmpty().filterIsInstance<NdefMessage>()
                .flatMap { it.records.toList() }.mapNotNull { NdefDecoder.decode(it) }
            return values.ifEmpty { listOfNotNull(intent.dataString) }
        }
    }
}
