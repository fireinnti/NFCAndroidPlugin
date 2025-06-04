package org.godotengine.plugin.android.nfc

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.util.Log
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot
import org.godotengine.godot.plugin.SignalInfo

class GodotAndroidPlugin(godot: Godot) : GodotPlugin(godot) {

    private var nfcAdapter: NfcAdapter? = null
    private var previousIntent: Intent? = null
    private var pollingThread: Thread? = null
    @Volatile private var keepPolling = false

    override fun getPluginName() = BuildConfig.GODOT_PLUGIN_NAME

    override fun getPluginMethods(): List<String> {
        return listOf("enableNFC")
    }

    override fun getPluginSignals(): Set<SignalInfo> {
        return setOf(
            SignalInfo("tag_readed", ByteArray::class.java)
        )
    }

    @UsedByGodot
    fun enableNFC() {
        val activity = activity ?: return
        nfcAdapter = NfcAdapter.getDefaultAdapter(activity)

        if (nfcAdapter?.isEnabled == true) {
            val intent = Intent(activity, activity.javaClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

            val pendingIntent = PendingIntent.getActivity(
                activity, 0, intent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            nfcAdapter?.enableForegroundDispatch(activity, pendingIntent, null, null)

            startPollingThread()
        } else {
            Log.w("GodotNFC", "NFC is disabled or unavailable.")
        }
    }

    private fun decodeTextPayload(payload: ByteArray): String {
        val statusByte = payload[0].toInt()
        val languageCodeLength = statusByte and 0x3F
        return String(payload, languageCodeLength + 1, payload.size - languageCodeLength - 1, Charsets.UTF_8)
    }


    private fun startPollingThread() {
        if (pollingThread != null && pollingThread!!.isAlive) return

        keepPolling = true
        pollingThread = Thread {
            while (keepPolling) {
                try {
                    pollTags()
                    Thread.sleep(500)
                } catch (e: Exception) {
                    Log.e("GodotNFC", "Polling error: ${e.message}")
                }
            }
        }
        pollingThread!!.start()
    }

    private fun stopPollingThread() {
        keepPolling = false
        pollingThread?.interrupt()
        pollingThread = null
    }

    private fun pollTags() {
        val activity = activity ?: return
        val intent = activity.intent ?: return

        if (intent != previousIntent && intent.hasExtra(NfcAdapter.EXTRA_TAG)) {
            previousIntent = intent

            val messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            if (messages != null) {
                for (rawMsg in messages) {
                    val msg = rawMsg as NdefMessage
                    for (record in msg.records) {

                        val text = decodeTextPayload(record.payload)
                        emitSignal("tag_readed", text.toByteArray(Charsets.UTF_8))
                        Log.d("GodotNFC", "Tag detected, signal emitted.")
                    }
                }
            }
            else{
                emitSignal("tag_readed", "no data".toByteArray(Charsets.UTF_8))
            }

            // Reset intent so tag isn't processed again
            activity.intent = Intent()
        }
    }

    override fun onMainDestroy() {
        stopPollingThread()
        super.onMainDestroy()
    }
}
