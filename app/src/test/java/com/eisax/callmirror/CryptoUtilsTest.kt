package com.eisax.callmirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class CryptoUtilsTest {

    private val secret = "ABCDEFGHJKLMNPQRSTUVWXYZabcd"

    @Test
    fun roundtripRecoversPlaintext() {
        val encrypted = CryptoUtils.encrypt("hello mirror", secret)
        assertNotNull(encrypted)
        assertEquals("hello mirror", CryptoUtils.decrypt(encrypted!!, secret))
    }

    @Test
    fun encryptEmitsCm3Envelope() {
        val encrypted = CryptoUtils.encrypt("hello", secret)!!
        assertTrue(encrypted.startsWith("CM3:"))
        assertEquals("hello", CryptoUtils.decrypt(encrypted, secret))
    }

    @Test
    fun wrongSecretFailsClosed() {
        val encrypted = CryptoUtils.encrypt("hello", secret)!!
        assertNull(CryptoUtils.decrypt(encrypted, "wrong-wrong-wrong!!"))
    }

    @Test
    fun tamperedCiphertextFailsClosed() {
        val encrypted = CryptoUtils.encrypt("hello", secret)!!
        val packed = java.util.Base64.getDecoder().decode(encrypted.removePrefix("CM3:"))
        val index = packed.size - 1
        packed[index] = packed[index].toInt().xor(1).toByte()
        val tampered = "CM3:" + java.util.Base64.getEncoder().encodeToString(packed)
        assertNull(CryptoUtils.decrypt(tampered, secret))
    }

    @Test
    fun tamperedNonceFailsClosed() {
        val encrypted = CryptoUtils.encrypt("hello", secret)!!
        val packed = java.util.Base64.getDecoder().decode(encrypted.removePrefix("CM3:"))
        packed[0] = packed[0].toInt().xor(1).toByte()
        val tampered = "CM3:" + java.util.Base64.getEncoder().encodeToString(packed)
        assertNull(CryptoUtils.decrypt(tampered, secret))
    }

    @Test
    fun malformedInputsAreRejected() {
        assertNull(CryptoUtils.decrypt("garbage", secret))
        assertNull(CryptoUtils.decrypt("CM3:", secret))
        assertNull(CryptoUtils.decrypt("CM3:notbase64!!", secret))
        assertNull(CryptoUtils.decrypt("CM2:", secret))
        assertNull(CryptoUtils.encrypt("hello", "short"))
    }

    @Test
    fun topicIsStableDerivedAndSecretProof() {
        val topic = CryptoUtils.topicFor(secret)
        assertEquals(topic, CryptoUtils.topicFor(secret))
        assertTrue(topic.startsWith("callmirror_"))
        assertTrue(topic.matches(Regex("callmirror_[0-9a-f]{32}")))
        assertNotEquals(topic, CryptoUtils.topicFor("other-secret-16chars!!"))
        assertTrue(!topic.contains(secret))
    }

    @Test
    fun everyMessageUsesFreshNonce() {
        val first = CryptoUtils.encrypt("same text", secret)!!
        val second = CryptoUtils.encrypt("same text", secret)!!
        assertNotEquals(first, second)
    }

    @Test
    fun derivedKeysAreCachedStableAndDomainSeparated() {
        val first = Cm3Keys.forSecret(secret)
        val second = Cm3Keys.forSecret(secret)
        assertSame(first, second)
        assertNotEquals(first.payloadKey.encoded.toList(), first.ackKey.encoded.toList())
        assertNotEquals(
            Cm3Keys.forSecret("other-secret-16chars!!").payloadKey.encoded.toList(),
            first.payloadKey.encoded.toList()
        )
    }

    @Test
    fun decryptsLegacyCm2EnvelopesForMigration() {
        val legacy = legacyCm2Encrypt("legacy hello", secret)
        assertTrue(legacy.startsWith("CM2:"))
        assertEquals("legacy hello", CryptoUtils.decrypt(legacy, secret))
    }

    @Test
    fun legacyCm2WithWrongSecretFailsClosed() {
        val legacy = legacyCm2Encrypt("legacy hello", secret)
        assertNull(CryptoUtils.decrypt(legacy, "wrong-wrong-wrong!!"))
    }

    /**
     * Independent CM2 implementation (per-message PBKDF2 salt), used to prove the
     * migration receive path still interoperates with a pre-CM3 sender.
     */
    private fun legacyCm2Encrypt(plainText: String, pairingSecret: String): String {
        val random = java.security.SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(
            PBEKeySpec(pairingSecret.toCharArray(), salt, 210_000, 256)
        ).encoded
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, nonce))
        val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return "CM2:" + java.util.Base64.getEncoder().encodeToString(salt + nonce + ciphertext)
    }
}
