package com.resqlink.app.data.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.resqlink.app.R
import com.resqlink.app.util.Constants
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ResQLinkMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.d("New FCM token: $token")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val senderName = data["senderName"] ?: "Unknown"
        val emergencyMessage = data["message"] ?: "Emergency!"
        val latitude = data["latitude"] ?: "0.0"
        val longitude = data["longitude"] ?: "0.0"
        val messageId = data["messageId"] ?: ""

        showEmergencyNotification(senderName, emergencyMessage, latitude, longitude, messageId)
    }

    private fun showEmergencyNotification(
        senderName: String,
        message: String,
        latitude: String,
        longitude: String,
        messageId: String
    ) {
        val notificationManager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.CHANNEL_ALERT_ID,
                getString(R.string.channel_alert_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_alert_description)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val mapsUrl = "https://maps.google.com/?q=$latitude,$longitude"

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl))

        val pendingIntent = PendingIntent.getActivity(
            this, messageId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val timeStr = dateFormat.format(Date())

        val bigText = buildString {
            appendLine("EMERGENCY ALERT")
            appendLine()
            appendLine("Message: $message")
            appendLine("Location: $mapsUrl")
            appendLine("Time: $timeStr")
        }

        val notification = NotificationCompat.Builder(this, Constants.CHANNEL_ALERT_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("EMERGENCY from $senderName")
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(bigText.trim())
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .build()

        notificationManager.notify(messageId.hashCode(), notification)
    }
}
