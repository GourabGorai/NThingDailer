package com.example.nthingdailer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    private const val BLOCKED_CHANNEL_ID = "blocked_calls_channel"
    private const val MISSED_CHANNEL_ID = "missed_calls_channel"

    fun showBlockedCallNotification(context: Context, number: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BLOCKED_CHANNEL_ID,
                "Blocked Calls",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for blocked incoming calls"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, BLOCKED_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_def_app_icon) 
            .setContentTitle("Blocked Call")
            .setContentText("Incoming call from $number was blocked.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(number.hashCode(), notification)
    }

    fun showMissedCallNotification(context: Context, number: String, name: String? = null) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MISSED_CHANNEL_ID,
                "Missed Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for missed calls"
                enableLights(true)
                lightColor = android.graphics.Color.RED
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val displayName = if (!name.isNullOrBlank() && !name.equals("Unknown", true)) name else number
        
        val notification = NotificationCompat.Builder(context, MISSED_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_missed)
            .setContentTitle("Missed Call")
            .setContentText("You missed a call from $displayName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(android.app.Notification.CATEGORY_MISSED_CALL)
            .setAutoCancel(true)
            // Intent to open dialer when tapped
            .setContentIntent(
                android.app.PendingIntent.getActivity(
                    context,
                    0,
                    android.content.Intent(context, MainActivity::class.java).apply {
                        putExtra("start_tab", "recents")
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

        notificationManager.notify("missed_$number".hashCode(), notification)
    }
}
