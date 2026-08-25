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
                val name = cursor.getString(nameIndex) ?: "Unknown"
                val number = cursor.getString(numberIndex) ?: ""
                val typeInt = cursor.getInt(typeIndex)
                val dateLong = cursor.getLong(dateIndex)
                val durationSeconds = cursor.getLong(durationIndex)

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
