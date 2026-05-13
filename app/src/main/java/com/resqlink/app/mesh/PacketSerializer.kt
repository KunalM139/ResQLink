package com.resqlink.app.mesh

import com.resqlink.app.crypto.PacketEncryption
import com.resqlink.app.data.model.EmergencyPacket
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serializes EmergencyPacket to/from byte arrays for BLE transmission.
 * Payloads are encrypted before broadcast and decrypted on receipt.
 */
@Singleton
class PacketSerializer @Inject constructor(
    private val encryption: PacketEncryption
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun serialize(packet: EmergencyPacket): ByteArray {
        val jsonString = json.encodeToString(EmergencyPacket.serializer(), packet)
        return encryption.encrypt(jsonString.toByteArray(Charsets.UTF_8))
    }

    fun deserialize(data: ByteArray): EmergencyPacket? {
        return try {
            val decrypted = encryption.decrypt(data)
            val jsonString = decrypted.toString(Charsets.UTF_8)
            json.decodeFromString(EmergencyPacket.serializer(), jsonString)
        } catch (e: Exception) {
            Timber.e(e, "Failed to deserialize packet")
            null
        }
    }
}
