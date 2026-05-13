package com.resqlink.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.resqlink.app.R
import com.resqlink.app.data.model.EmergencyPacket
import com.resqlink.app.data.repository.EmergencyRepository
import com.resqlink.app.domain.usecase.RelayPacketUseCase
import com.resqlink.app.mesh.MeshManager
import com.resqlink.app.ui.MainActivity
import com.resqlink.app.util.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Foreground service that keeps the BLE mesh network active.
 *
 * Responsibilities:
 * - BLE scanning for nearby ResQLink peers
 * - BLE advertising when this device has packets to relay
 * - Processing received mesh packets (relay or gateway upload)
 * - Monitoring connectivity to upload stored packets when internet returns
 * - Periodically cleaning up expired packets
 */
@AndroidEntryPoint
class MeshForegroundService : LifecycleService() {

    @Inject lateinit var meshManager: MeshManager
    @Inject lateinit var relayPacketUseCase: RelayPacketUseCase
    @Inject lateinit var emergencyRepository: EmergencyRepository
    @Inject lateinit var connectivityMonitor: ConnectivityMonitor
    @Inject lateinit var firebaseService: com.resqlink.app.data.remote.FirebaseService

    private var connectivityJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        Timber.d("MeshForegroundService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> startMeshService()
            ACTION_STOP -> stopMeshService()
        }

        return START_STICKY
    }

    private fun startMeshService() {
        val notification = buildForegroundNotification()
        startForeground(Constants.MESH_NOTIFICATION_ID, notification)

        // Start mesh networking — BLE received packets are relay-only (no alert)
        meshManager.onPacketReceived = { packet ->
            handleBleReceivedPacket(packet)
        }
        meshManager.start()

        // Listen for incoming emergencies from Firestore — these are real alerts
        firebaseService.listenForEmergencies(
            sinceTimestamp = System.currentTimeMillis() - Constants.PACKET_EXPIRY_MS
        ) { packet ->
            handleFirestoreReceivedPacket(packet)
        }

        // Monitor connectivity for store-carry-forward
        connectivityJob = lifecycleScope.launch {
            connectivityMonitor.observeConnectionStatus().collect { status ->
                if (status == com.resqlink.app.data.model.ConnectionStatus.ONLINE) {
                    uploadStoredPackets()
                }
            }
        }

        // Periodic cleanup
        lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(Constants.CLEANUP_INTERVAL_MS)
                emergencyRepository.cleanupExpiredPackets()
            }
        }

        Timber.d("Mesh service started")
    }

    private fun stopMeshService() {
        meshManager.stop()
        firebaseService.stopListeningForEmergencies()
        connectivityJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Timber.d("Mesh service stopped")
    }

    /**
     * BLE mesh received packet — this device is an intermediate relay node.
     * Relay the packet (gateway SMS/Firebase or BLE rebroadcast) but do NOT
     * show it in this device's Alerts or notifications.
     */
    private fun handleBleReceivedPacket(packet: EmergencyPacket) {
        lifecycleScope.launch {
            relayPacketUseCase(packet)
        }
    }

    /**
     * Firestore received packet — this is a genuine alert for this user.
     * Save it for display in Alerts and show a notification.
     */
    private fun handleFirestoreReceivedPacket(packet: EmergencyPacket) {
        lifecycleScope.launch {
            if (!emergencyRepository.packetExists(packet.messageId)) {
                emergencyRepository.savePacket(packet)
                showEmergencyNotification(packet)
            }
        }
    }

    private fun showEmergencyNotification(packet: EmergencyPacket) {
        val notificationManager = getSystemService(NotificationManager::class.java)

        val mapsUrl = "https://maps.google.com/?q=${packet.latitude},${packet.longitude}"
        val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl))
        val pendingIntent = PendingIntent.getActivity(
            this, packet.messageId.hashCode(), mapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val timeStr = dateFormat.format(Date(packet.timestamp))

        val bigText = buildString {
            appendLine("EMERGENCY ALERT")
            appendLine()
            if (packet.senderPhone.isNotBlank()) {
                appendLine("From: ${packet.senderName} (${packet.senderPhone})")
            } else {
                appendLine("From: ${packet.senderName}")
            }
            appendLine("Message: ${packet.message}")
            appendLine("Location: $mapsUrl")
            appendLine("Time: $timeStr")
        }

        val notification = NotificationCompat.Builder(this, Constants.CHANNEL_ALERT_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("EMERGENCY from ${packet.senderName}")
            .setContentText(packet.message)
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

        notificationManager.notify(packet.messageId.hashCode(), notification)
    }

    /**
     * Store-carry-forward: when internet becomes available,
     * upload all undelivered packets to the server.
     */
    private suspend fun uploadStoredPackets() {
        Timber.d("Internet available — uploading stored packets")
        val undelivered = emergencyRepository.getUndeliveredPackets()
        for (packet in undelivered) {
            emergencyRepository.uploadPacketToServer(packet)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val meshChannel = NotificationChannel(
                Constants.CHANNEL_MESH_ID,
                getString(R.string.channel_mesh_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_mesh_description)
            }

            val alertChannel = NotificationChannel(
                Constants.CHANNEL_ALERT_ID,
                getString(R.string.channel_alert_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_alert_description)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(meshChannel)
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.CHANNEL_MESH_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.mesh_notification_title))
            .setContentText(getString(R.string.mesh_notification_text))
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        meshManager.stop()
        firebaseService.stopListeningForEmergencies()
        connectivityJob?.cancel()
        super.onDestroy()
        Timber.d("MeshForegroundService destroyed")
    }

    companion object {
        const val ACTION_START = "com.resqlink.START_MESH"
        const val ACTION_STOP = "com.resqlink.STOP_MESH"

        fun startIntent(context: android.content.Context): Intent {
            return Intent(context, MeshForegroundService::class.java).apply {
                action = ACTION_START
            }
        }

        fun stopIntent(context: android.content.Context): Intent {
            return Intent(context, MeshForegroundService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }
}
