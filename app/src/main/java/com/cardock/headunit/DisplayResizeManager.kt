package com.cardock.headunit

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object DisplayResizeManager {
    private const val TAG = "DisplayResizeManager"

    /**
     * Option B : Réserve l'espace sous l'écran pour que les applications (GPS, ZLink)
     * remontent au-dessus de la barre sans être masquées.
     */
    fun applyOptionB(context: Context, bottomPx: Int): Boolean {
        // 1. Tenter la commande standard AOSP wm overscan (autoradios Android 8 à 10)
        val overscanCmd = "wm overscan 0,0,0,$bottomPx"
        val successOverscan = executeCommand(overscanCmd)

        // 2. Si wm overscan n'est pas supporté (Android 11+ ou émulateur), ajuster avec wm size
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels
        val baseHeight = metrics.heightPixels
        val targetHeight = if (baseHeight > 100) baseHeight - bottomPx else 670

        val sizeCmd = "wm size ${width}x${targetHeight}"
        val successSize = executeCommand(sizeCmd)

        return successOverscan || successSize
    }

    /**
     * Option A : Rétablit l'écran complet plein format (superposition standard).
     */
    fun applyOptionA(): Boolean {
        val overscanReset = executeCommand("wm overscan reset")
        val sizeReset = executeCommand("wm size reset")
        return overscanReset || sizeReset
    }

    private fun executeCommand(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            DataOutputStream(process.outputStream).use { os ->
                os.writeBytes("$command\n")
                os.writeBytes("exit\n")
                os.flush()
            }
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                Log.d(TAG, "Exécuté via root : $command")
                return true
            }
            fallbackShell(command)
        } catch (e: Exception) {
            fallbackShell(command)
        }
    }

    private fun fallbackShell(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(command)
            val exitCode = process.waitFor()
            Log.d(TAG, "Exécuté via shell (code $exitCode) : $command")
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "Erreur commande : ${e.message}")
            false
        }
    }
}
