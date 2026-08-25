package com.example.nthingdailer

import android.telecom.Call
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object CallStateManager {
    private val _isCallActive = MutableStateFlow(false)
    val isCallActive = _isCallActive.asStateFlow()

    private val _callState = MutableStateFlow(Call.STATE_DISCONNECTED)
    val callState = _callState.asStateFlow()

    private val _lastCallName = MutableStateFlow<String?>(null)
    val lastCallName = _lastCallName.asStateFlow()

    private val _lastCallNumber = MutableStateFlow<String?>(null)
    val lastCallNumber = _lastCallNumber.asStateFlow()

    private val _lastCallStartTime = MutableStateFlow(0L)
    val lastCallStartTime = _lastCallStartTime.asStateFlow()

    private val _triggerAcknowledgement = MutableStateFlow(false)
    val triggerAcknowledgement = _triggerAcknowledgement.asStateFlow()

    fun updateCallState(active: Boolean, name: String? = null, number: String? = null, state: Int = Call.STATE_ACTIVE) {
        val wasActive = _isCallActive.value
        if (!active && wasActive) {
            _triggerAcknowledgement.value = true
        }
        _isCallActive.value = active
        _callState.value = if (active) state else Call.STATE_DISCONNECTED
        
        if (active) {
            if (!wasActive) {
                // New call starting: reset everything
                _lastCallName.value = name
                _lastCallNumber.value = number
                _lastCallStartTime.value = System.currentTimeMillis()
            } else {
                // Existing call update: only update if new info is provided
                name?.let { _lastCallName.value = it }
                number?.let { _lastCallNumber.value = it }
            }
        }
    }

    fun clearAcknowledgement() {
        _triggerAcknowledgement.value = false
        _lastCallName.value = null
        _lastCallNumber.value = null
    }
}
