package com.resqlink.app.domain.usecase

import com.resqlink.app.data.model.EmergencyPacket
import com.resqlink.app.data.repository.EmergencyRepository
import com.resqlink.app.mesh.MeshManager
import com.resqlink.app.util.NetworkUtil
import timber.log.Timber
import javax.inject.Inject

class RelayPacketUseCase @Inject constructor(
    private val emergencyRepository: EmergencyRepository,
    private val meshManager: MeshManager,
    private val networkUtil: NetworkUtil
) {
    suspend operator fun invoke(packet: EmergencyPacket): Result<Unit> {
        return try {
            // Check for duplicate
            if (emergencyRepository.packetExists(packet.messageId)) {
                Timber.d("Duplicate packet ignored: ${packet.messageId}")
                return Result.failure(IllegalStateException("Duplicate packet"))
            }

            // Store the packet as a relay (won't appear in this device's Alerts)
            emergencyRepository.savePacket(packet, isRelayed = true)

            val hasInternet = networkUtil.isInternetAvailable()
            val hasCellular = networkUtil.isCellularNetworkAvailable()

            if (hasInternet) {
                // GATEWAY NODE — upload to server
                Timber.d("Gateway mode: uploading packet ${packet.messageId}")
                val uploaded = emergencyRepository.uploadPacketToServer(packet)
                if (uploaded) {
                    Timber.d("Packet delivered to server via gateway")
                }
            }

            if (packet.receiverPhones.isNotBlank()) {
                // GATEWAY NODE — forward SMS to receiver phones.
                // Attempt regardless of cellular data transport detection; SMS can work even when
                // active data is Wi-Fi-only.
                Timber.d("Gateway mode: sending SMS to ${packet.receiverPhones}")
                val phones = packet.receiverPhones.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                phones.forEach { phone ->
                    try {
                        emergencyRepository.sendSmsBackup(packet, phone)
                    } catch (e: Exception) {
                        Timber.w(e, "Gateway SMS to $phone failed")
                    }
                }
                emergencyRepository.markRelayed(packet.messageId)
            }

            if (!hasInternet && !hasCellular) {
                // No connectivity — rebroadcast via BLE with incremented hop count
                if (packet.hopCount < packet.ttl) {
                    val relayPacket = packet.copy(hopCount = packet.hopCount + 1)
                    Timber.d("Rebroadcasting packet: hop ${relayPacket.hopCount}/${relayPacket.ttl}")
                    meshManager.broadcastPacket(relayPacket)
                } else {
                    Timber.d("Packet TTL expired: ${packet.messageId}")
                }
                emergencyRepository.markRelayed(packet.messageId)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to relay packet")
            Result.failure(e)
        }
    }
}
