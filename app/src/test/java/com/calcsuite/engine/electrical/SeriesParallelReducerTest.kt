package com.calcsuite.engine.electrical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The series/parallel reduction used by the explanation.
 *
 * The reduction has to keep the nodes of the network as it combines parts;
 * otherwise it loses the branch that is in parallel with a series chain.
 */
class SeriesParallelReducerTest {

    private fun source(id: String) = CircuitComponent(
        id = id,
        kind = ComponentKind.VOLTAGE_SOURCE,
        label = id,
        voltage = BigDecimal("12"),
    )

    private fun resistor(id: String, ohms: String) = CircuitComponent(
        id = id,
        kind = ComponentKind.RESISTOR,
        label = id,
        resistance = BigDecimal(ohms),
    )

    private fun ground() = CircuitComponent(
        id = "GND",
        kind = ComponentKind.GROUND,
        label = "GND",
    )

    private fun wire(id: String, from: String, fromIndex: Int, to: String, toIndex: Int) =
        CircuitWire(id, TerminalRef(from, fromIndex), TerminalRef(to, toIndex))

    private fun reduce(circuit: Circuit): ReductionResult =
        SeriesParallelReducer(circuit, CircuitTopology(circuit)).reduce()

    @Test fun aSeriesChainWithAShuntBranchIsReduced() {
        // 100 + 200 + 300 = 600 ohms, in parallel with 600 ohms: 300 ohms.
        val circuit = Circuit(
            components = listOf(
                source("V1"),
                resistor("R1", "100"),
                resistor("R2", "200"),
                resistor("R3", "300"),
                resistor("R4", "600"),
                ground(),
            ),
            wires = listOf(
                wire("w1", "V1", 0, "R1", 0),
                wire("w2", "R1", 1, "R2", 0),
                wire("w3", "R2", 1, "R3", 0),
                wire("w4", "R3", 1, "V1", 1),
                wire("w5", "V1", 0, "R4", 0),
                wire("w6", "R4", 1, "V1", 1),
                wire("w7", "V1", 1, "GND", 0),
            ),
        )
        val reduction = reduce(circuit)
        assertTrue("the network is reduced", reduction.reduced)
        assertEquals("the shunt branch is kept", 300.0, reduction.equivalent!!, 1e-9)
        assertEquals("two series steps and one parallel step", 3, reduction.steps.size)
    }

    @Test fun largeParallelResistancesDoNotOverflow() {
        // Two 10^308 ohm parts in parallel make 5 × 10^307 ohms; multiplying
        // them first would give infinity.
        val circuit = Circuit(
            components = listOf(
                source("V1"),
                resistor("R1", "1e308"),
                resistor("R2", "1e308"),
                ground(),
            ),
            wires = listOf(
                wire("w1", "V1", 0, "R1", 0),
                wire("w2", "V1", 0, "R2", 0),
                wire("w3", "R1", 1, "V1", 1),
                wire("w4", "R2", 1, "V1", 1),
                wire("w5", "V1", 1, "GND", 0),
            ),
        )
        val reduction = reduce(circuit)
        assertTrue("the network is reduced", reduction.reduced)
        assertEquals("the parallel resistance stays finite", 5e307, reduction.equivalent!!, 1e300)
    }

    @Test fun aSeriesPairWhoseEndsFaceTheSameWayIsReduced() {
        // Both resistors start at the same junction, but that junction joins
        // nothing else, so they are in series and make 300 ohms.
        val circuit = Circuit(
            components = listOf(
                source("V1"),
                resistor("R1", "100"),
                resistor("R2", "200"),
                ground(),
            ),
            wires = listOf(
                wire("w1", "V1", 0, "R1", 1),
                wire("w2", "R1", 0, "R2", 0),
                wire("w3", "R2", 1, "V1", 1),
                wire("w4", "V1", 1, "GND", 0),
            ),
        )
        val reduction = reduce(circuit)
        assertTrue("the network is reduced", reduction.reduced)
        assertEquals("the resistances add", 300.0, reduction.equivalent!!, 1e-9)
    }
}
