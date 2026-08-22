package com.example.nthingdailer

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile

/**
 * Required service for an app to be considered a valid Dialer by the Android system.
 * This service receives callbacks about call state changes when the app is the default dialer.
 */
class NothingInCallService : InCallService() {

    companion object {
        private var currentCall: Call? = null

        fun disconnectCurrentCall() {
            currentCall?.disconnect()
            currentCall = null
        }

        fun answerCurrentCall() {
            currentCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
        }
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call?, state: Int) {
            super.onStateChanged(call, state)
            val number = call?.details?.handle?.schemeSpecificPart
            if (state == Call.STATE_DISCONNECTED) {
                CallStateManager.updateCallState(false)
                currentCall = null
                stopOverlay()
            } else {
                CallStateManager.updateCallState(true, null, number, state)
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall = call
        call.registerCallback(callCallback)
        
        // Extract number if available
        val number = call.details.handle?.schemeSpecificPart
        CallStateManager.updateCallState(true, null, number, call.state)
        
        // Bring app to foreground if we are default dialer
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)

        // Show overlay in case user backgrounds the app
        startOverlay(number)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        if (currentCall == call) {
            currentCall = null
            CallStateManager.updateCallState(false)
            stopOverlay()
        }
    }

    private fun startOverlay(number: String?) {
        val prefs = getSharedPreferences("nthing_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("is_overlay_enabled", true)) return

        val intent = Intent(this, CallOverlayService::class.java).apply {
            putExtra("number", number)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopOverlay() {
        val intent = Intent(this, CallOverlayService::class.java)
        stopService(intent)
    }
}
