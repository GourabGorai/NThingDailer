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
import android.widget.Toast
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
        // Start foreground as soon as service is created
        val notification = createNotification("Call Assistant Active")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val number = intent?.getStringExtra("number")
        val name = intent?.getStringExtra("name")
        
        currentNumber = number
        callStartTime = System.currentTimeMillis()
        
        // Update notification with caller info
        val notification = createNotification(name ?: number ?: "Active Call")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)

        if (Settings.canDrawOverlays(this)) {
            showOverlay(number, name)
            if (number == null) fetchLatestCallLogNumber()
        }
        return START_NOT_STICKY
    }

    private fun fetchLatestCallLogNumber() {
        CoroutineScope(Dispatchers.IO).launch {
            val repository = DialerRepository(applicationContext)
            // Try multiple times as it takes time for system to log the call
            for (i in 1..5) {
                delay(1000L * i) 
                val logs = repository.fetchCallLogs()
                if (logs.isNotEmpty()) {
                    val latest = logs.first()
                    // Check if it's likely the current call (recent)
                    withContext(Dispatchers.Main) {
                        currentNumber = latest.number
                        updateOverlayText(latest.number, latest.name)
                        lookupContact(latest.number)
                    }
                    break
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Call Assistant", NotificationManager.IMPORTANCE_LOW)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
                override fun performClick(): Boolean { return super.performClick() }
            }.apply {
                orientation = LinearLayout.VERTICAL
                setPadding(80, 50, 80, 50)
                background = GradientDrawable().apply {
                    setColor(0xFF000000.toInt())
                    cornerRadius = 80f
                    setStroke(2, 0xFFFFFFFF.toInt())
                }
                elevation = 40f
                gravity = Gravity.CENTER_HORIZONTAL
                minimumWidth = 500 // Ensure enough width for full numbers
            }

            val titleText = TextView(this).apply {
                id = View.generateViewId()
                text = "CALL"
                setTextColor(0xFFE5272C.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                letterSpacing = 0.15f
            }

            val infoText = TextView(this).apply {
                id = android.R.id.text2
                setTextColor(0xFFFFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setPadding(0, 8, 0, 4)
            }

            val numberText = TextView(this).apply {
                id = android.R.id.summary
                setTextColor(0xFFA1A1AA.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 12)
                visibility = View.GONE
            }

            val recordBtn = TextView(this).apply {
                text = "RECORD"
                setTextColor(0xFFFFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setPadding(30, 12, 30, 12)
                background = GradientDrawable().apply {
                    setColor(0xFF1E1E22.toInt())
                    cornerRadius = 30f
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 10 }
                
                setOnClickListener {
                    isRecording = !isRecording
                    if (isRecording) {
                        text = "RECORDING"
                        setTextColor(0xFFE5272C.toInt())
                        (background as GradientDrawable).setStroke(1, 0xFFE5272C.toInt())
                    } else {
                        text = "RECORD"
                        setTextColor(0xFFFFFFFF.toInt())
                        (background as GradientDrawable).setStroke(0, 0)
                    }
                }
            }

            container.addView(titleText)
            container.addView(infoText)
            container.addView(numberText)
            container.addView(recordBtn)
            overlayView = container

            updateOverlayText(number, name)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                val prefs = getSharedPreferences("nthing_prefs", MODE_PRIVATE)
                x = 0
                y = prefs.getInt("popup_y_offset", 150)
            }

            container.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(overlayView, params)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val diffX = Math.abs(event.rawX - initialTouchX)
                        val diffY = Math.abs(event.rawY - initialTouchY)
                        if (diffX < 10 && diffY < 10) {
                            val location = IntArray(2)
                            recordBtn.getLocationOnScreen(location)
                            if (event.rawX >= location[0] && event.rawX <= location[0] + recordBtn.width &&
                                event.rawY >= location[1] && event.rawY <= location[1] + recordBtn.height) {
                                recordBtn.performClick()
                            }
                        } else {
                            getSharedPreferences("nthing_prefs", MODE_PRIVATE).edit {
                                putInt("popup_x_offset", params.x)
                                putInt("popup_y_offset", params.y)
                            }
                        }
                        true
                    }
                    else -> false
                }
            }

            windowManager?.addView(overlayView, params)
            CallStateManager.updateCallState(true, name, number)
            if ((name == null || name.isBlank()) && number != null) lookupContact(number)
            
            Toast.makeText(this, "Call Popup Ready", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Overlay Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateOverlayText(number: String?, name: String?) {
        val infoText = overlayView?.findViewById<TextView>(android.R.id.text2)
        val numText = overlayView?.findViewById<TextView>(android.R.id.summary)
        if (name != null && name.isNotBlank() && !name.equals("Unknown", ignoreCase = true)) {
            infoText?.text = name.uppercase()
            numText?.text = number ?: ""
            numText?.visibility = View.VISIBLE
        } else {
            infoText?.text = number ?: ""
            numText?.visibility = View.GONE
        }
    }

    private fun lookupContact(number: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val repository = DialerRepository(applicationContext)
            var foundName: String? = null
            val contacts = repository.fetchContacts()
            val match = contacts.find { it.number.replace("\\D".toRegex(), "").contains(number.replace("\\D".toRegex(), "")) }
            if (match != null) foundName = match.name
            else {
                for (i in 1..3) {
                    delay(2000)
                    val retryMatch = repository.fetchContacts().find { it.number.replace("\\D".toRegex(), "").contains(number.replace("\\D".toRegex(), "")) }
                    if (retryMatch != null) { foundName = retryMatch.name; break }
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
            val recordingId = "rec_${currentNumber}_${callStartTime}"
            prefs.edit {
                putString(recordingId, "internal_storage/recordings/call_rec.mp3")
                val updated = (prefs.getStringSet("all_recordings", mutableSetOf()) ?: mutableSetOf()).toMutableSet().apply { add(recordingId) }
                putStringSet("all_recordings", updated)
            }
        }
        if (overlayView != null) { windowManager?.removeView(overlayView); overlayView = null }
    }
}
