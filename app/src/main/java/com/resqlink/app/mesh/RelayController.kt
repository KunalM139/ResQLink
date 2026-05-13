package com.resqlink.app.mesh

import com.resqlink.app.data.model.EmergencyPacket
import com.resqlink.app.util.Constants
import kotlinx.coroutines.delay
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Controls relay behavior to prevent duplicate broadcasts and network congestion.
 *
 * - Caches seen message IDs to suppress duplicates.
 * - Enforces random jitter delay before rebroadcast.
 * - Enforces TTL / hop-count limits.
 * - Expires cache entries after 24 hours.
 */
@Singleton
class RelayController @Inject constructor() {

    // messageId → timestamp when first seen
    private val seenMessages = ConcurrentHashMap<String, Long>()

    /**
     * Returns true if this packet should be relayed (not seen before, TTL ok).
     */
    fun shouldRelay(packet: EmergencyPacket): Boolean {
        cleanupExpired()

        if (packet.hopCount >= packet.ttl) {
            Timber.d("Packet ${packet.messageId} exceeded TTL")
            return false
        }

        val alreadySeen = seenMessages.putIfAbsent(packet.messageId, System.currentTimeMillis())
        if (alreadySeen != null) {
            Timber.d("Packet ${packet.messageId} already relayed")
            return false
        }

        return true
    }

    /**
     * Mark a message as seen (e.g., own outgoing messages).
     */
    fun markSeen(messageId: String) {
        seenMessages[messageId] = System.currentTimeMillis()
    }

    /**
     * Wait a random jitter delay (1–3 seconds) before relaying
     * to reduce broadcast collisions in dense environments.
     */
    suspend fun jitterDelay() {
        val delayMs = Random.nextLong(
            Constants.RELAY_MIN_DELAY_MS,
            Constants.RELAY_MAX_DELAY_MS
        )
        Timber.d("Relay jitter: waiting ${delayMs}ms")
        delay(delayMs)
    }

    private fun cleanupExpired() {
        val cutoff = System.currentTimeMillis() - Constants.PACKET_EXPIRY_MS
        seenMessages.entries.removeAll { it.value < cutoff }
    }
}
