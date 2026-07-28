package com.example.nthingdailer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class CallStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    // Incoming call
                    startOverlayService(context, incomingNumber)
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    // Call started (incoming answered or outgoing started)
                    startOverlayService(context, incomingNumber)
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    // Call ended
                    stopOverlayService(context)
                }
            }
        }
    }

    private fun startOverlayService(context: Context, number: String?) {
        val serviceIntent = Intent(context, CallOverlayService::class.java).apply {
            putExtra("number", number ?: CallStateManager.lastCallNumber.value)
            putExtra("name", CallStateManager.lastCallName.value)
        }
        context.startService(serviceIntent)
    }

    private fun stopOverlayService(context: Context) {
        val serviceIntent = Intent(context, CallOverlayService::class.java)
        context.stopService(serviceIntent)
        CallStateManager.updateCallState(false)
    }
}
