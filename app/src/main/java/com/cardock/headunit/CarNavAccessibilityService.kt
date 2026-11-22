package com.cardock.headunit

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
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

        fun openRecents(): Boolean {
            return instance?.performGlobalAction(GLOBAL_ACTION_RECENTS) ?: false
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "CarNavAccessibilityService connecté et actif.")
        ensureDockRunning()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Au réveil de la voiture (lancement d'une app, changement de fenêtre), s'assurer que le dock est actif
        ensureDockRunning()
    }

    private fun ensureDockRunning() {
        try {
            val prefs = getSharedPreferences(CarDockOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean(CarDockOverlayService.PREF_DOCK_ENABLED, true)
            if (isEnabled && !CarDockOverlayService.isServiceRunning && Settings.canDrawOverlays(this)) {
                Log.d(TAG, "Relance automatique de CarDockOverlayService par le service d'accessibilité...")
                val intent = Intent(this, CarDockOverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur ensureDockRunning: ${e.message}")
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "CarNavAccessibilityService interrompu.")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }
}
