package com.resqlink.app.data.remote

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.resqlink.app.data.model.EmergencyPacket
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun sendEmergencySms(packet: EmergencyPacket, phoneNumber: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Timber.w("SMS permission not granted")
            return false
        }

        return try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            val message = formatSmsMessage(packet)

            // Split long messages into parts
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(
                phoneNumber, null, parts, null, null
            )

            Timber.d("SMS sent to $phoneNumber")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to send SMS to $phoneNumber")
            false
        }
    }

    private fun formatSmsMessage(packet: EmergencyPacket): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val timeStr = dateFormat.format(Date(packet.timestamp))
        val mapsLink = "https://maps.google.com/?q=${packet.latitude},${packet.longitude}"

        return buildString {
            appendLine("[ResQLink] EMERGENCY ALERT")
            appendLine()
            if (packet.senderPhone.isNotBlank()) {
                appendLine("From: ${packet.senderName} (${packet.senderPhone})")
            } else {
                appendLine("From: ${packet.senderName}")
            }
            appendLine("Message: ${packet.message}")
            appendLine("Location: $mapsLink")
            appendLine("Time: $timeStr")
            appendLine()
            appendLine("Sent via ResQLink Emergency App")
        }
    }
}
