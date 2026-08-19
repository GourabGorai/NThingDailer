package com.example.nthingdailer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object CallStateManager {
    private val _isCallActive = MutableStateFlow(false)
    val isCallActive = _isCallActive.asStateFlow()

    private val _lastCallName = MutableStateFlow<String?>(null)
    val lastCallName = _lastCallName.asStateFlow()

    private val _lastCallNumber = MutableStateFlow<String?>(null)
    val lastCallNumber = _lastCallNumber.asStateFlow()

    private val _lastCallStartTime = MutableStateFlow(0L)
    val lastCallStartTime = _lastCallStartTime.asStateFlow()

    private val _triggerAcknowledgement = MutableStateFlow(false)
    val triggerAcknowledgement = _triggerAcknowledgement.asStateFlow()

    fun updateCallState(active: Boolean, name: String? = null, number: String? = null) {
        if (!active && _isCallActive.value) {
            _triggerAcknowledgement.value = true
        }
        _isCallActive.value = active
        if (active) {
            name?.let { _lastCallName.value = it }
            number?.let { _lastCallNumber.value = it }
            _lastCallStartTime.value = System.currentTimeMillis()
        }
    }

    fun clearAcknowledgement() {
        _triggerAcknowledgement.value = false
    }
}
