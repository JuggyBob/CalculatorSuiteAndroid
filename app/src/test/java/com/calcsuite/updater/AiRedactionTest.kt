package com.calcsuite.updater

import com.calcsuite.ai.AiClient
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Provider errors are shown to the user, so nothing that looks like a key may
 * survive the trip. This test lives in the updater test package because that
 * is the test directory this hardening batch may write to.
 */
class AiRedactionTest {

    @Test fun aKeyLikeTokenIsRemoved() {
        val cleaned = AiClient.redact("Bad request for sk-abcdefghijklmnop, try again")
        assertFalse("the key must not remain: $cleaned", cleaned.contains("sk-abcdefghijklmnop"))
        assertTrue("a marker takes its place", cleaned.contains("[the key]"))
    }

    @Test fun aLongBareTokenIsRemoved() {
        val token = "A".repeat(40)
        val cleaned = AiClient.redact("Failure: $token")
        assertFalse("the token must not remain: $cleaned", cleaned.contains(token))
        assertTrue(cleaned.contains("[hidden]"))
    }

    @Test fun aLongMessageIsCutShort() {
        val cleaned = AiClient.redact("word ".repeat(50))
        assertEquals(201, cleaned.length)
        assertTrue(cleaned.endsWith("…"))
    }

    @Test fun redactionIsIdempotent() {
        val once = AiClient.redact("sk-abcdefghijklmnop ${"B".repeat(40)}")
        assertEquals(once, AiClient.redact(once))
    }

    @Test fun aServerErrorDoesNotEchoTheProvidersKey() {
        val token = "sk-abcdefghijklmnopqrstuvwxyz"
        val (kind, message) = AiClient.classify(503, "Upstream failed for $token")
        assertEquals(ErrorKind.Unavailable, kind)
        assertFalse("a 5xx body must not leak the key: $message", message.contains(token))
        assertTrue(message.contains("[the key]"))
    }

    @Test fun aGenericErrorDoesNotEchoTheProvidersKey() {
        val token = "sk-abcdefghijklmnopqrstuvwxyz"
        val (kind, message) = AiClient.classify(400, "Rejected $token")
        assertEquals(ErrorKind.InvalidInput, kind)
        assertFalse("the echoed message must be redacted: $message", message.contains(token))
    }

    @Test fun aBlankServerErrorKeepsItsPlainMessage() {
        val (_, message) = AiClient.classify(503, "")
        assertTrue(message.contains("trouble"))
    }
}
