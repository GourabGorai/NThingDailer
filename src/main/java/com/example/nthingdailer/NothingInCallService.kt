package com.example.nthingdailer

import android.telecom.Call
import android.telecom.InCallService

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
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call?, state: Int) {
            super.onStateChanged(call, state)
            if (state == Call.STATE_DISCONNECTED) {
                CallStateManager.updateCallState(false)
                currentCall = null
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall = call
        call.registerCallback(callCallback)
        
        // Extract number if available
        val number = call.details.handle?.schemeSpecificPart
        CallStateManager.updateCallState(true, null, number)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        if (currentCall == call) {
            currentCall = null
            CallStateManager.updateCallState(false)
        }
    }
}
