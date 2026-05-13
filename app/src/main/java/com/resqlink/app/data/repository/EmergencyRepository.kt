package com.resqlink.app.data.repository

import com.resqlink.app.data.local.dao.EmergencyPacketDao
import com.resqlink.app.data.local.entity.EmergencyPacketEntity
import com.resqlink.app.data.model.EmergencyPacket
import com.resqlink.app.data.remote.FirebaseService
import com.resqlink.app.data.remote.SmsService
import com.resqlink.app.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmergencyRepository @Inject constructor(
    private val packetDao: EmergencyPacketDao,
    private val firebaseService: FirebaseService,
    private val smsService: SmsService
) {

    suspend fun savePacket(packet: EmergencyPacket, isOwn: Boolean = false, isRelayed: Boolean = false): Boolean {
        val entity = packet.toEntity(isOwn, isRelayed)
        val result = packetDao.insert(entity)
        return result != -1L
    }

    suspend fun packetExists(messageId: String): Boolean {
        return packetDao.exists(messageId)
    }

    suspend fun getUndeliveredPackets(): List<EmergencyPacket> {
        return packetDao.getUndeliveredPackets().map { it.toModel() }
    }

    suspend fun getPacketsToRelay(): List<EmergencyPacketEntity> {
        return packetDao.getPacketsToRelay()
    }

    fun getReceivedAlerts(userId: String): Flow<List<EmergencyPacket>> {
        return packetDao.getReceivedPackets(userId).map { entities ->
            entities.map { it.toModel() }
        }
    }

    fun getAllAlerts(): Flow<List<EmergencyPacket>> {
        return packetDao.getAllPackets().map { entities ->
            entities.map { it.toModel() }
        }
    }

    suspend fun uploadPacketToServer(packet: EmergencyPacket): Boolean {
        val result = firebaseService.uploadEmergencyPacket(packet)
        if (result.isSuccess) {
            packetDao.markDelivered(packet.messageId)
            return true
        }
        return false
    }

    suspend fun markRelayed(messageId: String) {
        packetDao.markRelayed(messageId)
    }

    suspend fun sendSmsBackup(packet: EmergencyPacket, phoneNumber: String): Boolean {
        return smsService.sendEmergencySms(packet, phoneNumber)
    }

    suspend fun cleanupExpiredPackets() {
        val expiryTime = System.currentTimeMillis() - Constants.PACKET_EXPIRY_MS
        packetDao.deleteExpiredPackets(expiryTime)
        Timber.d("Cleaned up expired packets")
    }

    suspend fun deleteAlert(messageId: String) {
        packetDao.deleteById(messageId)
    }

    suspend fun deleteAllAlerts() {
        packetDao.deleteAllAlerts()
    }

    private fun EmergencyPacket.toEntity(isOwn: Boolean = false, isRelayed: Boolean = false) = EmergencyPacketEntity(
        messageId = messageId,
        senderId = senderId,
        receiverId = receiverId,
        senderName = senderName,
        latitude = latitude,
        longitude = longitude,
        message = message,
        timestamp = timestamp,
        ttl = ttl,
        hopCount = hopCount,
        isOwnMessage = isOwn,
        isRelayed = isRelayed,
        receiverPhones = receiverPhones,
        senderPhone = senderPhone
    )

    private fun EmergencyPacketEntity.toModel() = EmergencyPacket(
        messageId = messageId,
        senderId = senderId,
        receiverId = receiverId,
        senderName = senderName,
        latitude = latitude,
        longitude = longitude,
        message = message,
        timestamp = timestamp,
        ttl = ttl,
        hopCount = hopCount,
        receiverPhones = receiverPhones,
        senderPhone = senderPhone
    )
}
