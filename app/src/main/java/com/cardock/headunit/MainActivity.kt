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

    private lateinit var rgMenuOrientation: RadioGroup
    private lateinit var radioOrientHorizontal: RadioButton
    private lateinit var radioOrientVertical: RadioButton

    private lateinit var rgButtonSize: RadioGroup
    private lateinit var radioSizeCompact: RadioButton
    private lateinit var radioSizeMedium: RadioButton
    private lateinit var radioSizeLarge: RadioButton
    private lateinit var radioSizeXLarge: RadioButton

    private lateinit var rgIntegrationMode: RadioGroup
    private lateinit var radioOptionA: RadioButton
    private lateinit var radioOptionB: RadioButton

    private lateinit var switchSnapToEdge: androidx.appcompat.widget.SwitchCompat

    private lateinit var btnStartDock: Button
    private lateinit var btnStopDock: Button

    private lateinit var prefs: SharedPreferences

    companion object {
        const val PREFS_NAME = "car_dock_prefs"
        const val PREF_INTEGRATION_MODE = "integration_mode"
        const val MODE_OPTION_A = "option_a" // Superposition
        const val MODE_OPTION_B = "option_b" // Redimensionnement d'écran

        const val PREF_DOCK_ORIENTATION = "dock_orientation"
        const val ORIENT_HORIZONTAL = "horizontal"
        const val ORIENT_VERTICAL = "vertical"

        const val PREF_DOCK_SIZE = "dock_size"
        const val SIZE_COMPACT = "compact" // 44dp
        const val SIZE_MEDIUM = "medium"   // 56dp
        const val SIZE_LARGE = "large"     // 68dp
        const val SIZE_XLARGE = "xlarge"   // 80dp

        const val PREF_SNAP_TO_EDGE = "dock_snap_to_edge"
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

        rgMenuOrientation = findViewById(R.id.rgMenuOrientation)
        radioOrientHorizontal = findViewById(R.id.radioOrientHorizontal)
        radioOrientVertical = findViewById(R.id.radioOrientVertical)

        rgButtonSize = findViewById(R.id.rgButtonSize)
        radioSizeCompact = findViewById(R.id.radioSizeCompact)
        radioSizeMedium = findViewById(R.id.radioSizeMedium)
        radioSizeLarge = findViewById(R.id.radioSizeLarge)
        radioSizeXLarge = findViewById(R.id.radioSizeXLarge)

        rgIntegrationMode = findViewById(R.id.rgIntegrationMode)
        radioOptionA = findViewById(R.id.radioOptionA)
        radioOptionB = findViewById(R.id.radioOptionB)

        switchSnapToEdge = findViewById(R.id.switchSnapToEdge)

        btnStartDock = findViewById(R.id.btnStartDock)
        btnStopDock = findViewById(R.id.btnStopDock)
    }

    private fun loadSavedPreferences() {
        // 1. Format Orientation
        val orientation = prefs.getString(PREF_DOCK_ORIENTATION, ORIENT_HORIZONTAL)
        if (orientation == ORIENT_VERTICAL) {
            rgMenuOrientation.check(R.id.radioOrientVertical)
        } else {
            rgMenuOrientation.check(R.id.radioOrientHorizontal)
        }

        // 2. Taille des boutons
        when (prefs.getString(PREF_DOCK_SIZE, SIZE_LARGE)) {
            SIZE_COMPACT -> rgButtonSize.check(R.id.radioSizeCompact)
            SIZE_MEDIUM -> rgButtonSize.check(R.id.radioSizeMedium)
            SIZE_XLARGE -> rgButtonSize.check(R.id.radioSizeXLarge)
            else -> rgButtonSize.check(R.id.radioSizeLarge)
        }

        // 3. Aimantation Intelligente (Snap to Edge)
        switchSnapToEdge.isChecked = prefs.getBoolean(PREF_SNAP_TO_EDGE, true)

        // 4. Mode Intégration
        val currentMode = prefs.getString(PREF_INTEGRATION_MODE, MODE_OPTION_B)
        if (currentMode == MODE_OPTION_A) {
            rgIntegrationMode.check(R.id.radioOptionA)
        } else {
            rgIntegrationMode.check(R.id.radioOptionB)
        }
    }

    private fun setupListeners() {
        // 1. Permission Overlay
        btnGrantOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        // 2. Permission Accessibilité
        btnGrantAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(
                this,
                "Activez 'Car HeadUnit Dock' dans la liste d'accessibilité",
                Toast.LENGTH_LONG
            ).show()
        }

        // 3. Format du Menu (Horizontal vs Vertical)
        rgMenuOrientation.setOnCheckedChangeListener { _, checkedId ->
            val newOrient = if (checkedId == R.id.radioOrientVertical) ORIENT_VERTICAL else ORIENT_HORIZONTAL
            prefs.edit().putString(PREF_DOCK_ORIENTATION, newOrient).apply()
            notifyDockConfigChanged()
            val label = if (newOrient == ORIENT_VERTICAL) "Vertical (Colonne)" else "Horizontal (Ligne)"
            Toast.makeText(this, "Format du menu : $label", Toast.LENGTH_SHORT).show()
        }

        // 4. Taille globale de la barre (Compact, Medium, Large, XLarge)
        rgButtonSize.setOnCheckedChangeListener { _, checkedId ->
            val newSize = when (checkedId) {
                R.id.radioSizeCompact -> SIZE_COMPACT
                R.id.radioSizeMedium -> SIZE_MEDIUM
                R.id.radioSizeXLarge -> SIZE_XLARGE
                else -> SIZE_LARGE
            }
            prefs.edit().putString(PREF_DOCK_SIZE, newSize).apply()
            notifyDockConfigChanged()
            val label = when (newSize) {
                SIZE_COMPACT -> "Compact (Barre 56 dp)"
                SIZE_MEDIUM -> "Standard (Barre 68 dp)"
                SIZE_XLARGE -> "Très Grand (Barre 96 dp)"
                else -> "Grand (Barre 82 dp — Recommandé)"
            }
            Toast.makeText(this, "Format de barre : $label", Toast.LENGTH_SHORT).show()
        }

        // 5. Aimantation Intelligente (Snap to Edge)
        switchSnapToEdge.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_SNAP_TO_EDGE, isChecked).apply()
            notifyDockConfigChanged()
            val msg = if (isChecked) {
                "Aimantation intelligente activée"
            } else {
                "Aimantation désactivée (Position libre)"
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // 6. Mode Intégration
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

        // 6. Démarrer la barre
        btnStartDock.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Activez d'abord la permission de superposition !", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startDockService()
            Toast.makeText(this, "Barre activée !", Toast.LENGTH_SHORT).show()
        }

        // 7. Masquer la barre
        btnStopDock.setOnClickListener {
            val intent = Intent(this, CarDockOverlayService::class.java)
            stopService(intent)
            Toast.makeText(this, "Barre masquée", Toast.LENGTH_SHORT).show()
        }
    }

    private fun notifyDockConfigChanged() {
        if (CarDockOverlayService.isServiceRunning) {
            val intent = Intent(this, CarDockOverlayService::class.java).apply {
                action = CarDockOverlayService.ACTION_UPDATE_CONFIG
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
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
