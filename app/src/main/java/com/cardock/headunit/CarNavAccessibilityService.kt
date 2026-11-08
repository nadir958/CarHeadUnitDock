package com.cardock.headunit

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class CarNavAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CarNavAccessibility"
        private var instance: CarNavAccessibilityService? = null

        fun isRunning(): Boolean = instance != null

        fun goBack(): Boolean {
            return instance?.performGlobalAction(GLOBAL_ACTION_BACK) ?: false
        }

        fun goHome(): Boolean {
            return instance?.performGlobalAction(GLOBAL_ACTION_HOME) ?: false
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "CarNavAccessibilityService connecté et actif.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Pas besoin de traiter les événements de contenu
    }

    override fun onInterrupt() {
        Log.w(TAG, "CarNavAccessibilityService interrompu.")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }
}
