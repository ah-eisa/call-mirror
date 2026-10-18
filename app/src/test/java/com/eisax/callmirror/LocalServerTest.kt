package com.eisax.callmirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end local transport over loopback: real sockets, real CM3 crypto and
 * the authenticated acknowledgement handshake.
 */
class LocalServerTest {

    private val secret = "ABCDEFGHJKLMNPQRSTUVWXYZabcd"

    @Test
    fun localSendIsAcknowledgedAndDelivered() {
        val received = mutableListOf<MirrorPayload>()
        val server = startServer(received)
        try {
            val payload = MirrorPayload(
                type = MirrorPayload.TYPE_NOTIFICATION,
                appName = "Signal",
                text = "hello"
            )
            assertTrue(LocalTransport.send("127.0.0.1", server.boundPort, secret, payload))
            assertEquals(1, received.size)
            assertEquals(payload.messageId, received.first().messageId)
        } finally {
            server.close()
        }
    }

    @Test
    fun duplicateMessageIsNotReDeliveredOrAcknowledged() {
        val received = mutableListOf<MirrorPayload>()
        val server = startServer(received)
        try {
            val payload = MirrorPayload(type = MirrorPayload.TYPE_CALL, caller = "+20100")
            assertTrue(LocalTransport.send("127.0.0.1", server.boundPort, secret, payload))
            assertFalse(LocalTransport.send("127.0.0.1", server.boundPort, secret, payload))
            assertEquals(1, received.size)
        } finally {
            server.close()
        }
    }

    @Test
    fun wrongSecretSenderGetsNoDeliveryAndNoAck() {
        val received = mutableListOf<MirrorPayload>()
        val server = startServer(received)
        try {
            val payload = MirrorPayload(type = MirrorPayload.TYPE_NOTIFICATION, text = "hello")
            assertFalse(
                LocalTransport.send("127.0.0.1", server.boundPort, "other-secret-16chars!!", payload)
            )
            assertTrue(received.isEmpty())
        } finally {
            server.close()
        }
    }

    @Test
    fun unreachablePeerReportsFailure() {
        val payload = MirrorPayload(type = MirrorPayload.TYPE_NOTIFICATION, text = "hello")
        assertFalse(LocalTransport.send("127.0.0.1", 1, secret, payload))
    }

    private fun startServer(received: MutableList<MirrorPayload>): LocalServer {
        val server = LocalServer(0, secret, onPayload = { received += it })
        server.start()
        return server
    }
}
