package com.example.nthingdailer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager

import android.widget.Toast

import android.util.Log

class CallStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        
        if (action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("NothingDialer", "Boot completed, background detection active")
            return
        }

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) 
            ?: intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
        
        Log.d("NothingDialer", "Broadcast received: $action, state: $state, number: $incomingNumber")

        if (state == TelephonyManager.EXTRA_STATE_IDLE) {
            stopOverlayService(context)
        } else {
            triggerPopup(context, incomingNumber)
        }
    }

    private fun triggerPopup(context: Context, number: String?) {
        val prefs = context.getSharedPreferences("nthing_prefs", Context.MODE_PRIVATE)
        val isOverlayEnabled = prefs.getBoolean("is_overlay_enabled", true)
        
        if (!isOverlayEnabled) {
            Toast.makeText(context, "Popup Disabled in Settings", Toast.LENGTH_SHORT).show()
            return
        }

        if (Settings.canDrawOverlays(context)) {
            startOverlayService(context, number)
        } else {
            Toast.makeText(context, "Grant 'Display over other apps' to Nthing", Toast.LENGTH_LONG).show()
        }
    }

    private fun startOverlayService(context: Context, number: String?) {
        val serviceIntent = Intent(context, CallOverlayService::class.java).apply {
            putExtra("number", number ?: CallStateManager.lastCallNumber.value)
            val lastNumber = CallStateManager.lastCallNumber.value
            val lastName = CallStateManager.lastCallName.value
            if (number != null && lastNumber != null && number == lastNumber) {
                putExtra("name", lastName)
            } else {
                putExtra("name", null as String?)
            }
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("NothingDialer", "Failed to start overlay service", e)
            Toast.makeText(context, "Failed to start call popup: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopOverlayService(context: Context) {
        val serviceIntent = Intent(context, CallOverlayService::class.java)
        context.stopService(serviceIntent)
        CallStateManager.updateCallState(false)
    }
}
