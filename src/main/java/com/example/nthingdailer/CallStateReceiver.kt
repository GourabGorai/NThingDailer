package com.example.nthingdailer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.TelephonyManager

import android.widget.Toast

class CallStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            // Skip floating popup if we are the default dialer
            if (isDefaultDialer(context)) return

            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING, TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    val prefs = context.getSharedPreferences("nthing_prefs", Context.MODE_PRIVATE)
                    val isOverlayEnabled = prefs.getBoolean("is_overlay_enabled", true)
                    
                    if (isOverlayEnabled && Settings.canDrawOverlays(context)) {
                        startOverlayService(context, incomingNumber)
                    } else if (isOverlayEnabled) {
                        // Diagnostic toast if permission is missing but feature enabled
                        Toast.makeText(context, "Nothing Dialer: Grant 'Display over other apps' to see call popup", Toast.LENGTH_LONG).show()
                    }
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    stopOverlayService(context)
                }
            }
        }
    }

    private fun isDefaultDialer(context: Context): Boolean {
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecomManager.defaultDialerPackage == context.packageName
        } catch (e: Exception) {
            false
        }
    }

    private fun startOverlayService(context: Context, number: String?) {
        val serviceIntent = Intent(context, CallOverlayService::class.java).apply {
            putExtra("number", number ?: CallStateManager.lastCallNumber.value)
            // Only pass name if the number matches the last call info
            val lastNumber = CallStateManager.lastCallNumber.value
            val lastName = CallStateManager.lastCallName.value
            if (number != null && lastNumber != null && number == lastNumber) {
                putExtra("name", lastName)
            } else {
                putExtra("name", null as String?)
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    private fun stopOverlayService(context: Context) {
        val serviceIntent = Intent(context, CallOverlayService::class.java)
        context.stopService(serviceIntent)
        CallStateManager.updateCallState(false)
    }
}
