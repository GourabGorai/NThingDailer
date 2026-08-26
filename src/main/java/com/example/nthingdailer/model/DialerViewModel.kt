package com.example.nthingdailer.model

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DialerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DialerRepository(application)

    private val _contacts = MutableStateFlow<List<ContactItem>>(emptyList())
    val contacts: StateFlow<List<ContactItem>> = _contacts.asStateFlow()

    private val _recents = MutableStateFlow<List<RecentItem>>(emptyList())
    val recents: StateFlow<List<RecentItem>> = _recents.asStateFlow()

    private val _dialNumber = MutableStateFlow("")
    val dialNumber: StateFlow<String> = _dialNumber.asStateFlow()

    private val _t9Matches = MutableStateFlow<List<ContactItem>>(emptyList())
    val t9Matches: StateFlow<List<ContactItem>> = _t9Matches.asStateFlow()

    private val _isExternalCallActive = MutableStateFlow(false)
    val isExternalCallActive: StateFlow<Boolean> = _isExternalCallActive.asStateFlow()

    private val _showAcknowledgement = MutableStateFlow(false)
    val showAcknowledgement: StateFlow<Boolean> = _showAcknowledgement.asStateFlow()

    private val _lastCallInfo = MutableStateFlow<Pair<String, String>?>(null) // Name, Number
    val lastCallInfo: StateFlow<Pair<String, String>?> = _lastCallInfo.asStateFlow()

    private val callLogObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            refreshData()
        }
    }

    init {
        application.contentResolver.registerContentObserver(
            CallLog.Calls.CONTENT_URI,
            true,
            callLogObserver
        )
        refreshData()
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().contentResolver.unregisterContentObserver(callLogObserver)
    }

    fun setCallState(active: Boolean, name: String? = null, number: String? = null) {
        if (!active && _isExternalCallActive.value) {
            // Call just ended
            _showAcknowledgement.value = true
        }
        _isExternalCallActive.value = active
        if (active && name != null && number != null) {
            _lastCallInfo.value = name to number
        }
    }

    fun dismissAcknowledgement() {
        _showAcknowledgement.value = false
        _lastCallInfo.value = null
    }

    private var refreshJob: Job? = null
    fun refreshData() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            // Add a small delay to debounce multiple rapid changes (common with CallLog updates)
            delay(300)
            _contacts.value = repository.fetchContacts()
            _recents.value = repository.fetchCallLogs()
        }
    }

    fun onDialNumberChanged(newNumber: String) {
        _dialNumber.value = newNumber
        updateT9Matches(newNumber)
    }

    fun clearDialer() {
        _dialNumber.value = ""
        _t9Matches.value = emptyList()
    }

    private fun updateT9Matches(query: String) {
        if (query.isEmpty()) {
            _t9Matches.value = emptyList()
            return
        }

        val matches = _contacts.value.filter { contact ->
            contact.number.replace("\\D".toRegex(), "").contains(query) ||
                    nameMatchesT9(contact.name, query)
        }
        _t9Matches.value = matches
    }

    private fun nameMatchesT9(name: String, query: String): Boolean {
        val t9Map = mapOf(
            '2' to "abc", '3' to "def", '4' to "ghi", '5' to "jkl",
            '6' to "mno", '7' to "pqrs", '8' to "tuv", '9' to "wxyz"
        )
        
        val normalizedName = name.lowercase()
        // Simple T9 check: see if the name starts with characters matching the query digits
        if (query.length > normalizedName.length) return false
        
        for (i in query.indices) {
            val digit = query[i]
            val allowedChars = t9Map[digit] ?: return false
            if (normalizedName[i] !in allowedChars) {
                // Also check if any part of the name (after space) matches
                val parts = normalizedName.split(" ")
                for (part in parts) {
                    if (part.length >= query.length) {
                        var partMatch = true
                        for (j in query.indices) {
                            if (part[j] !in (t9Map[query[j]] ?: "")) {
                                partMatch = false
                                break
                            }
                        }
                        if (partMatch) return true
                    }
                }
                return false
            }
        }
        return true
    }
}
