package com.calcsuite.engine.electrical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Saving a circuit and reading it back.
 *
 * The text has to keep everything about the circuit: the parts, their
 * values, where they are drawn, and the wires between them.
 */
class CircuitCodecTest {

    @Test fun aCircuitIsWrittenAndReadBack() {
        val original = CircuitExamples.byId("mixed")!!.circuit
        val text = CircuitCodec.encode(original)
        val read = CircuitCodec.decode(text)
        assertNotNull("the circuit is read back", read)
        assertEquals("the parts are the same", original.components, read!!.components)
        assertEquals("the wires are the same", original.wires, read.wires)
    }

    @Test fun everySampleSurvivesTheJourney() {
        for (example in CircuitExamples.all) {
            val read = CircuitCodec.decode(CircuitCodec.encode(example.circuit))
            assertNotNull("the sample ${example.title} is read back", read)
            assertEquals(
                "the parts of ${example.title} are the same",
                example.circuit.components,
                read!!.components,
            )
            assertEquals(
                "the wires of ${example.title} are the same",
                example.circuit.wires,
                read.wires,
            )
        }
    }

    @Test fun theValuesOfThePartsAreKept() {
        val circuit = Circuit(
            components = listOf(
                CircuitComponent(
                    id = "R1",
                    kind = ComponentKind.RESISTOR,
                    label = "R1",
                    resistance = java.math.BigDecimal("220"),
                    powerRating = java.math.BigDecimal("0.25"),
                ),
                CircuitComponent(
                    id = "V1",
                    kind = ComponentKind.VOLTAGE_SOURCE,
                    label = "V1",
                    voltage = java.math.BigDecimal("12"),
                ),
            ),
        )
        val read = CircuitCodec.decode(CircuitCodec.encode(circuit))!!
        assertEquals(
            "the resistance is kept",
            java.math.BigDecimal("220"),
            read.component("R1")!!.resistance,
        )
        assertEquals(
            "the power rating is kept",
            java.math.BigDecimal("0.25"),
            read.component("R1")!!.powerRating,
        )
        assertEquals(
            "the voltage is kept",
            java.math.BigDecimal("12"),
            read.component("V1")!!.voltage,
        )
    }

    @Test fun aNameWithTheSeparatorInItIsKept() {
        val circuit = Circuit(
            components = listOf(
                CircuitComponent(
                    id = "R1",
                    kind = ComponentKind.RESISTOR,
                    label = "R1 | special",
                    resistance = java.math.BigDecimal("100"),
                ),
            ),
        )
        val read = CircuitCodec.decode(CircuitCodec.encode(circuit))
        assertNotNull("the circuit is read back", read)
        assertEquals("the name is kept", "R1 | special", read!!.component("R1")!!.label)
    }

    @Test fun whereThePartsAreDrawnIsKept() {
        val circuit = Circuit(
            components = listOf(
                CircuitComponent(
                    id = "R1",
                    kind = ComponentKind.RESISTOR,
                    label = "R1",
                    x = 120.0,
                    y = -40.0,
                    rotation = 2,
                    resistance = java.math.BigDecimal("100"),
                ),
            ),
        )
        val read = CircuitCodec.decode(CircuitCodec.encode(circuit))!!
        val component = read.component("R1")!!
        assertEquals("the place is kept", 120.0, component.x, 0.0)
        assertEquals("the place is kept", -40.0, component.y, 0.0)
        assertEquals("the turn is kept", 2, component.rotation)
    }

    @Test fun anOpenSwitchStaysOpen() {
        val circuit = Circuit(
            components = listOf(
                CircuitComponent(
                    id = "S1",
                    kind = ComponentKind.SWITCH,
                    label = "S1",
                    switchClosed = false,
                ),
            ),
        )
        val read = CircuitCodec.decode(CircuitCodec.encode(circuit))!!
        assertEquals("the switch is still open", false, read.component("S1")!!.switchClosed)
    }

    @Test fun nothingIsReadFromRubbish() {
        assertNull("there is nothing there", CircuitCodec.decode(""))
        assertNull("there is nothing there", CircuitCodec.decode("   "))
        assertNull("the version is missing", CircuitCodec.decode("c|R1|RESISTOR|R1"))
        assertNull("the version is wrong", CircuitCodec.decode("v99"))
        assertNull("the part is unknown", CircuitCodec.decode("v1\nc|R1|FLUB|R1|0|0|0|||||true|"))
        assertNull("the line is short", CircuitCodec.decode("v1\nc|R1|RESISTOR"))
    }

    @Test fun aCircuitIsDescribedInWords() {
        val summary = CircuitCodec.summary(CircuitExamples.byId("series")!!.circuit)
        assertTrue("the resistors are counted: $summary", summary.contains("2 resistors"))
        assertTrue("the source is counted: $summary", summary.contains("1 source"))
    }

    @Test fun aCircuitWithTooManyPartsOrWiresIsNotRead() {
        fun circuitOf(componentCount: Int, wireCount: Int): Circuit {
            val components = (0 until componentCount).map {
                CircuitComponent(
                    id = "C$it",
                    kind = ComponentKind.RESISTOR,
                    label = "C$it",
                    resistance = java.math.BigDecimal("100"),
                )
            }
            val wires = (0 until wireCount).map {
                CircuitWire("w$it", TerminalRef("C0", 0), TerminalRef("C0", 1))
            }
            return Circuit(components, wires)
        }

        val tooManyParts = CircuitCodec.encode(circuitOf(CircuitCodec.MAX_COMPONENTS + 1, 0))
        assertNull("a circuit over the part limit is refused", CircuitCodec.decode(tooManyParts))

        val tooManyWires = CircuitCodec.encode(circuitOf(1, CircuitCodec.MAX_WIRES + 1))
        assertNull("a circuit over the wire limit is refused", CircuitCodec.decode(tooManyWires))

        val atTheLimits = CircuitCodec.encode(
            circuitOf(CircuitCodec.MAX_COMPONENTS, CircuitCodec.MAX_WIRES),
        )
        assertNotNull("a circuit at both limits is read", CircuitCodec.decode(atTheLimits))
    }

    @Test fun hostileNumbersAreRefusedAndOrdinaryOnesSurvive() {
        fun withResistance(text: String) = Circuit(
            components = listOf(
                CircuitComponent(
                    id = "R1",
                    kind = ComponentKind.RESISTOR,
                    label = "R1",
                    resistance = java.math.BigDecimal(text),
                ),
            ),
        )

        for (hostile in listOf("1E+1000000000", "1E-1000000000", "1E+100000", "1E+400")) {
            val encoded = CircuitCodec.encode(withResistance(hostile))
            assertTrue("the encoded value stays short: $encoded", encoded.length < 200)
            assertNull("the hostile value $hostile is refused", CircuitCodec.decode(encoded))
        }

        for (normal in listOf("220", "0.25", "12", "1E+3", "0.000001")) {
            val value = java.math.BigDecimal(normal)
            val read = CircuitCodec.decode(CircuitCodec.encode(withResistance(normal)))
            assertNotNull("the ordinary value $normal is read", read)
            assertEquals(
                "the ordinary value $normal is kept",
                0,
                value.compareTo(read!!.component("R1")!!.resistance),
            )
        }
    }

    @Test fun aPartWithNoValueYetStillSurvives() {
        // A part can be placed before it is valued, and some parts never have
        // a value at all; saving and restoring must keep those empty fields.
        val circuit = Circuit(
            components = listOf(
                CircuitComponent(id = "R1", kind = ComponentKind.RESISTOR, label = "R1"),
                CircuitComponent(id = "S1", kind = ComponentKind.SWITCH, label = "S1"),
                CircuitComponent(id = "GND", kind = ComponentKind.GROUND, label = "Ground"),
            ),
        )
        val read = CircuitCodec.decode(CircuitCodec.encode(circuit))
        assertNotNull("the circuit is read back", read)
        assertEquals("the parts are kept", circuit.components, read!!.components)
        assertNull("the resistor still has no value", read.component("R1")!!.resistance)
        assertNull("the power rating is still empty", read.component("R1")!!.powerRating)
    }

    @Test fun anUnparseableNumberRefusesTheWholeCircuit() {
        val text = "v1\nc|R1|RESISTOR|R1|0|0|0|not-a-number|||true|"
        assertNull("bad text is not quietly downgraded to an empty value", CircuitCodec.decode(text))
    }
}
