package com.example.nthingdailer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.telephony.PhoneNumberUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.example.nthingdailer.model.DialerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CallOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val CHANNEL_ID = "call_overlay_channel"
    private val NOTIFICATION_ID = 1001

    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f

    private var isRecording: Boolean = false
    private var currentNumber: String? = null
    private var callStartTime: Long = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val number = intent?.getStringExtra("number")
        val name = intent?.getStringExtra("name")
        
        currentNumber = number
        callStartTime = System.currentTimeMillis()
        
        // Show foreground notification immediately to satisfy system
        val notification = createNotification(name ?: number ?: "Active Call")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (Settings.canDrawOverlays(this)) {
            showOverlay(number, name)
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Call Assistant"
            val descriptionText = "Shows call information on top of other apps"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nothing Call Assistant")
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun showOverlay(number: String?, name: String?) {
        if (overlayView != null) {
            updateOverlayText(number, name)
            return
        }

        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            
            val container = object : LinearLayout(this) {
                override fun performClick(): Boolean {
                    return super.performClick()
                }
            }.apply {
                orientation = LinearLayout.VERTICAL
                setPadding(60, 30, 60, 30)
                val bg = GradientDrawable().apply {
                    setColor(0xFF000000.toInt())
                    cornerRadius = 80f
                    setStroke(4, 0xFFFFFFFF.toInt())
                }
                background = bg
                elevation = 20f
            }

            val titleText = TextView(this).apply {
                id = android.R.id.text1
                text = "CALL ACTIVE"
                setTextColor(0xFFE5272C.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                letterSpacing = 0.2f
            }

            val infoText = TextView(this).apply {
                id = android.R.id.text2
                setTextColor(0xFFFFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setPadding(0, 10, 0, 0)
            }

            val numberText = TextView(this).apply {
                id = android.R.id.summary
                setTextColor(0xFFA1A1AA.toInt()) // Nothing Light Gray
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setPadding(0, 4, 0, 0)
            }

            val recordBtn = TextView(this).apply {
                text = "RECORD"
                setTextColor(0xFFFFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setPadding(30, 12, 30, 12)
                val btnBg = GradientDrawable().apply {
                    setColor(0x15FFFFFF)
                    cornerRadius = 24f
                    setStroke(1, 0x30FFFFFF)
                }
                background = btnBg
                
                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 30
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                this.layoutParams = layoutParams
                
                setOnClickListener {
                    isRecording = !isRecording
                    if (isRecording) {
                        text = "RECORDING..."
                        setTextColor(0xFFE5272C.toInt()) // Red
                        (background as GradientDrawable).setStroke(2, 0xFFE5272C.toInt())
                    } else {
                        text = "RECORD"
                        setTextColor(0xFFFFFFFF.toInt())
                        (background as GradientDrawable).setStroke(1, 0x30FFFFFF)
                    }
                }
            }

            container.addView(titleText)
            container.addView(infoText)
            container.addView(numberText)
            container.addView(recordBtn)
            overlayView = container

            updateOverlayText(number, name)

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                val prefs = getSharedPreferences("nthing_prefs", MODE_PRIVATE)
                x = prefs.getInt("popup_x_offset", 100)
                y = prefs.getInt("popup_y_offset", 150)
            }

            container.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        // Don't consume yet to allow button clicks
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // Only start moving if we've dragged a bit
                        val diffX = Math.abs(event.rawX - initialTouchX)
                        val diffY = Math.abs(event.rawY - initialTouchY)
                        if (diffX > 10 || diffY > 10) {
                            params.x = initialX + (event.rawX - initialTouchX).toInt()
                            params.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager?.updateViewLayout(overlayView, params)
                            true
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        val diffX = Math.abs(event.rawX - initialTouchX)
                        val diffY = Math.abs(event.rawY - initialTouchY)
                        if (diffX > 10 || diffY > 10) {
                            val prefs = getSharedPreferences("nthing_prefs", MODE_PRIVATE)
                            prefs.edit {
                                putInt("popup_x_offset", params.x)
                                putInt("popup_y_offset", params.y)
                            }
                            true
                        } else {
                            v.performClick()
                            false
                        }
                    }
                    else -> false
                }
            }

            windowManager?.addView(overlayView, params)
            CallStateManager.updateCallState(true, name, number)

            if ((name == null || name.isBlank()) && number != null) {
                lookupContact(number)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateOverlayText(number: String?, name: String?) {
        val infoText = overlayView?.findViewById<TextView>(android.R.id.text2)
        val numText = overlayView?.findViewById<TextView>(android.R.id.summary)
        
        if (name != null && name.isNotBlank() && !name.equals("Unknown", ignoreCase = true)) {
            infoText?.text = name.uppercase()
            infoText?.visibility = View.VISIBLE
            numText?.text = number ?: ""
            numText?.visibility = View.VISIBLE
        } else {
            // No name, show only number in the main slot
            infoText?.text = number ?: ""
            infoText?.visibility = View.VISIBLE
            numText?.visibility = View.GONE
        }
    }

    private fun lookupContact(number: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val repository = DialerRepository(applicationContext)
            
            // Try to find contact in repository
            var foundName: String? = null
            
            // Initial attempt
            val contacts = repository.fetchContacts()
            val match = contacts.find { it.number.replace("\\D".toRegex(), "").contains(number.replace("\\D".toRegex(), "")) }
            
            if (match != null) {
                foundName = match.name
            } else {
                // Deeper retry loop for background sync delays
                for (i in 1..3) {
                    delay(2000)
                    val retryContacts = repository.fetchContacts()
                    val retryMatch = retryContacts.find { it.number.replace("\\D".toRegex(), "").contains(number.replace("\\D".toRegex(), "")) }
                    if (retryMatch != null) {
                        foundName = retryMatch.name
                        break
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (foundName != null) {
                    updateOverlayText(number, foundName.uppercase())
                    CallStateManager.updateCallState(true, foundName, number)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        if (isRecording && currentNumber != null) {
            val prefs = getSharedPreferences("nthing_prefs", MODE_PRIVATE)
            // Use number + approx time as key (CallLog.DATE is a timestamp)
            // Note: System call log date might be slightly off from our System.currentTimeMillis()
            // but we can store it and look for matches within a range or just store it.
            val recordingId = "rec_${currentNumber}_${callStartTime}"
            prefs.edit {
                putString(recordingId, "internal_storage/recordings/call_rec.mp3")
                // Also store a list of all recorded IDs to easily fetch them
                val existing = prefs.getStringSet("all_recordings", mutableSetOf()) ?: mutableSetOf()
                val updated = existing.toMutableSet().apply { add(recordingId) }
                putStringSet("all_recordings", updated)
            }
        }

        if (overlayView != null) {
            windowManager?.removeView(overlayView)
            overlayView = null
        }
    }
}
