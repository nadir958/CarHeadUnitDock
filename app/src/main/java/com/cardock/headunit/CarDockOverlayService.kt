package com.cardock.headunit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat

class CarDockOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager
    private lateinit var prefs: SharedPreferences
    private var dockView: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    private lateinit var container: LinearLayout
    private lateinit var dragHandle: ImageView
    private lateinit var btnBack: ImageButton
    private lateinit var btnHome: ImageButton
    private lateinit var dockSeparator: View
    private lateinit var btnMute: ImageButton
    private lateinit var btnVolDown: ImageButton
    private lateinit var btnVolUp: ImageButton

    private val repeatHandler = Handler(Looper.getMainLooper())
    private var isMuted = false
    private var volumeBeforeMute = 0
    private var currentButtonRadiusPx = 18f

    // Variables pour le déplacement tactile fluide
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    companion object {
        const val CHANNEL_ID = "car_dock_service_channel"
        const val NOTIFICATION_ID = 1001
        const val PREFS_NAME = "car_dock_prefs"
        const val PREF_POS_X = "dock_pos_x"
        const val PREF_POS_Y = "dock_pos_y"

        const val PREF_DOCK_ORIENTATION = "dock_orientation"
        const val ORIENT_HORIZONTAL = "horizontal"
        const val ORIENT_VERTICAL = "vertical"

        const val PREF_DOCK_SIZE = "dock_size"
        const val SIZE_COMPACT = "compact"
        const val SIZE_MEDIUM = "medium"
        const val SIZE_LARGE = "large"
        const val SIZE_XLARGE = "xlarge"

        const val ACTION_UPDATE_CONFIG = "com.cardock.headunit.ACTION_UPDATE_CONFIG"
        var isServiceRunning = false
    }

    private data class DockPreset(
        val buttonSizeDp: Int,
        val dragBreadthDp: Int,
        val padLateralDp: Int,
        val padCrossDp: Int,
        val spacingDp: Int,
        val iconPaddingDp: Int
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        startAsForeground()
        createFloatingDockWindow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_UPDATE_CONFIG) {
            applyDockConfiguration()
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_volume_up)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createFloatingDockWindow() {
        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Position par défaut centrée en bas
        val defaultX = (screenWidth - (380 * density).toInt()) / 2
        val defaultY = screenHeight - (80 * density).toInt() - (16 * density).toInt()

        val savedX = prefs.getInt(PREF_POS_X, defaultX)
        val savedY = prefs.getInt(PREF_POS_Y, defaultY)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX.coerceIn(0, (screenWidth - 100).coerceAtLeast(0))
            y = savedY.coerceIn(0, (screenHeight - 100).coerceAtLeast(0))
        }

        val inflater = LayoutInflater.from(this)
        dockView = inflater.inflate(R.layout.layout_car_dock_bar, null)

        initViews(dockView!!)
        setupDragListener()
        applyDockConfiguration()

        windowManager.addView(dockView, layoutParams)
    }

    private fun initViews(view: View) {
        container = view.findViewById(R.id.floatingDockContainer)
        dragHandle = view.findViewById(R.id.dragHandle)
        btnBack = view.findViewById(R.id.btnBack)
        btnHome = view.findViewById(R.id.btnHome)
        dockSeparator = view.findViewById(R.id.dockSeparator)
        btnMute = view.findViewById(R.id.btnMute)
        btnVolDown = view.findViewById(R.id.btnVolDown)
        btnVolUp = view.findViewById(R.id.btnVolUp)

        // 1. Action RETOUR
        btnBack.setOnClickListener {
            android.util.Log.d("CarDockOverlayService", "btnBack clicked")
            CarNavAccessibilityService.goBack()
        }

        // 2. Action ACCUEIL (HOME)
        btnHome.setOnClickListener {
            val handled = CarNavAccessibilityService.goHome()
            android.util.Log.d("CarDockOverlayService", "btnHome clicked, handled by accessibility=$handled")
            if (!handled) {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(homeIntent)
            }
        }

        // 3. Action MUTE
        btnMute.setOnClickListener {
            toggleMute()
        }

        // 4. Action VOLUME MOINS (Clic court ou appui prolongé)
        setupRepeatableVolume(btnVolDown, AudioManager.ADJUST_LOWER)

        // 5. Action VOLUME PLUS (Clic court ou appui prolongé)
        setupRepeatableVolume(btnVolUp, AudioManager.ADJUST_RAISE)
    }

    /**
     * Applique une mise à l'échelle harmonieuse et proportionnelle de toute la barre :
     * conteneur capsule, poignée tactile de déplacement, boutons en squircle parfait,
     * séparateur et marges équilibrées.
     */
    private fun applyDockConfiguration() {
        val density = resources.displayMetrics.density
        val orientation = prefs.getString(PREF_DOCK_ORIENTATION, ORIENT_HORIZONTAL) ?: ORIENT_HORIZONTAL
        val sizePref = prefs.getString(PREF_DOCK_SIZE, SIZE_LARGE) ?: SIZE_LARGE

        val preset = when (sizePref) {
            SIZE_COMPACT -> DockPreset(
                buttonSizeDp = 42,
                dragBreadthDp = 28,
                padLateralDp = 10,
                padCrossDp = 7,
                spacingDp = 6,
                iconPaddingDp = 9
            )
            SIZE_MEDIUM -> DockPreset(
                buttonSizeDp = 52,
                dragBreadthDp = 34,
                padLateralDp = 12,
                padCrossDp = 8,
                spacingDp = 8,
                iconPaddingDp = 11
            )
            SIZE_XLARGE -> DockPreset(
                buttonSizeDp = 72,
                dragBreadthDp = 46,
                padLateralDp = 16,
                padCrossDp = 12,
                spacingDp = 11,
                iconPaddingDp = 15
            )
            else -> DockPreset( // SIZE_LARGE (Grand - Recommandé Tablette Voiture)
                buttonSizeDp = 60,
                dragBreadthDp = 38,
                padLateralDp = 14,
                padCrossDp = 10,
                spacingDp = 9,
                iconPaddingDp = 13
            )
        }

        val btnSizePx = (preset.buttonSizeDp * density).toInt()
        val dragBreadthPx = (preset.dragBreadthDp * density).toInt()
        val padLateralPx = (preset.padLateralDp * density).toInt()
        val padCrossPx = (preset.padCrossDp * density).toInt()
        val spacingPx = (preset.spacingDp * density).toInt()
        val iconPaddingPx = (preset.iconPaddingDp * density).toInt()

        // Rayon de courbure proportionnel (Squircle 30%)
        currentButtonRadiusPx = btnSizePx * 0.30f
        val strokeWidthPx = (1.2f * density).toInt().coerceAtLeast(1)

        val isVertical = (orientation == ORIENT_VERTICAL)

        // 1. Configuration de l'orientation du conteneur
        container.orientation = if (isVertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL

        // 2. Padding interne du conteneur
        if (isVertical) {
            container.setPadding(padCrossPx, padLateralPx, padCrossPx, padLateralPx)
        } else {
            container.setPadding(padLateralPx, padCrossPx, padLateralPx, padCrossPx)
        }

        // 3. Fond capsule du conteneur flottant (Courbure continue sur toute la hauteur)
        val crossAxisSize = btnSizePx + (padCrossPx * 2)
        val capsuleRadius = crossAxisSize / 2f
        val capsuleBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#F00A0E17")) // Noir profond automobile 94%
            cornerRadius = capsuleRadius
            setStroke((1.5f * density).toInt().coerceAtLeast(1), Color.parseColor("#2E3D52"))
        }
        container.background = capsuleBg

        // 4. Poignée de déplacement tactile (Bouton de déplacement dédié)
        val dragHandleBg = createRippleSquircle(
            fillColor = Color.parseColor("#131B28"),
            strokeColor = Color.parseColor("#273547"),
            strokeWidthPx = strokeWidthPx,
            rippleColor = Color.parseColor("#344863"),
            radiusPx = currentButtonRadiusPx
        )
        dragHandle.background = dragHandleBg
        dragHandle.rotation = 0f
        dragHandle.setPadding((6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt())

        if (isVertical) {
            dragHandle.setImageResource(R.drawable.ic_drag_handle_vertical)
            dragHandle.layoutParams = LinearLayout.LayoutParams(btnSizePx, dragBreadthPx).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = spacingPx
            }
        } else {
            dragHandle.setImageResource(R.drawable.ic_drag_handle_horizontal)
            dragHandle.layoutParams = LinearLayout.LayoutParams(dragBreadthPx, btnSizePx).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginEnd = spacingPx
            }
        }

        // 5. Stylisation et dimensionnement des boutons
        val normalBtnBgSupplier = {
            createRippleSquircle(
                fillColor = Color.parseColor("#182232"),
                strokeColor = Color.parseColor("#2C3B4E"),
                strokeWidthPx = strokeWidthPx,
                rippleColor = Color.parseColor("#384F6E"),
                radiusPx = currentButtonRadiusPx
            )
        }

        val buttons = listOf(btnBack, btnHome, btnVolDown, btnVolUp)
        for (btn in buttons) {
            btn.background = normalBtnBgSupplier()
            btn.setPadding(iconPaddingPx, iconPaddingPx, iconPaddingPx, iconPaddingPx)
            btn.scaleType = ImageView.ScaleType.FIT_CENTER
        }

        btnMute.setPadding(iconPaddingPx, iconPaddingPx, iconPaddingPx, iconPaddingPx)
        btnMute.scaleType = ImageView.ScaleType.FIT_CENTER
        updateMuteStateVisual()

        // 6. Disposition des boutons et du séparateur selon l'orientation
        val sepThicknessPx = (2f * density).toInt().coerceAtLeast(1)
        val sepLengthPx = (btnSizePx * 0.52f).toInt()
        val sepDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#2E3D52"))
            cornerRadius = (1.5f * density)
        }
        dockSeparator.background = sepDrawable

        if (isVertical) {
            btnBack.layoutParams = LinearLayout.LayoutParams(btnSizePx, btnSizePx).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = spacingPx
            }
            btnHome.layoutParams = LinearLayout.LayoutParams(btnSizePx, btnSizePx).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = spacingPx
            }

            dockSeparator.layoutParams = LinearLayout.LayoutParams(sepLengthPx, sepThicknessPx).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (spacingPx / 2)
                bottomMargin = spacingPx
            }

            btnMute.layoutParams = LinearLayout.LayoutParams(btnSizePx, btnSizePx).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = spacingPx
            }
            btnVolDown.layoutParams = LinearLayout.LayoutParams(btnSizePx, btnSizePx).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = spacingPx
            }
            btnVolUp.layoutParams = LinearLayout.LayoutParams(btnSizePx, btnSizePx).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 0
            }
        } else {
            btnBack.layoutParams = LinearLayout.LayoutParams(btnSizePx, btnSizePx).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginEnd = spacingPx
            }
            btnHome.layoutParams = LinearLayout.LayoutParams(btnSizePx, btnSizePx).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginEnd = spacingPx
            }

            dockSeparator.layoutParams = LinearLayout.LayoutParams(sepThicknessPx, sepLengthPx).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginEnd = spacingPx
            }

            btnMute.layoutParams = LinearLayout.LayoutParams(btnSizePx, btnSizePx).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginEnd = spacingPx
            }
            btnVolDown.layoutParams = LinearLayout.LayoutParams(btnSizePx, btnSizePx).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginEnd = spacingPx
            }
            btnVolUp.layoutParams = LinearLayout.LayoutParams(btnSizePx, btnSizePx).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginEnd = 0
            }
        }

        // 7. Recalcul et repositionnement précis de la fenêtre de superposition
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val totalBreadthPx = (padLateralPx * 2) + dragBreadthPx + (5 * btnSizePx) + (6 * spacingPx) + sepThicknessPx
        val totalCrossPx = btnSizePx + (padCrossPx * 2)

        val estimatedW = if (isVertical) totalCrossPx else totalBreadthPx
        val estimatedH = if (isVertical) totalBreadthPx else totalCrossPx

        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParams.x = layoutParams.x.coerceIn(0, (screenWidth - estimatedW).coerceAtLeast(0))
        layoutParams.y = layoutParams.y.coerceIn(0, (screenHeight - estimatedH).coerceAtLeast(0))

        if (dockView != null && dockView!!.isAttachedToWindow) {
            windowManager.updateViewLayout(dockView, layoutParams)
        }
    }

    private fun setupDragListener() {
        val onTouchDrag = View.OnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()

                    val displayMetrics = resources.displayMetrics
                    val screenWidth = displayMetrics.widthPixels
                    val screenHeight = displayMetrics.heightPixels
                    val viewW = if (dockView?.width ?: 0 > 0) dockView!!.width else 160
                    val viewH = if (dockView?.height ?: 0 > 0) dockView!!.height else 80

                    layoutParams.x = (initialX + deltaX).coerceIn(0, (screenWidth - viewW).coerceAtLeast(0))
                    layoutParams.y = (initialY + deltaY).coerceIn(0, (screenHeight - viewH).coerceAtLeast(0))

                    if (dockView != null && dockView!!.isAttachedToWindow) {
                        windowManager.updateViewLayout(dockView, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    prefs.edit()
                        .putInt(PREF_POS_X, layoutParams.x)
                        .putInt(PREF_POS_Y, layoutParams.y)
                        .apply()
                    true
                }
                else -> false
            }
        }
        dragHandle.setOnTouchListener(onTouchDrag)
    }

    private fun setupRepeatableVolume(button: ImageButton, direction: Int) {
        var isHolding = false
        val runnable = object : Runnable {
            override fun run() {
                if (isHolding) {
                    adjustVolume(direction)
                    repeatHandler.postDelayed(this, 120)
                }
            }
        }

        button.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isHolding = true
                    v.isPressed = true
                    adjustVolume(direction)
                    repeatHandler.postDelayed(runnable, 400)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    isHolding = false
                    v.isPressed = false
                    repeatHandler.removeCallbacks(runnable)
                    v.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    isHolding = false
                    v.isPressed = false
                    repeatHandler.removeCallbacks(runnable)
                    true
                }
                else -> false
            }
        }
    }

    private fun adjustVolume(direction: Int) {
        if (isMuted && direction == AudioManager.ADJUST_RAISE) {
            isMuted = false
            val restoreVol = if (volumeBeforeMute > 0) volumeBeforeMute else 5
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restoreVol, AudioManager.FLAG_SHOW_UI)
            updateMuteStateVisual()
            return
        }

        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun toggleMute() {
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (isMuted) {
            val restoreVol = if (volumeBeforeMute > 0) volumeBeforeMute else 5
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restoreVol, AudioManager.FLAG_SHOW_UI)
            isMuted = false
        } else {
            volumeBeforeMute = currentVol
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
            isMuted = true
        }
        updateMuteStateVisual()
    }

    private fun updateMuteStateVisual() {
        val density = resources.displayMetrics.density
        if (isMuted) {
            val muteBg = createRippleSquircle(
                fillColor = Color.parseColor("#38141E"),
                strokeColor = Color.parseColor("#EF4444"),
                strokeWidthPx = (1.8f * density).toInt().coerceAtLeast(2),
                rippleColor = Color.parseColor("#661D2E"),
                radiusPx = currentButtonRadiusPx
            )
            btnMute.background = muteBg
        } else {
            val normalBg = createRippleSquircle(
                fillColor = Color.parseColor("#182232"),
                strokeColor = Color.parseColor("#2C3B4E"),
                strokeWidthPx = (1.2f * density).toInt().coerceAtLeast(1),
                rippleColor = Color.parseColor("#384F6E"),
                radiusPx = currentButtonRadiusPx
            )
            btnMute.background = normalBg
        }
        btnMute.setImageResource(R.drawable.ic_mute)
    }

    private fun createSquircleDrawable(
        fillColor: Int,
        strokeColor: Int,
        strokeWidthPx: Int,
        radiusPx: Float
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            cornerRadius = radiusPx
            if (strokeWidthPx > 0) {
                setStroke(strokeWidthPx, strokeColor)
            }
        }
    }

    private fun createRippleSquircle(
        fillColor: Int,
        strokeColor: Int,
        strokeWidthPx: Int,
        rippleColor: Int,
        radiusPx: Float
    ): RippleDrawable {
        val content = createSquircleDrawable(fillColor, strokeColor, strokeWidthPx, radiusPx)
        val mask = createSquircleDrawable(Color.WHITE, Color.TRANSPARENT, 0, radiusPx)
        return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        dockView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Ignore if already removed
            }
        }
        repeatHandler.removeCallbacksAndMessages(null)
    }
}
