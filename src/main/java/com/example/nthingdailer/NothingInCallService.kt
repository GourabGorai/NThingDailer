package com.example.nthingdailer

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import com.example.nthingdailer.model.DialerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Required service for an app to be considered a valid Dialer by the Android system.
 * This service receives callbacks about call state changes when the app is the default dialer.
 */
class NothingInCallService : InCallService() {

    private var wasAnswered = false
    private var isIncoming = false

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
            if (state == Call.STATE_ACTIVE) {
                wasAnswered = true
            }
            
            val number = call?.details?.handle?.schemeSpecificPart
            if (state == Call.STATE_DISCONNECTED) {
                if (isIncoming && !wasAnswered) {
                    // This was a missed call
                    NotificationHelper.showMissedCallNotification(
                        applicationContext,
                        number ?: "Unknown",
                        CallStateManager.lastCallName.value
                    )
                }
                CallStateManager.updateCallState(false)
                currentCall = null
            } else {
                // Pass null for name; the manager will keep the existing name if one was found
                CallStateManager.updateCallState(true, null, number, state)
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall = call
        wasAnswered = false
        isIncoming = call.state == Call.STATE_RINGING
        call.registerCallback(callCallback)
        
        // Extract number if available
        val number = call.details.handle?.schemeSpecificPart
        
        if (number != null) {
            val repository = DialerRepository(applicationContext)
            if (repository.isNumberBlocked(number)) {
                // BLOCK THE CALL
                call.disconnect()
                NotificationHelper.showBlockedCallNotification(this, number)
                return 
            }
        }

        CallStateManager.updateCallState(true, null, number, call.state)
        
        if (number != null) {
            lookupContact(number, call.state)
        }
        
        // Bring app to foreground if we are default dialer
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("from_call_service", true)
        }
        startActivity(intent)
    }

    private fun lookupContact(number: String, state: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val repository = DialerRepository(applicationContext)
            val contacts = repository.fetchContacts()
            val cleanNum = number.replace("\\D".toRegex(), "")
            if (cleanNum.length < 3) return@launch // Avoid matching very short sequences
            
            val match = contacts.find { 
                val cNum = it.number.replace("\\D".toRegex(), "")
                // Match if one contains the other, but prioritize exact suffix match for reliability
                cNum.endsWith(cleanNum) || cleanNum.endsWith(cNum) || 
                (cleanNum.length >= 7 && cNum.contains(cleanNum))
            }
            if (match != null) {
                withContext(Dispatchers.Main) {
                    CallStateManager.updateCallState(true, match.name, number, state)
                }
            }
        }
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
