package com.resqlink.app.domain.usecase

import com.resqlink.app.data.model.EmergencyPacket
import com.resqlink.app.data.repository.ContactRepository
import com.resqlink.app.data.repository.EmergencyRepository
import com.resqlink.app.data.repository.LocationRepository
import com.resqlink.app.mesh.MeshManager
import com.resqlink.app.util.NetworkUtil
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

class SendEmergencyUseCase @Inject constructor(
    private val emergencyRepository: EmergencyRepository,
    private val contactRepository: ContactRepository,
    private val locationRepository: LocationRepository,
    private val meshManager: MeshManager,
    private val networkUtil: NetworkUtil
) {
    suspend operator fun invoke(
        senderId: String,
        senderName: String,
        senderPhone: String = "",
        message: String = "EMERGENCY! I need help!"
    ): Result<EmergencyPacket> {
        return try {
            // 1. Get current location
            val location = locationRepository.getCurrentLocation()
            val latitude = location?.latitude ?: 0.0
            val longitude = location?.longitude ?: 0.0

            // 2. Get selected emergency contacts
            val contacts = contactRepository.getSelectedContacts()
            if (contacts.isEmpty()) {
                return Result.failure(IllegalStateException("No emergency contacts configured"))
            }

            // 3. Create a single emergency packet (one broadcast, not per-contact)
            // Include receiver phone numbers so gateway/relay nodes can send SMS
            val phoneList = contacts.joinToString(",") { it.phone }
            val packet = EmergencyPacket(
                messageId = UUID.randomUUID().toString(),
                senderId = senderId,
                receiverId = "",
                senderName = senderName,
                senderPhone = senderPhone,
                latitude = latitude,
                longitude = longitude,
                message = message,
                timestamp = System.currentTimeMillis(),
                ttl = 5,
                hopCount = 0,
                receiverPhones = phoneList
            )
            emergencyRepository.savePacket(packet, isOwn = true)

            // 4. Send via best available method
            val hasInternet = networkUtil.isInternetAvailable()
            val hasCellular = networkUtil.isCellularNetworkAvailable()

            if (hasInternet && hasCellular) {
                // CASE 1: Internet + Cellular — send via Firebase AND SMS
                Timber.d("Sending via internet + SMS (Case 1)")
                emergencyRepository.uploadPacketToServer(packet)
                contacts.forEach { contact ->
                    try {
                        emergencyRepository.sendSmsBackup(packet, contact.phone)
                    } catch (e: Exception) {
                        Timber.w(e, "SMS send failed for ${contact.phone}")
                    }
                }
            } else if (hasInternet) {
                // CASE 2: Internet only (no cellular) — send via Firebase only
                Timber.d("Sending via internet only (Case 2)")
                emergencyRepository.uploadPacketToServer(packet)
            } else if (hasCellular) {
                // Cellular only (no internet) — send via SMS
                Timber.d("No internet but cellular available — sending via SMS")
                contacts.forEach { contact ->
                    emergencyRepository.sendSmsBackup(packet, contact.phone)
                }
            } else {
                // CASE 3: No internet, no cellular — BLE Mesh Relay
                Timber.d("No internet or cellular — sending via BLE mesh (Case 3)")
                meshManager.broadcastPacket(packet)
            }

            Result.success(packet)
        } catch (e: Exception) {
            Timber.e(e, "Failed to send emergency")
            Result.failure(e)
        }
    }
}
