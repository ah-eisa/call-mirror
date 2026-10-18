package com.eisax.callmirror

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAckTest {

    private val secret = "ABCDEFGHJKLMNPQRSTUVWXYZabcd"
    private val messageId = "4c1f0f6e-9c26-4f0e-9a5b-2a6f5d1b7e01"

    @Test
    fun ackVerifiesForMatchingMessageAndSecret() {
        val ack = CryptoUtils.createAck(secret, messageId)
        assertTrue(ack!!.startsWith("CM3A:"))
        assertTrue(CryptoUtils.verifyAck(secret, ack, messageId))
    }

    @Test
    fun ackRejectsDifferentMessageId() {
        val ack = CryptoUtils.createAck(secret, messageId)!!
        assertFalse(CryptoUtils.verifyAck(secret, ack, "some-other-id"))
    }

    @Test
    fun ackRejectsDifferentSecret() {
        val ack = CryptoUtils.createAck(secret, messageId)!!
        assertFalse(CryptoUtils.verifyAck("other-secret-16chars!!", ack, messageId))
    }

    @Test
    fun ackRejectsTamperedMac() {
        val ack = CryptoUtils.createAck(secret, messageId)!!
        val last = ack.last()
        val replacement = if (last == 'a') 'b' else 'a'
        val tampered = ack.dropLast(1) + replacement
        assertFalse(CryptoUtils.verifyAck(secret, tampered, messageId))
    }

    @Test
    fun ackRejectsMalformedInput() {
        assertFalse(CryptoUtils.verifyAck(secret, "", messageId))
        assertFalse(CryptoUtils.verifyAck(secret, "CM3A:", messageId))
        assertFalse(CryptoUtils.verifyAck(secret, "CM3A:$messageId", messageId))
        assertFalse(CryptoUtils.verifyAck(secret, "CM3A:$messageId:", messageId))
        assertFalse(CryptoUtils.verifyAck(secret, "garbage", messageId))
        assertFalse(CryptoUtils.verifyAck(secret, "CM3:whatever", messageId))
    }

    @Test
    fun ackCreationRejectsShortSecretAndBlankId() {
        assertNull(CryptoUtils.createAck("short", messageId))
        assertNull(CryptoUtils.createAck(secret, ""))
    }
}
