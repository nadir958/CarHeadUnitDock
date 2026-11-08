package com.cardock.headunit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
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
import androidx.core.app.NotificationCompat

class CarDockOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager
    private lateinit var prefs: SharedPreferences
    private var dockView: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    private lateinit var dragHandle: ImageView
    private lateinit var btnBack: ImageButton
    private lateinit var btnHome: ImageButton
    private lateinit var btnMute: ImageButton
    private lateinit var btnVolDown: ImageButton
    private lateinit var btnVolUp: ImageButton

    private val repeatHandler = Handler(Looper.getMainLooper())
    private var isMuted = false
    private var volumeBeforeMute = 0

    // Variables pour le déplacement flottant
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
        val density = resources.displayMetrics.density
        val heightPx = (48 * density).toInt()

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Position par défaut : centré horizontalement, à 16dp du bas de l'écran
        val estimatedDockWidth = (280 * density).toInt()
        val defaultX = (screenWidth - estimatedDockWidth) / 2
        val defaultY = screenHeight - heightPx - (16 * density).toInt()

        val savedX = prefs.getInt(PREF_POS_X, defaultX)
        val savedY = prefs.getInt(PREF_POS_Y, defaultY)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX.coerceIn(0, screenWidth - 100)
            y = savedY.coerceIn(0, screenHeight - heightPx)
        }

        val inflater = LayoutInflater.from(this)
        dockView = inflater.inflate(R.layout.layout_car_dock_bar, null)

        initViews(dockView!!)
        setupDragListener()

        windowManager.addView(dockView, layoutParams)
    }

    private fun initViews(view: View) {
        dragHandle = view.findViewById(R.id.dragHandle)
        btnBack = view.findViewById(R.id.btnBack)
        btnHome = view.findViewById(R.id.btnHome)
        btnMute = view.findViewById(R.id.btnMute)
        btnVolDown = view.findViewById(R.id.btnVolDown)
        btnVolUp = view.findViewById(R.id.btnVolUp)

        // 1. Action RETOUR
        btnBack.setOnClickListener {
            CarNavAccessibilityService.goBack()
        }

        // 2. Action ACCUEIL (HOME)
        btnHome.setOnClickListener {
            val handled = CarNavAccessibilityService.goHome()
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

    private fun setupDragListener() {
        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()

                    layoutParams.x = initialX + deltaX
                    layoutParams.y = initialY + deltaY

                    if (dockView != null && dockView!!.isAttachedToWindow) {
                        windowManager.updateViewLayout(dockView, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Sauvegarder la nouvelle position pour la prochaine fois
                    prefs.edit()
                        .putInt(PREF_POS_X, layoutParams.x)
                        .putInt(PREF_POS_Y, layoutParams.y)
                        .apply()
                    true
                }
                else -> false
            }
        }
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

        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isHolding = true
                    adjustVolume(direction)
                    repeatHandler.postDelayed(runnable, 400)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isHolding = false
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
            updateMuteStateVisual()
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
