package com.eisax.callmirror

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cloud publish contract against a local mock relay: target path, relay hints,
 * and a CM3 envelope that decrypts back to the original payload.
 */
class CloudRelayPublishTest {

    private val secret = "ABCDEFGHJKLMNPQRSTUVWXYZabcd"

    private fun samplePayload() = MirrorPayload(
        type = MirrorPayload.TYPE_NOTIFICATION,
        appName = "Signal",
        title = "Code",
        text = "hello"
    )

    @Test
    fun publishPostsEncryptedEnvelopeToTopicPath() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200))
        server.start()
        try {
            val payload = samplePayload()
            assertTrue(CloudRelay.publishNow(baseUrl(server), OkHttpClient(), secret, payload))

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/${CryptoUtils.topicFor(secret)}", request.path)
            assertEquals("no", request.getHeader("Cache"))
            assertEquals("no", request.getHeader("Firebase"))

            val decrypted = CryptoUtils.decrypt(request.body.readUtf8(), secret)
            assertNotNull(decrypted)
            assertEquals(payload, MirrorPayload.fromJson(decrypted!!))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun publishReportsHttpFailure() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(500))
        server.start()
        try {
            assertFalse(CloudRelay.publishNow(baseUrl(server), OkHttpClient(), secret, samplePayload()))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun publishIsSkippedForShortSecret() {
        val server = MockWebServer()
        server.start()
        try {
            assertFalse(CloudRelay.publishNow(baseUrl(server), OkHttpClient(), "short", samplePayload()))
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    private fun baseUrl(server: MockWebServer): String = server.url("/").toString().trimEnd('/')
}
