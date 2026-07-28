package com.example.nthingdailer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneNumberUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.example.nthingdailer.model.DialerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CallOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val number = intent?.getStringExtra("number")
        val name = intent?.getStringExtra("name")
        showOverlay(number, name)
        return START_NOT_STICKY
    }

    private fun showOverlay(number: String?, name: String?) {
        if (overlayView != null) {
            // Update existing view if it exists
            updateOverlayText(number, name)
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // Custom Nothing OS Style Overlay - High Visibility
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 30, 60, 30)
            val bg = GradientDrawable().apply {
                setColor(0xFF000000.toInt()) // Opaque Black
                cornerRadius = 80f
                setStroke(4, 0xFFFFFFFF.toInt()) // Thick White border
            }
            background = bg
            elevation = 20f
        }

        val titleText = TextView(this).apply {
            id = android.R.id.text1
            text = "CALL ACTIVE"
            setTextColor(0xFFE5272C.toInt()) // Nothing Red
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            letterSpacing = 0.2f
        }

        val infoText = TextView(this).apply {
            id = android.R.id.text2
            text = name ?: number ?: "Unknown Number"
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 0)
        }

        container.addView(titleText)
        container.addView(infoText)
        overlayView = container

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
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 150 // Lower down for visibility
        }

        windowManager?.addView(overlayView, params)
        
        CallStateManager.updateCallState(true, name, number)

        // If name is unknown, try a deeper lookup
        if (name == null && number != null) {
            lookupContact(number)
        }
    }

    private fun updateOverlayText(number: String?, name: String?) {
        val infoText = overlayView?.findViewById<TextView>(android.R.id.text2)
        infoText?.text = name ?: number ?: "Unknown Number"
    }

    private fun lookupContact(number: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val repository = DialerRepository(applicationContext)
            // Initial lookup
            var contact = repository.fetchContacts().find { 
                PhoneNumberUtils.compare(applicationContext, it.number, number) 
            }
            
            // If not found, it might be because the database hasn't updated yet.
            // Retry a few times with a delay.
            if (contact == null) {
                for (i in 1..3) {
                    delay(2000)
                    contact = repository.fetchContacts().find { 
                        PhoneNumberUtils.compare(applicationContext, it.number, number) 
                    }
                    if (contact != null) break
                }
            }

            withContext(Dispatchers.Main) {
                if (contact != null) {
                    updateOverlayText(number, contact.name.uppercase())
                    CallStateManager.updateCallState(true, contact.name, number)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (overlayView != null) {
            windowManager?.removeView(overlayView)
            overlayView = null
        }
    }
}
