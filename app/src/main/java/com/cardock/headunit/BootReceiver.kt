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
        Log.d(TAG, "Événement de démarrage détecté : ${intent.action}")

        // Vérifier si l'autorisation d'overlay est accordée
        if (Settings.canDrawOverlays(context)) {
            val serviceIntent = Intent(context, CarDockOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(TAG, "CarDockOverlayService démarré avec succès au boot de la voiture.")
        } else {
            Log.w(TAG, "Impossible de démarrer la barre : permission SYSTEM_ALERT_WINDOW manquante.")
        }
    }
}
