package com.cardock.headunit

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var txtStatusOverlay: TextView
    private lateinit var btnGrantOverlay: Button
    private lateinit var txtStatusAccessibility: TextView
    private lateinit var btnGrantAccessibility: Button
    private lateinit var rgIntegrationMode: RadioGroup
    private lateinit var radioOptionA: RadioButton
    private lateinit var radioOptionB: RadioButton
    private lateinit var btnStartDock: Button
    private lateinit var btnStopDock: Button

    private lateinit var prefs: SharedPreferences

    companion object {
        const val PREFS_NAME = "car_dock_prefs"
        const val PREF_INTEGRATION_MODE = "integration_mode"
        const val MODE_OPTION_A = "option_a" // Superposition
        const val MODE_OPTION_B = "option_b" // Redimensionnement d'écran
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        initViews()
        loadSavedPreferences()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionsStatus()
    }

    private fun initViews() {
        txtStatusOverlay = findViewById(R.id.txtStatusOverlay)
        btnGrantOverlay = findViewById(R.id.btnGrantOverlay)
        txtStatusAccessibility = findViewById(R.id.txtStatusAccessibility)
        btnGrantAccessibility = findViewById(R.id.btnGrantAccessibility)
        rgIntegrationMode = findViewById(R.id.rgIntegrationMode)
        radioOptionA = findViewById(R.id.radioOptionA)
        radioOptionB = findViewById(R.id.radioOptionB)
        btnStartDock = findViewById(R.id.btnStartDock)
        btnStopDock = findViewById(R.id.btnStopDock)
    }

    private fun loadSavedPreferences() {
        val currentMode = prefs.getString(PREF_INTEGRATION_MODE, MODE_OPTION_B)
        if (currentMode == MODE_OPTION_A) {
            radioOptionA.isChecked = true
        } else {
            radioOptionB.isChecked = true
        }
    }

    private fun setupListeners() {
        // 1. Bouton Permission Superposition (Overlay)
        btnGrantOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        // 2. Bouton Permission Accessibilité (Pour Retour et Home)
        btnGrantAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(
                this,
                "Activez 'Car HeadUnit Dock' dans la liste d'accessibilité",
                Toast.LENGTH_LONG
            ).show()
        }

        // 3. Changement de Mode d'intégration (Option A vs Option B)
        rgIntegrationMode.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.radioOptionA) {
                prefs.edit().putString(PREF_INTEGRATION_MODE, MODE_OPTION_A).apply()
                DisplayResizeManager.applyOptionA()
                Toast.makeText(this, "Option A activée : Superposition plein écran", Toast.LENGTH_SHORT).show()
            } else {
                prefs.edit().putString(PREF_INTEGRATION_MODE, MODE_OPTION_B).apply()
                val density = resources.displayMetrics.density
                val heightPx = (50 * density).toInt()
                DisplayResizeManager.applyOptionB(this, heightPx)
                Toast.makeText(this, "Option B activée : Écran redimensionné (Zéro masquage)", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. Démarrer la barre
        btnStartDock.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Activez d'abord la permission de superposition !", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startDockService()
            Toast.makeText(this, "Barre activée !", Toast.LENGTH_SHORT).show()
        }

        // 5. Masquer la barre
        btnStopDock.setOnClickListener {
            val intent = Intent(this, CarDockOverlayService::class.java)
            stopService(intent)
            Toast.makeText(this, "Barre masquée", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startDockService() {
        val intent = Intent(this, CarDockOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun updatePermissionsStatus() {
        val hasOverlay = Settings.canDrawOverlays(this)
        if (hasOverlay) {
            txtStatusOverlay.text = "• Affichage par-dessus les applications : Activé ✓"
            txtStatusOverlay.setTextColor(getColor(R.color.car_accent_amber))
            btnGrantOverlay.isEnabled = false
            btnGrantOverlay.alpha = 0.5f
        } else {
            txtStatusOverlay.text = "• Affichage par-dessus les applications : Non autorisé"
            txtStatusOverlay.setTextColor(getColor(R.color.car_text_primary))
            btnGrantOverlay.isEnabled = true
            btnGrantOverlay.alpha = 1.0f
        }

        val hasAccessibility = CarNavAccessibilityService.isRunning()
        if (hasAccessibility) {
            txtStatusAccessibility.text = "• Service Accessibilité (Retour/Home) : Connecté ✓"
            txtStatusAccessibility.setTextColor(getColor(R.color.car_accent_amber))
            btnGrantAccessibility.isEnabled = false
            btnGrantAccessibility.alpha = 0.5f
        } else {
            txtStatusAccessibility.text = "• Service Accessibilité (Retour/Home) : Désactivé"
            txtStatusAccessibility.setTextColor(getColor(R.color.car_text_primary))
            btnGrantAccessibility.isEnabled = true
            btnGrantAccessibility.alpha = 1.0f
        }
    }
}
