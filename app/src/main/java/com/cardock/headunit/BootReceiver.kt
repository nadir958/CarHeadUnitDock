package com.cardock.headunit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "CarBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Événement de démarrage / réveil détecté : ${intent.action}")

        val prefs = context.getSharedPreferences(CarDockOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(CarDockOverlayService.PREF_DOCK_ENABLED, true)

        if (isEnabled && Settings.canDrawOverlays(context)) {
            val serviceIntent = Intent(context, CarDockOverlayService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.d(TAG, "CarDockOverlayService démarré avec succès au boot / réveil de la voiture.")
            } catch (e: Exception) {
                Log.e(TAG, "Erreur au démarrage du service : ${e.message}")
            }
        } else {
            Log.w(TAG, "Démarrage automatique ignoré : activé=$isEnabled, overlay=${Settings.canDrawOverlays(context)}")
        }
    }
}
