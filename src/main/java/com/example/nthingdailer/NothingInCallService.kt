package com.example.nthingdailer

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
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
    private val activeCalls = mutableListOf<Call>()

    companion object {
        private var currentCall: Call? = null
        private var instance: NothingInCallService? = null

        fun disconnectCurrentCall() {
            currentCall?.disconnect()
            currentCall = null
        }

        fun answerCurrentCall() {
            currentCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
        }
        
        fun mergeCalls() {
            val calls = instance?.activeCalls ?: return
            if (calls.size >= 2) {
                // In a real app, we'd find calls that can be conferenced
                val call1 = calls.find { it.state == Call.STATE_ACTIVE }
                val call2 = calls.find { it.state == Call.STATE_HOLDING }
                if (call1 != null && call2 != null) {
                    call1.conference(call2)
                }
            }
        }

        fun setSpeaker(enabled: Boolean) {
            instance?.setAudioRoute(if (enabled) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE)
        }

        fun setMuted(muted: Boolean) {
            instance?.setMuted(muted)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
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
                
                call?.let { activeCalls.remove(it) }
                checkSessionEnd()
            } else {
                // Update UI if the state changed for our tracked call
                if (currentCall == call) {
                    val isConference = call?.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true
                    val name = if (isConference) "CONFERENCE CALL" else null
                    CallStateManager.updateCallState(true, name, number, state)
                }
            }
        }
    }

    private fun checkSessionEnd() {
        if (activeCalls.isEmpty()) {
            currentCall = null
            CallStateManager.updateCallState(false)
        } else {
            // Switch currentCall to the next available call if the primary one ended
            if (currentCall == null || currentCall?.state == Call.STATE_DISCONNECTED) {
                currentCall = activeCalls.firstOrNull { it.state != Call.STATE_DISCONNECTED }
                if (currentCall != null) {
                    val nextNum = currentCall?.details?.handle?.schemeSpecificPart
                    val isConference = currentCall?.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true
                    val name = if (isConference) "CONFERENCE CALL" else null
                    CallStateManager.updateCallState(true, name, nextNum, currentCall?.state ?: Call.STATE_ACTIVE)
                }
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        activeCalls.add(call)
        
        val isConference = call.details.hasProperty(Call.Details.PROPERTY_CONFERENCE)
        
        // Primary call tracking
        if (currentCall == null || currentCall?.state == Call.STATE_DISCONNECTED) {
            currentCall = call
            wasAnswered = (call.state == Call.STATE_ACTIVE)
            isIncoming = (call.state == Call.STATE_RINGING)
        }
        
        call.registerCallback(callCallback)
        
        // Block check (only for individual incoming calls)
        val number = call.details.handle?.schemeSpecificPart
        if (number != null && !isConference && call.state == Call.STATE_RINGING) {
            val repository = DialerRepository(applicationContext)
            if (repository.isNumberBlocked(number)) {
                call.disconnect()
                NotificationHelper.showBlockedCallNotification(this, number)
                return 
            }
        }

        val displayName = if (isConference) "CONFERENCE CALL" else null
        CallStateManager.updateCallState(true, displayName, number, call.state)
        
        if (number != null && !isConference) {
            lookupContact(number, call.state)
        }
        
        // Bring app to foreground
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
            if (cleanNum.length < 3) return@launch
            
            val match = contacts.find { 
                val cNum = it.number.replace("\\D".toRegex(), "")
                cNum.endsWith(cleanNum) || cleanNum.endsWith(cNum)
            }
            if (match != null) {
                withContext(Dispatchers.Main) {
                    if (currentCall?.details?.handle?.schemeSpecificPart == number) {
                        CallStateManager.updateCallState(true, match.name, number, state)
                    }
                }
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        activeCalls.remove(call)
        checkSessionEnd()
    }
}
