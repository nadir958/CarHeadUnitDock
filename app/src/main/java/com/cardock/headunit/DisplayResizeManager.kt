package com.cardock.headunit

import android.content.Context
import android.util.Log
import java.io.DataOutputStream

object DisplayResizeManager {
    private const val TAG = "DisplayResizeManager"

    /**
     * Réserve physiquement l'espace sur l'écran pour que les applications (GPS, ZLink, Spotify)
     * s'affichent uniquement dans la zone restante sans aucun chevauchement.
     */
    fun applyInsets(context: Context, leftPx: Int = 0, topPx: Int = 0, rightPx: Int = 0, bottomPx: Int = 0): Boolean {
        Log.d(TAG, "applyInsets: left=$leftPx, top=$topPx, right=$rightPx, bottom=$bottomPx")
        // 1. Tenter la commande standard AOSP wm overscan (autoradios Android 8 à 10)
        val overscanCmd = "wm overscan $leftPx,$topPx,$rightPx,$bottomPx"
        val successOverscan = executeCommand(overscanCmd)

        // 2. Si wm overscan n'est pas supporté (certains émulateurs ou Android 11+), fallback wm size
        if (!successOverscan && (leftPx > 0 || bottomPx > 0)) {
            val metrics = context.resources.displayMetrics
            val baseWidth = metrics.widthPixels
            val baseHeight = metrics.heightPixels

            val targetWidth = if (baseWidth > leftPx + rightPx + 50) baseWidth - leftPx - rightPx else baseWidth
            val targetHeight = if (baseHeight > topPx + bottomPx + 50) baseHeight - topPx - bottomPx else baseHeight

            val sizeCmd = "wm size ${targetWidth}x${targetHeight}"
            executeCommand(sizeCmd)
        }

        return successOverscan
    }

    /**
     * Rétablit l'écran complet plein format (superposition standard).
     */
    fun resetInsets(): Boolean {
        Log.d(TAG, "resetInsets")
        val overscanReset = executeCommand("wm overscan reset")
        val sizeReset = executeCommand("wm size reset")
        return overscanReset || sizeReset
    }

    // Rétrocompatibilité avec les anciens appels
    fun applyOptionB(context: Context, bottomPx: Int): Boolean {
        return applyInsets(context, bottomPx = bottomPx)
    }

    fun applyOptionA(): Boolean {
        return resetInsets()
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
                Log.d(TAG, "Exécuté avec succès via root : $command")
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
            Log.e(TAG, "Erreur commande shell : ${e.message}")
            false
        }
    }
}
