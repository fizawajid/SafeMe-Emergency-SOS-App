package com.example.finalproject

import android.content.Context
import android.content.SharedPreferences

object ShakeDetectionManager {
    private const val PREF_NAME = "ShakeDetectionPrefs"
    private const val KEY_SHAKE_ENABLED = "shake_detection_enabled"

    fun isEnabled(context: Context): Boolean {
        val prefs = getPrefs(context)
        return prefs.getBoolean(KEY_SHAKE_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = getPrefs(context)
        prefs.edit().putBoolean(KEY_SHAKE_ENABLED, enabled).apply()

        if (enabled) {
            ShakeDetectorService.start(context)
        } else {
            ShakeDetectorService.stop(context)
        }
    }

    fun toggle(context: Context): Boolean {
        val newState = !isEnabled(context)
        setEnabled(context, newState)
        return newState
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
}