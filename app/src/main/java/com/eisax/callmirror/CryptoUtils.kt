package com.eisax.callmirror

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Message protection for protocol CM3.
 *
 * Outgoing envelopes use CM3: a per-message random 96-bit nonce plus
 * AES-256-GCM under a key derived once from the pairing secret and cached
 * (see [Cm3Keys]). Incoming CM2 envelopes — which carried a per-message
 * PBKDF2 salt — are still decrypted for migration compatibility, but no new
 * CM2 message is ever produced.
 */
object CryptoUtils {
    private const val PREFIX_CM3 = "CM3:"
    private const val PREFIX_CM2 = "CM2:"
    private const val MIN_SECRET_CHARS = 16
    private const val NONCE_LEN = 12
    private const val TAG_BITS = 128
    private const val TAG_BYTES = TAG_BITS / 8
    private const val AES_GCM = "AES/GCM/NoPadding"

    // Legacy CM2 receive path only.
    private const val LEGACY_SALT_LEN = 16
    private const val LEGACY_PBKDF2_ROUNDS = 210_000

    private val rng = SecureRandom()

    fun encrypt(plainText: String, pairingSecret: String): String? {
        return try {
            require(pairingSecret.length >= MIN_SECRET_CHARS)
            val nonce = ByteArray(NONCE_LEN).also(rng::nextBytes)
            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                Cm3Keys.forSecret(pairingSecret).payloadKey,
                GCMParameterSpec(TAG_BITS, nonce)
            )
            val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            PREFIX_CM3 + Base64.getEncoder().encodeToString(nonce + ciphertext)
        } catch (_: Exception) {
            null
        }
    }

    fun decrypt(cipherText: String, pairingSecret: String): String? {
        return try {
            when {
                pairingSecret.length < MIN_SECRET_CHARS -> null
                cipherText.startsWith(PREFIX_CM3) -> decryptCm3(cipherText, pairingSecret)
                cipherText.startsWith(PREFIX_CM2) -> decryptLegacyCm2(cipherText, pairingSecret)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun topicFor(pairingSecret: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(("callmirror-topic-v2:" + pairingSecret).toByteArray(Charsets.UTF_8))
        return "callmirror_" + digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    private fun decryptCm3(cipherText: String, pairingSecret: String): String? {
        val packed = Base64.getDecoder().decode(cipherText.removePrefix(PREFIX_CM3))
        if (packed.size <= NONCE_LEN + TAG_BYTES) return null
        val nonce = packed.copyOfRange(0, NONCE_LEN)
        val ciphertext = packed.copyOfRange(NONCE_LEN, packed.size)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(
            Cipher.DECRYPT_MODE,
            Cm3Keys.forSecret(pairingSecret).payloadKey,
            GCMParameterSpec(TAG_BITS, nonce)
        )
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun decryptLegacyCm2(cipherText: String, pairingSecret: String): String? {
        val packed = Base64.getDecoder().decode(cipherText.removePrefix(PREFIX_CM2))
        if (packed.size <= LEGACY_SALT_LEN + NONCE_LEN) return null
        val salt = packed.copyOfRange(0, LEGACY_SALT_LEN)
        val nonce = packed.copyOfRange(LEGACY_SALT_LEN, LEGACY_SALT_LEN + NONCE_LEN)
        val ciphertext = packed.copyOfRange(LEGACY_SALT_LEN + NONCE_LEN, packed.size)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(
            Cipher.DECRYPT_MODE,
            deriveLegacyKey(pairingSecret, salt),
            GCMParameterSpec(TAG_BITS, nonce)
        )
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun deriveLegacyKey(pairingSecret: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(pairingSecret.toCharArray(), salt, LEGACY_PBKDF2_ROUNDS, 256)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}
