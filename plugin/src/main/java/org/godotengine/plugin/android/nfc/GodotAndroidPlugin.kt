// TODO: Update to match your plugin's package name.
package org.godotengine.plugin.android.nfc

import android.app.PendingIntent
import android.content.Intent
import android.nfc.*
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable

import android.util.Log
import android.widget.Toast
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot
import org.godotengine.godot.plugin.SignalInfo
import java.io.IOException

class GodotNFC(godot: Godot) : GodotPlugin(godot) {

    private var previousIntent: Intent? = null
    private var nfcAdapter: NfcAdapter? = null
    private var status = 0
    private var queuedWriteData: ByteArray? = null

    override fun getPluginName() = BuildConfig.GODOT_PLUGIN_NAME

    override fun getPluginMethods(): List<String> {
        return listOf("enableNFC", "getStatus", "pollTags", "queueWrite")
    }

    override fun getPluginSignals(): Set<SignalInfo> {
        return setOf(
            SignalInfo("nfc_enabled", Int::class.java),
            SignalInfo("tag_readed", ByteArray::class.java),
            SignalInfo("tag_written")
        )
    }

    @UsedByGodot
    fun enableNFC() {
        runOnUiThread {
            val currentActivity = activity // Safely retrieve the nullable activity
            currentActivity?.let { nonNullActivity ->

                nfcAdapter = NfcAdapter.getDefaultAdapter(nonNullActivity)
                if (nfcAdapter == null) {
                    status = -1
                } else if (nfcAdapter!!.isEnabled) {
                    status = 1

                    // Use the non-null activity inside the let block
                    val intent = Intent(nonNullActivity, nonNullActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }

                    val pendingIntent = PendingIntent.getActivity(
                        nonNullActivity, 0, intent, PendingIntent.FLAG_MUTABLE
                    )

                    nfcAdapter!!.enableForegroundDispatch(nonNullActivity, pendingIntent, null, null)
                } else {
                    status = 0
                }

                // Emit the signal after setting the status
                emitSignal("nfc_enabled", status)
            } ?: run {
                // Handle the case where activity is null
                Log.e("GodotNFC", "Activity is null, cannot enable NFC")
                status = -1
                emitSignal("nfc_enabled", status)
            }
        }
    }


    @UsedByGodot
    fun getStatus(): Int {
        return status
    }

    @UsedByGodot
    fun pollTags() {
        val currentActivity = activity // Get the current activity
        val intent = currentActivity?.intent // Get the current intent from the activity

        // Check if intent is not null and different from previous intent
        if (intent != null && intent != previousIntent) {
            previousIntent = intent

            if (intent.hasExtra(NfcAdapter.EXTRA_TAG)) {
                if (queuedWriteData == null) { // Read mode
                    val parcelables = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
                    parcelables?.forEach { parcelable ->
                        val ndefMessage = parcelable as NdefMessage
                        ndefMessage.records.forEach { record ->
                            val payload = record.payload
                            emitSignal("tag_readed", payload)
                        }
                    }
                } else { // Write mode
                    val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
                    tag?.let {
                        writeTag(it, queuedWriteData!!)
                        queuedWriteData = null
                    }
                }
            }
        }
    }


    @UsedByGodot
    fun queueWrite(data: ByteArray) {
        queuedWriteData = data
    }

    private fun writeTag(tag: Tag, data: ByteArray) {
        val relayRecord = NdefRecord.createTextRecord(null, String(data))
        val message = NdefMessage(arrayOf(relayRecord))

        val ndef = Ndef.get(tag)
        if (ndef == null) {
            // Tag not formatted
            val format = NdefFormatable.get(tag)
            if (format != null) {
                try {
                    format.connect()
                    format.format(message)
                    emitSignal("tag_written")
                } catch (e: IOException) {
                    Log.e(pluginName, "Failed to format tag", e)
                }
            }
        } else {
            // Tag already formatted
            try {
                ndef.connect()
                if (ndef.isWritable && data.size <= ndef.maxSize) {
                    try {
                        ndef.writeNdefMessage(message)
                        emitSignal("tag_written")
                    } catch (e: IOException) {
                        Log.e(pluginName, "Failed to write to tag", e)
                    }
                }
            } catch (e: IOException) {
                Log.e(pluginName, "Failed to connect to tag", e)
            }
        }
    }

    /**
     * Example showing how to declare a method that's used by Godot.
     *
     * Shows a 'Hello World' toast.
     */
    @UsedByGodot
    private fun helloWorld() {
        runOnUiThread {
            Toast.makeText(activity, "Hello World", Toast.LENGTH_LONG).show()
            Log.v(pluginName, "Hello World")
        }
    }
}
