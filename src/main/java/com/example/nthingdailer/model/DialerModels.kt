package com.example.nthingdailer.model

data class ContactItem(
    val id: Long,
    val name: String,
    val number: String,
    val initial: String,
    val type: String,
    val favorite: Boolean
)

data class RecentItem(
    val id: Long,
    val name: String,
    val number: String,
    val type: String, // "incoming", "outgoing", "missed"
    val time: String,
    val duration: String,
    val missed: Boolean,
    val recordingPath: String? = null,
    val timestamp: Long = 0
)

object SampleData {
    val initialContacts = listOf(
        ContactItem(1, "Carl Pei", "+44 7700 900077", "C", "Mobile", true),
        ContactItem(2, "Akis Evangelidis", "+44 7700 900123", "A", "Work", true),
        ContactItem(3, "Design Team", "+1 555 019 2831", "D", "Work", false),
        ContactItem(4, "Madina Aliyeva", "+1 555 012 9944", "M", "Mobile", true),
        ContactItem(5, "Nothing Support", "+1 800 555 0199", "N", "Support", false),
        ContactItem(6, "Tom Holland", "+1 212 555 0172", "T", "Personal", false)
    )

    val initialRecents = listOf(
        RecentItem(1, "Carl Pei", "+44 7700 900077", "incoming", "10:42 AM", "2m 14s", false),
        RecentItem(2, "Madina Aliyeva", "+1 555 012 9944", "missed", "Yesterday", "0s", true),
        RecentItem(3, "Unknown", "+1 555 982 1100", "outgoing", "Jul 22", "45s", false),
        RecentItem(4, "Akis Evangelidis", "+44 7700 900123", "incoming", "Jul 21", "8m 02s", false)
    )
}
