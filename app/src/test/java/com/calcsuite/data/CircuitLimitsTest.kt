package com.calcsuite.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CircuitLimitsTest {

    @Test fun realToolIdsAreAccepted() {
        for (id in listOf(
            "other.electrical_circuits", "algebra.gcf_lcm", "a", "a_1.b",
            "x".repeat(64),
        )) {
            assertTrue(id, CircuitLimits.isValidToolId(id))
            assertEquals("circuit_$id", CircuitLimits.circuitKey(id))
        }
    }

    @Test fun malformedToolIdsAreRejected() {
        for (id in listOf(
            "", "x".repeat(65), "Upper.case", "with-hyphen", "with/slash",
            "with space", "with:colon", "with#hash", "with\nnewline",
        )) {
            assertFalse(id, CircuitLimits.isValidToolId(id))
        }
    }

    @Test fun encodedLengthLimitIsInclusive() {
        assertTrue(CircuitLimits.encodedWithinLimit("x".repeat(CircuitLimits.MAX_ENCODED_LENGTH)))
        assertFalse(CircuitLimits.encodedWithinLimit("x".repeat(CircuitLimits.MAX_ENCODED_LENGTH + 1)))
    }
}
