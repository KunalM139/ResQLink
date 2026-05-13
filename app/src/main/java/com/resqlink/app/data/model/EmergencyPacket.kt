package com.resqlink.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class EmergencyPacket(
    val messageId: String,
    val senderId: String,
    val receiverId: String,
    val senderName: String,
    val latitude: Double,
    val longitude: Double,
    val message: String,
    val timestamp: Long,
    val ttl: Int = 5,
    val hopCount: Int = 0,
    val receiverPhones: String = "",
    val senderPhone: String = ""
)
