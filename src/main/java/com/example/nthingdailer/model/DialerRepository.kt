package com.example.nthingdailer.model

import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DialerRepository(private val context: Context) {

    suspend fun fetchContacts(): List<ContactItem> = withContext(Dispatchers.IO) {
        val contacts = mutableListOf<ContactItem>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.STARRED
        )

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
            val starredIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val name = cursor.getString(nameIndex) ?: "Unknown"
                val number = cursor.getString(numberIndex) ?: ""
                val typeInt = cursor.getInt(typeIndex)
                val isStarred = cursor.getInt(starredIndex) == 1

                val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                    context.resources, typeInt, ""
                ).toString()

                contacts.add(
                    ContactItem(
                        id = id,
                        name = name,
                        number = number,
                        initial = name.take(1).uppercase(),
                        type = label,
                        favorite = isStarred
                    )
                )
            }
        }
        contacts
    }

    suspend fun toggleFavorite(contactId: Long, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        val values = android.content.ContentValues().apply {
            put(ContactsContract.Contacts.STARRED, if (isFavorite) 1 else 0)
        }
        try {
            context.contentResolver.update(
                ContactsContract.Contacts.CONTENT_URI,
                values,
                "${ContactsContract.Contacts._ID} = ?",
                arrayOf(contactId.toString())
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun blockNumber(number: String) {
        val prefs = context.getSharedPreferences("nthing_prefs", Context.MODE_PRIVATE)
        val blocked = prefs.getStringSet("blocked_numbers", mutableSetOf()) ?: mutableSetOf()
        val updated = blocked.toMutableSet().apply { add(number.replace("\\D".toRegex(), "")) }
        prefs.edit().putStringSet("blocked_numbers", updated).apply()
    }

    fun unblockNumber(number: String) {
        val prefs = context.getSharedPreferences("nthing_prefs", Context.MODE_PRIVATE)
        val blocked = prefs.getStringSet("blocked_numbers", mutableSetOf()) ?: mutableSetOf()
        val updated = blocked.toMutableSet().apply { remove(number.replace("\\D".toRegex(), "")) }
        prefs.edit().putStringSet("blocked_numbers", updated).apply()
    }

    fun getBlockedNumbers(): Set<String> {
        val prefs = context.getSharedPreferences("nthing_prefs", Context.MODE_PRIVATE)
        return prefs.getStringSet("blocked_numbers", emptySet()) ?: emptySet()
    }

    fun isNumberBlocked(number: String): Boolean {
        val cleanNum = number.replace("\\D".toRegex(), "")
        if (cleanNum.isEmpty()) return false
        val blocked = getBlockedNumbers()
        return blocked.any { it == cleanNum || cleanNum.endsWith(it) || it.endsWith(cleanNum) }
    }

    suspend fun fetchAllRecordings(): List<RecordingItem> = withContext(Dispatchers.IO) {
        val recordings = mutableListOf<RecordingItem>()
        val prefs = context.getSharedPreferences("nthing_prefs", Context.MODE_PRIVATE)
        val allRecordingIds = (prefs.getStringSet("all_recordings", emptySet()) ?: emptySet()).toMutableSet()
        
        val contacts = fetchContacts()
        var changed = false

        for (recId in allRecordingIds.toList()) {
            val path = prefs.getString(recId, null) ?: continue
            
            // Check if file actually exists on the device
            val file = java.io.File(path)
            if (!file.exists()) {
                // Clean up stale reference
                allRecordingIds.remove(recId)
                prefs.edit().remove(recId).apply()
                changed = true
                continue
            }

            // ID format: rec_number_timestamp
            val parts = recId.removePrefix("rec_").split("_")
            if (parts.size >= 2) {
                val number = parts[0]
                val timestamp = parts[1].toLongOrNull() ?: 0L
                
                val contactName = contacts.find { it.number.replace("\\D".toRegex(), "") == number.replace("\\D".toRegex(), "") }?.name ?: "Unknown"
                
                recordings.add(
                    RecordingItem(
                        id = recId,
                        name = contactName,
                        number = number,
                        date = formatTime(timestamp),
                        path = path,
                        timestamp = timestamp
                    )
                )
            }
        }
        
        if (changed) {
            prefs.edit().putStringSet("all_recordings", allRecordingIds).apply()
        }
        
        recordings.sortedByDescending { it.timestamp }
    }

    suspend fun fetchCallLogs(): List<RecentItem> = withContext(Dispatchers.IO) {
        val recents = mutableListOf<RecentItem>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )

        val contacts = fetchContacts()

        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            CallLog.Calls.DATE + " DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(CallLog.Calls._ID)
            val nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
            val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
            val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)

            val prefs = context.getSharedPreferences("nthing_prefs", Context.MODE_PRIVATE)
            val allRecordingIds = prefs.getStringSet("all_recordings", emptySet()) ?: emptySet()

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val cachedName = cursor.getString(nameIndex)
                val number = cursor.getString(numberIndex) ?: ""
                val typeInt = cursor.getInt(typeIndex)
                val dateLong = cursor.getLong(dateIndex)
                val durationSeconds = cursor.getLong(durationIndex)

                // SMART NAME LOOKUP: If cached name is generic, check our fresh contact list
                val name = if (cachedName.isNullOrBlank() || cachedName.equals("Unknown", true) || cachedName.equals("Unsaved", true)) {
                    val cleanNum = number.replace("\\D".toRegex(), "")
                    contacts.find { 
                        val cNum = it.number.replace("\\D".toRegex(), "")
                        cNum != "" && (cNum == cleanNum || cleanNum.endsWith(cNum) || cNum.endsWith(cleanNum))
                    }?.name ?: "Unknown"
                } else {
                    cachedName
                }

                // Look for associated recording
                var recordingPath: String? = null
                // We check for recordings within a 10-second window of the call log entry
                for (recId in allRecordingIds) {
                    if (recId.startsWith("rec_$number")) {
                        val recTime = recId.substringAfterLast("_").toLongOrNull() ?: 0L
                        if (kotlin.math.abs(recTime - dateLong) < 10000) {
                            recordingPath = prefs.getString(recId, null)
                            break
                        }
                    }
                }

                val type = when (typeInt) {
                    CallLog.Calls.INCOMING_TYPE -> "incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                    CallLog.Calls.MISSED_TYPE -> "missed"
                    else -> "unknown"
                }

                val duration = formatDuration(durationSeconds)
                val timeLabel = formatTime(dateLong)

                recents.add(
                    RecentItem(
                        id = id,
                        name = name,
                        number = number,
                        type = type,
                        time = timeLabel,
                        duration = duration,
                        missed = typeInt == CallLog.Calls.MISSED_TYPE,
                        recordingPath = recordingPath,
                        timestamp = dateLong
                    )
                )
            }
        }
        recents
    }

    private fun formatDuration(seconds: Long): String {
        return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
    }

    private fun formatTime(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        val sdf = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
        return sdf.format(date)
    }
}
