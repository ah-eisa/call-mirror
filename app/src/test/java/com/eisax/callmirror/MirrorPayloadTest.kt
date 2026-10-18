package com.eisax.callmirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MirrorPayloadTest {

    private fun samplePayload() = MirrorPayload(
        type = MirrorPayload.TYPE_NOTIFICATION,
        appName = "Signal",
        title = "Code 123456",
        text = "Your code is 123456"
    )

    @Test
    fun roundtripPreservesFields() {
        val original = samplePayload()
        val restored = MirrorPayload.fromJson(original.toJson())
        assertNotNull(restored)
        assertEquals(original, restored!!)
    }

    @Test
    fun invalidTypeIsRejected() {
        assertNull(MirrorPayload.fromJson("""{"type":"weird","messageId":"id"}"""))
    }

    @Test
    fun missingOrBlankMessageIdIsRejected() {
        assertNull(MirrorPayload.fromJson("""{"type":"call"}"""))
        assertNull(MirrorPayload.fromJson("""{"type":"call","messageId":""}"""))
    }

    @Test
    fun malformedJsonIsRejected() {
        assertNull(MirrorPayload.fromJson("not json"))
        assertNull(MirrorPayload.fromJson("[1,2,3]"))
    }

    @Test
    fun jsonNullFieldsAreTreatedAsAbsent() {
        val raw = """{"type":"call","caller":null,"title":null,"text":null,"messageId":"m1"}"""
        val payload = MirrorPayload.fromJson(raw)
        assertNotNull(payload)
        // explicit JSON nulls (or their "null" string rendering) must normalize to absent
        assertNull(payload!!.caller)
        assertNull(payload.title)
        assertNull(payload.text)
    }

    @Test
    fun callPayloadKeepsCaller() {
        val restored = MirrorPayload.fromJson(
            MirrorPayload(type = MirrorPayload.TYPE_CALL, caller = "+20100").toJson()
        )
        assertEquals("+20100", restored?.caller)
        assertNull(restored?.text)
    }
}
