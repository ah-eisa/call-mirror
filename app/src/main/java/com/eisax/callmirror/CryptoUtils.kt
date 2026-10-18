package com.eisax.callmirror

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private const val PREFIX = "CM2:"
    private const val SALT_LEN = 16
    private const val NONCE_LEN = 12
    private const val TAG_BITS = 128
    private const val PBKDF2_ROUNDS = 210_000
    private val rng = SecureRandom()

    fun encrypt(plainText: String, pairingSecret: String): String? = try {
        require(pairingSecret.length >= 16)
        val salt = ByteArray(SALT_LEN).also(rng::nextBytes)
        val nonce = ByteArray(NONCE_LEN).also(rng::nextBytes)
        val key = deriveKey(pairingSecret, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val packed = salt + nonce + ciphertext
        PREFIX + Base64.encodeToString(packed, Base64.NO_WRAP)
    } catch (_: Exception) {
        null
    }

    fun decrypt(cipherText: String, pairingSecret: String): String? = try {
        if (!cipherText.startsWith(PREFIX) || pairingSecret.length < 16) return null
        val packed = Base64.decode(cipherText.removePrefix(PREFIX), Base64.NO_WRAP)
        if (packed.size <= SALT_LEN + NONCE_LEN) return null
        val salt = packed.copyOfRange(0, SALT_LEN)
        val nonce = packed.copyOfRange(SALT_LEN, SALT_LEN + NONCE_LEN)
        val encrypted = packed.copyOfRange(SALT_LEN + NONCE_LEN, packed.size)
        val key = deriveKey(pairingSecret, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    fun topicFor(pairingSecret: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(("callmirror-topic-v2:" + pairingSecret).toByteArray(Charsets.UTF_8))
        return "callmirror_" + digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    private fun deriveKey(secret: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(secret.toCharArray(), salt, PBKDF2_ROUNDS, 256)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}
