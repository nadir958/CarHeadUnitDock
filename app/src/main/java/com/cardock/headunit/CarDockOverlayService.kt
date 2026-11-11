package com.cardock.headunit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
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
        const val SIZE_COMPACT = "compact" // 44dp
        const val SIZE_MEDIUM = "medium"   // 56dp
        const val SIZE_LARGE = "large"     // 68dp
        const val SIZE_XLARGE = "xlarge"   // 80dp

        const val ACTION_UPDATE_CONFIG = "com.cardock.headunit.ACTION_UPDATE_CONFIG"
        var isServiceRunning = false
    }

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

        // Position par défaut
        val defaultX = (screenWidth - (300 * density).toInt()) / 2
        val defaultY = screenHeight - (76 * density).toInt() - (16 * density).toInt()

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

        // Assurer un style homogène sombre pour tous les boutons
        btnBack.setBackgroundResource(R.drawable.bg_button_night)
        btnHome.setBackgroundResource(R.drawable.bg_button_night)
        btnMute.setBackgroundResource(R.drawable.bg_button_night)
        btnVolDown.setBackgroundResource(R.drawable.bg_button_night)
        btnVolUp.setBackgroundResource(R.drawable.bg_button_night)

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
     * Applique dynamiquement l'orientation (Horizontal / Vertical)
     * et la taille des boutons (Compact 44dp, Medium 56dp, Large 68dp, XLarge 80dp).
     */
    private fun applyDockConfiguration() {
        val density = resources.displayMetrics.density
        val orientation = prefs.getString(PREF_DOCK_ORIENTATION, ORIENT_HORIZONTAL) ?: ORIENT_HORIZONTAL
        val sizePref = prefs.getString(PREF_DOCK_SIZE, SIZE_LARGE) ?: SIZE_LARGE

        val btnSizeDp = when (sizePref) {
            SIZE_COMPACT -> 44
            SIZE_MEDIUM -> 56
            SIZE_XLARGE -> 80
            else -> 68
        }
        val btnSizePx = (btnSizeDp * density).toInt()

        // Padding interne des boutons pour mise à l'échelle harmonieuse des icônes
        val iconPaddingPx = when (sizePref) {
            SIZE_COMPACT -> (9 * density).toInt()
            SIZE_MEDIUM -> (12 * density).toInt()
            SIZE_XLARGE -> (17 * density).toInt()
            else -> (14 * density).toInt()
        }

        val spacingPx = (6 * density).toInt()
        val padLateralPx = (8 * density).toInt()
        val padCrossPx = (5 * density).toInt()

        val isVertical = (orientation == ORIENT_VERTICAL)

        // 1. Orientation du conteneur
        container.orientation = if (isVertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL

        // 2. Padding du conteneur selon l'orientation
        if (isVertical) {
            container.setPadding(padCrossPx, padLateralPx, padCrossPx, padLateralPx)
        } else {
            container.setPadding(padLateralPx, padCrossPx, padLateralPx, padCrossPx)
        }

        // 3. Poignée de déplacement (dragHandle : large zone tactile)
        val handleSizePx = (36 * density).toInt()
        if (isVertical) {
            val dragLp = LinearLayout.LayoutParams(btnSizePx, handleSizePx).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (4 * density).toInt()
            }
            dragHandle.layoutParams = dragLp
            dragHandle.rotation = 90f // Barres orientées horizontalement pour drag vertical
        } else {
            val dragLp = LinearLayout.LayoutParams(handleSizePx, btnSizePx).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginEnd = (4 * density).toInt()
            }
            dragHandle.layoutParams = dragLp
            dragHandle.rotation = 0f
        }

        // 4. Liste des boutons interactifs
        val buttons = listOf(btnBack, btnHome, btnMute, btnVolDown, btnVolUp)
        for (btn in buttons) {
            btn.setPadding(iconPaddingPx, iconPaddingPx, iconPaddingPx, iconPaddingPx)
            btn.scaleType = ImageView.ScaleType.FIT_CENTER
        }

        // Appliquer les marges et dimensions aux boutons
        if (isVertical) {
            // Disposition en COLONNE
            btnBack.layoutParams = LinearLayout.LayoutParams(btnSizePx, btnSizePx).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = spacingPx
            }
            btnHome.layoutParams = LinearLayout.LayoutParams(btnSizePx, btnSizePx).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = spacingPx
            }

            // Séparateur horizontal en mode vertical
            dockSeparator.layoutParams = LinearLayout.LayoutParams((btnSizePx * 0.55f).toInt(), (1.5f * density).toInt()).apply {
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
            // Disposition en LIGNE (Horizontal)
            btnBack.layoutParams = LinearLayout.LayoutParams(btnSizePx, btnSizePx).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = (4 * density).toInt()
            }
            btnHome.layoutParams = LinearLayout.LayoutParams(btnSizePx, btnSizePx).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = spacingPx
            }

            // Séparateur vertical en mode horizontal
            dockSeparator.layoutParams = LinearLayout.LayoutParams((1.5f * density).toInt(), (btnSizePx * 0.55f).toInt()).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = spacingPx
                marginEnd = spacingPx
            }

            btnMute.layoutParams = LinearLayout.LayoutParams(btnSizePx, btnSizePx).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            btnVolDown.layoutParams = LinearLayout.LayoutParams(btnSizePx, btnSizePx).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = spacingPx
            }
            btnVolUp.layoutParams = LinearLayout.LayoutParams(btnSizePx, btnSizePx).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = spacingPx
            }
        }

        // 5. Fond capsule flottant dynamique (arrondi parfait quel que soit le diamètre des boutons)
        val crossAxisSize = btnSizePx + (padCrossPx * 2)
        val capsuleRadius = crossAxisSize / 2f
        val capsuleBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#F2090D16"))
            cornerRadius = capsuleRadius
            setStroke((1.5f * density).toInt(), Color.parseColor("#334155"))
        }
        container.background = capsuleBg

        // 6. Mise à jour de la disposition WindowManager
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val estimatedW = if (isVertical) (btnSizePx + padCrossPx * 2) else ((btnSizeDp * 5 + 90) * density).toInt()
        val estimatedH = if (isVertical) ((btnSizeDp * 5 + 90) * density).toInt() else (btnSizePx + padCrossPx * 2)

        // Réajuster pour garantir que la barre reste entièrement visible dans l'écran
        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParams.x = layoutParams.x.coerceIn(0, (screenWidth - estimatedW).coerceAtLeast(0))
        layoutParams.y = layoutParams.y.coerceIn(0, (screenHeight - estimatedH).coerceAtLeast(0))

        if (dockView != null && dockView!!.isAttachedToWindow) {
            windowManager.updateViewLayout(dockView, layoutParams)
        }
    }

    private fun setupDragListener() {
        val onTouchDrag = View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    android.util.Log.d("CarDockOverlayService", "DRAG DOWN initX=$initialX, initY=$initialY, rawX=${event.rawX}, rawY=${event.rawY}")
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()

                    val displayMetrics = resources.displayMetrics
                    val screenWidth = displayMetrics.widthPixels
                    val screenHeight = displayMetrics.heightPixels
                    val viewW = if (dockView?.width ?: 0 > 0) dockView!!.width else 120
                    val viewH = if (dockView?.height ?: 0 > 0) dockView!!.height else 120

                    layoutParams.x = (initialX + deltaX).coerceIn(0, (screenWidth - viewW).coerceAtLeast(0))
                    layoutParams.y = (initialY + deltaY).coerceIn(0, (screenHeight - viewH).coerceAtLeast(0))
                    android.util.Log.d("CarDockOverlayService", "DRAG MOVE to x=${layoutParams.x}, y=${layoutParams.y}")

                    if (dockView != null && dockView!!.isAttachedToWindow) {
                        windowManager.updateViewLayout(dockView, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    android.util.Log.d("CarDockOverlayService", "DRAG UP saved pos (${layoutParams.x}, ${layoutParams.y})")
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
        android.util.Log.d("CarDockOverlayService", "adjustVolume dir=$direction, isMuted=$isMuted")
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
        if (isMuted) {
            btnMute.setBackgroundResource(R.drawable.bg_button_mute_active)
            btnMute.setImageResource(R.drawable.ic_mute)
        } else {
            btnMute.setBackgroundResource(R.drawable.bg_button_night)
            btnMute.setImageResource(R.drawable.ic_mute)
        }
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
