package com.resqlink.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "emergency_packets")
data class EmergencyPacketEntity(
    @PrimaryKey
    val messageId: String,
    val senderId: String,
    val receiverId: String,
    val senderName: String,
    val latitude: Double,
    val longitude: Double,
    val message: String,
    val timestamp: Long,
    val ttl: Int,
    val hopCount: Int,
    val isDelivered: Boolean = false,
    val isRelayed: Boolean = false,
    val isOwnMessage: Boolean = false,
    val encryptedPayload: ByteArray? = null,
    val receivedAt: Long = System.currentTimeMillis(),
    val receiverPhones: String = "",
    val senderPhone: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmergencyPacketEntity) return false
        return messageId == other.messageId
    }

    override fun hashCode(): Int = messageId.hashCode()
}
