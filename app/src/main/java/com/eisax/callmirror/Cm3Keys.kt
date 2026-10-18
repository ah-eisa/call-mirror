package com.eisax.callmirror

import java.security.MessageDigest
import java.util.Collections
import java.util.LinkedHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Protocol CM3 key schedule.
 *
 * The pairing secret is expanded once — HKDF-SHA256 with a fixed salt, then
 * per-domain expansion — into domain-separated keys and cached. Message hot
 * paths therefore perform no key stretching at all: only a single AES-GCM
 * operation per message, instead of a fresh 210k-iteration PBKDF2.
 *
 * Purely JVM resident so it stays unit-testable.
 */
class Cm3Keys private constructor(masterSecret: String) {

    /** AES-256 key protecting CM3 payload envelopes. */
    val payloadKey: SecretKeySpec

    /** HMAC-SHA256 key authenticating CM3 local acknowledgements. */
    val ackKey: SecretKeySpec

    init {
        val extract = Mac.getInstance("HmacSHA256")
        extract.init(SecretKeySpec(HKDF_SALT.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val pseudorandomKey = extract.doFinal(masterSecret.toByteArray(Charsets.UTF_8))
        payloadKey = SecretKeySpec(expand(pseudorandomKey, DOMAIN_PAYLOAD), "AES")
        ackKey = SecretKeySpec(expand(pseudorandomKey, DOMAIN_ACK), "HmacSHA256")
    }

    companion object {
        private const val HKDF_SALT = "callmirror-cm3-hkdf-salt-v1"
        private const val DOMAIN_PAYLOAD = "callmirror-cm3-payload-v1"
        private const val DOMAIN_ACK = "callmirror-cm3-ack-v1"
        private const val CACHE_CAPACITY = 4

        // Keyed by a digest of the secret, so the plaintext pairing secret is
        // never stored a second time in memory.
        private val cache = Collections.synchronizedMap(
            object : LinkedHashMap<String, Cm3Keys>(CACHE_CAPACITY, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cm3Keys>?): Boolean =
                    size > CACHE_CAPACITY
            }
        )

        /** Returns the cached key set for [pairingSecret], deriving it on first use. */
        fun forSecret(pairingSecret: String): Cm3Keys {
            val cacheKey = sha256Hex(pairingSecret)
            synchronized(cache) {
                cache[cacheKey]?.let { return it }
                return Cm3Keys(pairingSecret).also { cache[cacheKey] = it }
            }
        }

        // HKDF-Expand, single 32-byte block: T(1) = HMAC-SHA256(PRK, info || 0x01).
        private fun expand(pseudorandomKey: ByteArray, info: String): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(pseudorandomKey, "HmacSHA256"))
            mac.update(info.toByteArray(Charsets.UTF_8))
            mac.update(0x01.toByte())
            return mac.doFinal()
        }

        private fun sha256Hex(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
