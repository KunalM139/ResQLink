package com.resqlink.app.data.remote

import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import com.resqlink.app.data.model.EmergencyPacket
import com.resqlink.app.util.Constants
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val messaging: FirebaseMessaging,
    private val sharedPreferences: SharedPreferences
) {

    val currentUserId: String?
        get() = auth.currentUser?.uid

    suspend fun uploadEmergencyPacket(packet: EmergencyPacket): Result<Unit> {
        return try {
            val data = hashMapOf(
                "messageId" to packet.messageId,
                "senderId" to packet.senderId,
                "receiverId" to packet.receiverId,
                "senderName" to packet.senderName,
                "latitude" to packet.latitude,
                "longitude" to packet.longitude,
                "message" to packet.message,
                "timestamp" to packet.timestamp,
                "ttl" to packet.ttl,
                "hopCount" to packet.hopCount,
                "receiverPhones" to packet.receiverPhones,
                "senderPhone" to packet.senderPhone
            )

            firestore.collection(COLLECTION_EMERGENCIES)
                .document(packet.messageId)
                .set(data)
                .await()

            Timber.d("Packet uploaded: ${packet.messageId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload packet")
            Result.failure(e)
        }
    }

    suspend fun registerFcmToken() {
        try {
            val token = messaging.token.await()
            val userId = currentUserId ?: return

            firestore.collection(COLLECTION_TOKENS)
                .document(userId)
                .set(hashMapOf("token" to token, "updatedAt" to System.currentTimeMillis()))
                .await()

            Timber.d("FCM token registered")
        } catch (e: Exception) {
            Timber.e(e, "Failed to register FCM token")
        }
    }

    suspend fun getReceiverFcmToken(receiverId: String): String? {
        return try {
            val doc = firestore.collection(COLLECTION_TOKENS)
                .document(receiverId)
                .get()
                .await()
            doc.getString("token")
        } catch (e: Exception) {
            Timber.e(e, "Failed to get receiver FCM token")
            null
        }
    }

    suspend fun signInAnonymously(): Result<String> {
        return try {
            val result = auth.signInAnonymously().await()
            val uid = result.user?.uid ?: throw IllegalStateException("No user ID")
            Timber.d("Signed in: $uid")
            Result.success(uid)
        } catch (e: Exception) {
            Timber.e(e, "Sign-in failed")
            Result.failure(e)
        }
    }

    suspend fun saveUserProfile(userId: String, name: String, phone: String) {
        try {
            firestore.collection(COLLECTION_USERS)
                .document(userId)
                .set(hashMapOf("name" to name, "phone" to phone))
                .await()
        } catch (e: Exception) {
            Timber.e(e, "Failed to save user profile")
        }
    }

    private var emergencyListener: ListenerRegistration? = null

    private fun getLastSeenTimestamp(): Long {
        return sharedPreferences.getLong(KEY_LAST_SEEN_TIMESTAMP, 0L)
    }

    private fun updateLastSeenTimestamp(timestamp: Long) {
        val current = getLastSeenTimestamp()
        if (timestamp > current) {
            sharedPreferences.edit().putLong(KEY_LAST_SEEN_TIMESTAMP, timestamp).apply()
        }
    }

    private fun normalizePhone(phone: String): String {
        return phone.filter { it.isDigit() }
    }

    private fun isPacketForCurrentUser(senderId: String, receiverId: String, receiverPhones: String): Boolean {
        // Never show own uploads as incoming alerts.
        if (senderId == currentUserId) return false

        // If explicit receiverId is used, honor it.
        if (receiverId.isNotBlank() && receiverId == currentUserId) return true

        // If receiver phones are present, only deliver to matching local phone.
        if (receiverPhones.isNotBlank()) {
            val myPhoneRaw = sharedPreferences.getString(Constants.KEY_USER_PHONE, "") ?: ""
            val myPhone = normalizePhone(myPhoneRaw)
            if (myPhone.isBlank()) {
                Timber.d("Skipping packet: receiverPhones present but local phone not configured")
                return false
            }

            val targets = receiverPhones.split(",")
                .map { normalizePhone(it.trim()) }
                .filter { it.isNotBlank() }

            return targets.any { target ->
                myPhone == target || myPhone.endsWith(target) || target.endsWith(myPhone)
            }
        }

        // Legacy packets with no targeting info are allowed.
        return true
    }

    fun listenForEmergencies(
        sinceTimestamp: Long,
        onPacketReceived: (EmergencyPacket) -> Unit
    ) {
        emergencyListener?.remove()

        // Use the later of: provided timestamp or last-seen timestamp
        // This prevents replaying old alerts after app update/restart
        val lastSeen = getLastSeenTimestamp()
        val effectiveTimestamp = maxOf(sinceTimestamp, lastSeen)

        emergencyListener = firestore.collection(COLLECTION_EMERGENCIES)
            .whereGreaterThan("timestamp", effectiveTimestamp)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Timber.e(error, "Firestore listener error")
                    return@addSnapshotListener
                }

                snapshots?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val doc = change.document
                        val senderId = doc.getString("senderId") ?: return@forEach
                        val receiverId = doc.getString("receiverId") ?: ""
                        val receiverPhones = doc.getString("receiverPhones") ?: ""

                        if (!isPacketForCurrentUser(senderId, receiverId, receiverPhones)) {
                            return@forEach
                        }

                        val packet = EmergencyPacket(
                            messageId = doc.getString("messageId") ?: return@forEach,
                            senderId = senderId,
                            receiverId = receiverId,
                            senderName = doc.getString("senderName") ?: "Unknown",
                            latitude = doc.getDouble("latitude") ?: 0.0,
                            longitude = doc.getDouble("longitude") ?: 0.0,
                            message = doc.getString("message") ?: "Emergency!",
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                            ttl = (doc.getLong("ttl") ?: 5).toInt(),
                            hopCount = (doc.getLong("hopCount") ?: 0).toInt(),
                            receiverPhones = receiverPhones,
                            senderPhone = doc.getString("senderPhone") ?: ""
                        )
                        Timber.d("Received emergency from Firestore: ${packet.messageId}")
                        onPacketReceived(packet)
                        updateLastSeenTimestamp(packet.timestamp)
                    }
                }
            }
    }

    fun stopListeningForEmergencies() {
        emergencyListener?.remove()
        emergencyListener = null
    }

    companion object {
        private const val COLLECTION_EMERGENCIES = "emergencies"
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_TOKENS = "fcm_tokens"
        private const val KEY_LAST_SEEN_TIMESTAMP = "last_seen_firestore_timestamp"
    }
}
