package org.godotengine.plugin.android.nfc

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Receives external links and NFC dispatch without modifying Godot's main activity. */
class GodotAndroidNFCActivityV2 : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        forward(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        forward(intent)
    }

    private fun forward(intent: Intent) {
        try {
            if (GodotAndroidNFCPlugin.acceptIntent(intent)) {
                // Resolve the app's actual launcher; do not hardcode an activity class/package.
                packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
                    launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(launch)
                }
            }
        } finally {
            finish()
        }
    }
}
