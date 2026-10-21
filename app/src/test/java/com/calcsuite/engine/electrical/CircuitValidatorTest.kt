package com.calcsuite.engine.electrical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The four states a circuit can stand in.
 *
 * Each of them needs something different from the user, so they have to be
 * told apart and the message has to say what to do.
 */
class CircuitValidatorTest {

    private fun component(
        id: String,
        kind: ComponentKind,
        resistance: String? = null,
        voltage: String? = null,
    ) = CircuitComponent(
        id = id,
        kind = kind,
        label = id,
        resistance = resistance?.let { BigDecimal(it) },
        voltage = voltage?.let { BigDecimal(it) },
    )

    private fun wire(id: String, from: String, fromIndex: Int, to: String, toIndex: Int) =
        CircuitWire(id, TerminalRef(from, fromIndex), TerminalRef(to, toIndex))

    private fun goodCircuit() = Circuit(
        components = listOf(
            component("V1", ComponentKind.VOLTAGE_SOURCE, voltage = "12"),
            component("R1", ComponentKind.RESISTOR, resistance = "100"),
            component("GND", ComponentKind.GROUND),
        ),
        wires = listOf(
            wire("w1", "V1", 0, "R1", 0),
            wire("w2", "R1", 1, "V1", 1),
            wire("w3", "V1", 1, "GND", 0),
        ),
    )

    @Test fun anEmptyCircuitIsEmpty() {
        val assessment = CircuitValidator.assess(Circuit())
        assertEquals(CircuitState.EMPTY, assessment.state)
        assertNotNull("there is a message", assessment.message)
        assertNotNull("there is a suggestion", assessment.suggestion)
        assertNull("there is no solution", assessment.solution)
    }

    @Test fun aFinishedCircuitIsSolved() {
        val assessment = CircuitValidator.assess(goodCircuit())
        assertEquals(CircuitState.SOLVED, assessment.state)
        assertTrue("the circuit is solved", assessment.isSolved)
        assertNotNull("there is a solution", assessment.solution)
        assertNotNull("there is an explanation", assessment.explanation)
    }

    @Test fun aCircuitWithNoGroundIsIncomplete() {
        val circuit = goodCircuit().let { original ->
            original.copy(
                components = original.components.filter { it.kind != ComponentKind.GROUND },
                wires = original.wires.filter { it.to.componentId != "GND" },
            )
        }
        val assessment = CircuitValidator.assess(circuit)
        assertEquals(CircuitState.INCOMPLETE, assessment.state)
        assertTrue(
            "the ground is asked for: ${assessment.message}",
            assessment.message!!.contains("ground"),
        )
    }

    @Test fun aPartThatHangsIsIncomplete() {
        val circuit = goodCircuit().let { original ->
            original.copy(
                components = original.components + component("R2", ComponentKind.RESISTOR, resistance = "50"),
            )
        }
        val assessment = CircuitValidator.assess(circuit)
        assertEquals(CircuitState.INCOMPLETE, assessment.state)
        assertTrue(
            "the part is named: ${assessment.message}",
            assessment.message!!.contains("R2"),
        )
        assertEquals("the part is pointed at", "R2", assessment.componentId)
    }

    @Test fun aPartWithNoValueIsIncomplete() {
        val circuit = goodCircuit().let { original ->
            original.copy(
                components = original.components.map {
                    if (it.id == "R1") it.copy(resistance = null) else it
                },
            )
        }
        val assessment = CircuitValidator.assess(circuit)
        assertEquals(CircuitState.INCOMPLETE, assessment.state)
        assertTrue(
            "the resistance is asked for: ${assessment.message}",
            assessment.message!!.contains("R1"),
        )
    }

    @Test fun aPartOfZeroResistanceIsInvalid() {
        val circuit = goodCircuit().let { original ->
            original.copy(
                components = original.components.map {
                    if (it.id == "R1") it.copy(resistance = BigDecimal.ZERO) else it
                },
            )
        }
        val assessment = CircuitValidator.assess(circuit)
        assertEquals(CircuitState.INVALID, assessment.state)
        assertTrue(
            "the reason is given: ${assessment.message}",
            assessment.message!!.contains("greater than zero"),
        )
    }

    @Test fun aShortCircuitIsInvalid() {
        val circuit = Circuit(
            components = listOf(
                component("V1", ComponentKind.VOLTAGE_SOURCE, voltage = "12"),
                component("GND", ComponentKind.GROUND),
            ),
            wires = listOf(
                wire("w1", "V1", 0, "V1", 1),
                wire("w2", "V1", 1, "GND", 0),
            ),
        )
        val assessment = CircuitValidator.assess(circuit)
        assertEquals(CircuitState.INVALID, assessment.state)
        assertTrue(
            "the short circuit is named: ${assessment.message}",
            assessment.message!!.contains("short"),
        )
    }

    @Test fun aBrokenWireIsIncomplete() {
        val circuit = goodCircuit().let { original ->
            original.copy(wires = original.wires + wire("w4", "R1", 0, "R9", 0))
        }
        val assessment = CircuitValidator.assess(circuit)
        assertEquals(CircuitState.INCOMPLETE, assessment.state)
        assertTrue(
            "the wire is named: ${assessment.message}",
            assessment.message!!.contains("wire"),
        )
    }

    @Test fun everyStateThatNeedsSomethingToSaySaysIt() {
        val assessments = listOf(
            CircuitValidator.assess(Circuit()),
            CircuitValidator.assess(goodCircuit().let { original ->
                original.copy(
                    components = original.components.filter { it.kind != ComponentKind.GROUND },
                    wires = original.wires.filter { it.to.componentId != "GND" },
                )
            }),
        )
        for (assessment in assessments) {
            assertTrue("there is a message", !assessment.message.isNullOrBlank())
            assertTrue("there is a suggestion", !assessment.suggestion.isNullOrBlank())
        }
    }
}
