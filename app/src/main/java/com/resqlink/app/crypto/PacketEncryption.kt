package com.resqlink.app.crypto

import timber.log.Timber
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256-GCM encryption for emergency packets transmitted over BLE mesh.
 *
 * Uses a shared app-level key so ALL ResQLink devices can decrypt mesh
 * packets from any other ResQLink device. This is essential for the
 * multi-hop relay to work — every node in the chain must be able to
 * read the packet to store it and show notifications.
 *
 * The IV is randomly generated per encryption and prepended to the
 * ciphertext so the receiver can decrypt.
 *
 * Production note: In a real deployment you would distribute the key
 * via a secure channel (e.g., Firebase Remote Config over TLS, or
 * ECDH key exchange during contact setup).
 */
@Singleton
class PacketEncryption @Inject constructor() {

    companion object {
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        // Shared key across all app installations (32 bytes = AES-256)
        private val SHARED_KEY_BYTES = byteArrayOf(
            0x52, 0x65, 0x73, 0x51, 0x4C, 0x69, 0x6E, 0x6B,  // ResQLink
            0x45, 0x6D, 0x65, 0x72, 0x67, 0x65, 0x6E, 0x63,  // Emergenc
            0x79, 0x4D, 0x65, 0x73, 0x68, 0x4B, 0x65, 0x79,  // yMeshKey
            0x32, 0x30, 0x32, 0x36, 0x53, 0x65, 0x63, 0x75   // 2026Secu
        )
    }

    private val secretKey: SecretKey = SecretKeySpec(SHARED_KEY_BYTES, "AES")

    /**
     * Encrypts plaintext bytes using AES-256-GCM.
     * Returns: [IV (12 bytes)] + [ciphertext + GCM tag]
     */
    fun encrypt(plaintext: ByteArray): ByteArray {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext)

            // Prepend IV to ciphertext
            iv + ciphertext
        } catch (e: Exception) {
            Timber.e(e, "Encryption failed")
            throw e
        }
    }

    /**
     * Decrypts data produced by [encrypt].
     * Expects: [IV (12 bytes)] + [ciphertext + GCM tag]
     */
    fun decrypt(data: ByteArray): ByteArray {
        return try {
            val iv = data.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = data.copyOfRange(GCM_IV_LENGTH, data.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            Timber.e(e, "Decryption failed")
            throw e
        }
    }
}
