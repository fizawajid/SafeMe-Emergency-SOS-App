package com.example.finalproject

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device booted")

            // Check if shake detection was enabled before
            if (ShakeDetectionManager.isEnabled(context)) {
                Log.d("BootReceiver", "Restarting shake detection service")
                ShakeDetectorService.start(context)
            }
        }
    }
}