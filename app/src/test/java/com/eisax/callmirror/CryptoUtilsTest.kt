package com.eisax.callmirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoUtilsTest {

    private val secret = "ABCDEFGHJKLMNPQRSTUVWXYZabcd"

    @Test
    fun roundtripRecoversPlaintext() {
        val encrypted = CryptoUtils.encrypt("hello mirror", secret)
        assertNotNull(encrypted)
        assertEquals("hello mirror", CryptoUtils.decrypt(encrypted!!, secret))
    }

    @Test
    fun wrongSecretFailsClosed() {
        val encrypted = CryptoUtils.encrypt("hello", secret)!!
        assertNull(CryptoUtils.decrypt(encrypted, "wrong-wrong-wrong!!"))
    }

    @Test
    fun tamperedCiphertextFailsClosed() {
        val encrypted = CryptoUtils.encrypt("hello", secret)!!
        val packed = java.util.Base64.getDecoder().decode(encrypted.removePrefix("CM2:"))
        val index = packed.size - 1
        packed[index] = packed[index].toInt().xor(1).toByte()
        val tampered = "CM2:" + java.util.Base64.getEncoder().encodeToString(packed)
        assertNull(CryptoUtils.decrypt(tampered, secret))
    }

    @Test
    fun tamperedSaltFailsClosed() {
        val encrypted = CryptoUtils.encrypt("hello", secret)!!
        val packed = java.util.Base64.getDecoder().decode(encrypted.removePrefix("CM2:"))
        packed[0] = packed[0].toInt().xor(1).toByte()
        val tampered = "CM2:" + java.util.Base64.getEncoder().encodeToString(packed)
        assertNull(CryptoUtils.decrypt(tampered, secret))
    }

    @Test
    fun malformedInputsAreRejected() {
        assertNull(CryptoUtils.decrypt("garbage", secret))
        assertNull(CryptoUtils.decrypt("CM2:", secret))
        assertNull(CryptoUtils.decrypt("CM2:notbase64!!", secret))
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
    fun everyMessageUsesFreshSaltAndNonce() {
        val first = CryptoUtils.encrypt("same text", secret)!!
        val second = CryptoUtils.encrypt("same text", secret)!!
        assertNotEquals(first, second)
    }
}
