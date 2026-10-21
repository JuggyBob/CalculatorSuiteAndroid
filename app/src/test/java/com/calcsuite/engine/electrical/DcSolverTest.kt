package com.calcsuite.engine.electrical

import com.calcsuite.core.CalcResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The DC solver, checked against circuits whose answers are known.
 *
 * The numbers here are the ones a textbook gives, worked out by hand, so
 * the solver is checked against the mathematics rather than against itself.
 */
class DcSolverTest {

    private fun dec(value: String) = BigDecimal(value)

    private fun component(
        id: String,
        kind: ComponentKind,
        label: String = id,
        resistance: String? = null,
        voltage: String? = null,
        current: String? = null,
        switchClosed: Boolean = true,
    ) = CircuitComponent(
        id = id,
        kind = kind,
        label = label,
        resistance = resistance?.let { dec(it) },
        voltage = voltage?.let { dec(it) },
        current = current?.let { dec(it) },
        switchClosed = switchClosed,
    )

    private fun wire(id: String, from: String, fromIndex: Int, to: String, toIndex: Int) =
        CircuitWire(id, TerminalRef(from, fromIndex), TerminalRef(to, toIndex))

    private fun solve(circuit: Circuit): DcSolution {
        val result = DcSolver.solve(circuit)
        assertTrue(
            "the circuit should be solved: " +
                ((result as? CalcResult.Err)?.error?.message ?: ""),
            result is CalcResult.Ok,
        )
        return (result as CalcResult.Ok).value
    }

    private fun failure(circuit: Circuit): String {
        val result = DcSolver.solve(circuit)
        assertTrue("the circuit should be refused", result is CalcResult.Err)
        return (result as CalcResult.Err).error.message
    }

    private fun assertClose(
        message: String,
        expected: Double,
        actual: Double,
        tolerance: Double = 1e-9,
    ) {
        assertEquals(message, expected, actual, tolerance)
    }

    // ------------------------------------------------------------- one resistor

    private fun oneResistor(): Circuit = Circuit(
        components = listOf(
            component("V1", ComponentKind.VOLTAGE_SOURCE, "V1", voltage = "12"),
            component("R1", ComponentKind.RESISTOR, "R1", resistance = "100"),
            component("GND", ComponentKind.GROUND, "GND"),
        ),
        wires = listOf(
            wire("w1", "V1", 0, "R1", 0),
            wire("w2", "R1", 1, "V1", 1),
            wire("w3", "V1", 1, "GND", 0),
        ),
    )

    @Test fun oneResistorGivesTheTextbookAnswer() {
        val solution = solve(oneResistor())
        assertClose("the current is 0.12 A", 0.12, solution.totalSourceCurrent, 1e-12)
        val resistor = solution.components.getValue("R1")
        assertClose("the voltage across the resistor is 12 V", 12.0, resistor.voltage, 1e-9)
        assertClose("the current through the resistor is 0.12 A", 0.12, resistor.current, 1e-12)
        assertClose("the power in the resistor is 1.44 W", 1.44, resistor.power, 1e-9)
        assertClose("the equivalent resistance is 100 ohms", 100.0, solution.equivalentResistance!!, 1e-6)
        assertClose("the power balance is zero", 0.0, solution.powerBalance, 1e-9)
    }

    // ----------------------------------------------------------------- series

    private fun series(): Circuit = Circuit(
        components = listOf(
            component("V1", ComponentKind.VOLTAGE_SOURCE, "V1", voltage = "12"),
            component("R1", ComponentKind.RESISTOR, "R1", resistance = "100"),
            component("R2", ComponentKind.RESISTOR, "R2", resistance = "200"),
            component("GND", ComponentKind.GROUND, "GND"),
        ),
        wires = listOf(
            wire("w1", "V1", 0, "R1", 0),
            wire("w2", "R1", 1, "R2", 0),
            wire("w3", "R2", 1, "V1", 1),
            wire("w4", "V1", 1, "GND", 0),
        ),
    )

    @Test fun twoResistorsInSeriesGiveTheTextbookAnswer() {
        val solution = solve(series())
        assertClose("the current is 0.04 A", 0.04, solution.totalSourceCurrent, 1e-12)
        val r1 = solution.components.getValue("R1")
        val r2 = solution.components.getValue("R2")
        assertClose("the same current flows through R1", 0.04, r1.current, 1e-12)
        assertClose("the same current flows through R2", 0.04, r2.current, 1e-12)
        assertClose("the drop across R1 is 4 V", 4.0, r1.voltage, 1e-9)
        assertClose("the drop across R2 is 8 V", 8.0, r2.voltage, 1e-9)
        assertClose("R1 takes 0.16 W", 0.16, r1.power, 1e-9)
        assertClose("R2 takes 0.32 W", 0.32, r2.power, 1e-9)
        assertClose("the total power is 0.48 W", 0.48, solution.totalPowerDissipated, 1e-9)
        assertClose("the source supplies 0.48 W", 0.48, solution.totalPowerSupplied, 1e-9)
        assertClose("the power balance is zero", 0.0, solution.powerBalance, 1e-9)
        assertClose(
            "the equivalent resistance is 300 ohms",
            300.0,
            solution.equivalentResistance!!,
            1e-6,
        )
        // The drops add up to the source.
        assertClose(
            "the drops add up to the source",
            12.0,
            r1.voltage + r2.voltage,
            1e-9,
        )
    }

    // ---------------------------------------------------------------- parallel

    private fun parallel(): Circuit = Circuit(
        components = listOf(
            component("V1", ComponentKind.VOLTAGE_SOURCE, "V1", voltage = "12"),
            component("R1", ComponentKind.RESISTOR, "R1", resistance = "100"),
            component("R2", ComponentKind.RESISTOR, "R2", resistance = "200"),
            component("GND", ComponentKind.GROUND, "GND"),
        ),
        wires = listOf(
            wire("w1", "V1", 0, "R1", 0),
            wire("w2", "V1", 0, "R2", 0),
            wire("w3", "R1", 1, "V1", 1),
            wire("w4", "R2", 1, "V1", 1),
            wire("w5", "V1", 1, "GND", 0),
        ),
    )

    @Test fun twoResistorsInParallelGiveTheTextbookAnswer() {
        val solution = solve(parallel())
        assertClose("the total current is 0.18 A", 0.18, solution.totalSourceCurrent, 1e-12)
        val r1 = solution.components.getValue("R1")
        val r2 = solution.components.getValue("R2")
        assertClose("the branch current in R1 is 0.12 A", 0.12, r1.current, 1e-12)
        assertClose("the branch current in R2 is 0.06 A", 0.06, r2.current, 1e-12)
        assertClose("both branches have the source voltage", 12.0, r1.voltage, 1e-9)
        assertClose("both branches have the source voltage", 12.0, r2.voltage, 1e-9)
        assertClose("R1 takes 1.44 W", 1.44, r1.power, 1e-9)
        assertClose("R2 takes 0.72 W", 0.72, r2.power, 1e-9)
        assertClose("the total power is 2.16 W", 2.16, solution.totalPowerDissipated, 1e-9)
        assertClose(
            "the equivalent resistance is 66.667 ohms",
            200.0 / 3.0,
            solution.equivalentResistance!!,
            1e-6,
        )
        // The branch currents add up.
        assertClose(
            "the branch currents add up to the total",
            solution.totalSourceCurrent,
            r1.current + r2.current,
            1e-9,
        )
    }

    // ------------------------------------------------------------------ mixed

    private fun mixed(): Circuit = Circuit(
        components = listOf(
            component("V1", ComponentKind.VOLTAGE_SOURCE, "V1", voltage = "12"),
            component("R1", ComponentKind.RESISTOR, "R1", resistance = "100"),
            component("R2", ComponentKind.RESISTOR, "R2", resistance = "200"),
            component("R3", ComponentKind.RESISTOR, "R3", resistance = "300"),
            component("GND", ComponentKind.GROUND, "GND"),
        ),
        wires = listOf(
            wire("w1", "V1", 0, "R1", 0),
            wire("w2", "R1", 1, "R2", 0),
            wire("w3", "R1", 1, "R3", 0),
            wire("w4", "R2", 1, "V1", 1),
            wire("w5", "R3", 1, "V1", 1),
            wire("w6", "V1", 1, "GND", 0),
        ),
    )

    @Test fun aMixedNetworkGivesTheTextbookAnswer() {
        val solution = solve(mixed())
        val r1 = solution.components.getValue("R1")
        val r2 = solution.components.getValue("R2")
        val r3 = solution.components.getValue("R3")

        assertClose(
            "the total current is 0.05454545 A",
            12.0 / 220.0,
            solution.totalSourceCurrent,
            1e-12,
        )
        assertClose("the drop across R1 is 5.454545 V", 12.0 * 100.0 / 220.0, r1.voltage, 1e-9)
        assertClose("the parallel part has 6.545455 V", 12.0 * 120.0 / 220.0, r2.voltage, 1e-9)
        assertClose("the 200 ohm branch carries 0.03272727 A", 6.545454545 / 200.0, r2.current, 1e-11)
        assertClose("the 300 ohm branch carries 0.02181818 A", 6.545454545 / 300.0, r3.current, 1e-11)
        assertClose(
            "the total power is 0.65454545 W",
            12.0 * 12.0 / 220.0,
            solution.totalPowerDissipated,
            1e-9,
        )
        assertClose("the power balance is zero", 0.0, solution.powerBalance, 1e-9)
        // The branch currents add up to the current in the series part.
        assertClose(
            "the branch currents add up",
            r1.current,
            r2.current + r3.current,
            1e-11,
        )
    }

    // ----------------------------------------------------------------- switch

    private fun withSwitch(closed: Boolean): Circuit = Circuit(
        components = listOf(
            component("V1", ComponentKind.VOLTAGE_SOURCE, "V1", voltage = "12"),
            component("R1", ComponentKind.RESISTOR, "R1", resistance = "100"),
            component("S1", ComponentKind.SWITCH, "S1", switchClosed = closed),
            component("GND", ComponentKind.GROUND, "GND"),
        ),
        wires = listOf(
            wire("w1", "V1", 0, "R1", 0),
            wire("w2", "R1", 1, "S1", 0),
            wire("w3", "S1", 1, "V1", 1),
            wire("w4", "V1", 1, "GND", 0),
        ),
    )

    @Test fun anOpenSwitchStopsTheCurrent() {
        val solution = solve(withSwitch(closed = false))
        assertClose("the current is zero", 0.0, solution.totalSourceCurrent, 1e-12)
        val resistor = solution.components.getValue("R1")
        assertClose("no current flows through the resistor", 0.0, resistor.current, 1e-12)
        // The whole source voltage is across the open switch.
        val switch = solution.components.getValue("S1")
        assertClose("the switch carries the source voltage", 12.0, switch.voltage, 1e-9)
    }

    @Test fun aClosedSwitchLetsTheCurrentFlow() {
        val solution = solve(withSwitch(closed = true))
        assertClose("the current is 0.12 A", 0.12, solution.totalSourceCurrent, 1e-12)
        val switch = solution.components.getValue("S1")
        assertClose("there is no voltage across a closed switch", 0.0, switch.voltage, 1e-9)
    }

    // --------------------------------------------------------------- failures

    @Test fun aCircuitWithNoGroundIsRefused() {
        val circuit = Circuit(
            components = listOf(
                component("V1", ComponentKind.VOLTAGE_SOURCE, "V1", voltage = "12"),
                component("R1", ComponentKind.RESISTOR, "R1", resistance = "100"),
            ),
            wires = listOf(wire("w1", "V1", 0, "R1", 0), wire("w2", "R1", 1, "V1", 1)),
        )
        val message = failure(circuit)
        assertTrue("the ground should be asked for: $message", message.contains("ground"))
    }

    @Test fun aResistorWithNoResistanceIsRefused() {
        val circuit = oneResistor().let { original ->
            original.copy(
                components = original.components.map {
                    if (it.id == "R1") it.copy(resistance = null) else it
                },
            )
        }
        val message = failure(circuit)
        assertTrue("the resistance should be asked for: $message", message.contains("R1"))
    }

    @Test fun aZeroResistanceIsRefused() {
        val circuit = oneResistor().let { original ->
            original.copy(
                components = original.components.map {
                    if (it.id == "R1") it.copy(resistance = BigDecimal.ZERO) else it
                },
            )
        }
        val message = failure(circuit)
        assertTrue("the resistance should be asked for: $message", message.contains("positive"))
    }

    @Test fun aShortCircuitedSourceIsRefused() {
        val circuit = Circuit(
            components = listOf(
                component("V1", ComponentKind.VOLTAGE_SOURCE, "V1", voltage = "12"),
                component("GND", ComponentKind.GROUND, "GND"),
            ),
            wires = listOf(wire("w1", "V1", 0, "V1", 1), wire("w2", "V1", 1, "GND", 0)),
        )
        val message = failure(circuit)
        assertTrue("the short circuit should be named: $message", message.contains("short"))
    }

    @Test fun anEmptyCircuitIsRefused() {
        val message = failure(Circuit())
        assertTrue("the empty circuit should be named: $message", message.isNotBlank())
    }

    @Test fun aFloatingPartIsRefused() {
        // The resistor is joined to the source by a wire, but the other end
        // of the source is not joined to the ground, so the voltages float.
        val circuit = Circuit(
            components = listOf(
                component("V1", ComponentKind.VOLTAGE_SOURCE, "V1", voltage = "12"),
                component("R1", ComponentKind.RESISTOR, "R1", resistance = "100"),
                component("GND", ComponentKind.GROUND, "GND"),
            ),
            wires = listOf(wire("w1", "V1", 0, "R1", 0)),
        )
        val message = failure(circuit)
        assertTrue("the floating part should be named: $message", message.isNotBlank())
    }

    // ------------------------------------------------------------- the checks

    @Test fun theCurrentFollowsKirchhoffsCurrentLawAtEveryNode() {
        for (circuit in listOf(oneResistor(), series(), parallel(), mixed())) {
            val solution = solve(circuit)
            val topology = CircuitTopology(circuit)
            for (node in topology.nodeCount - 1 downTo 0) {
                if (node == topology.groundNode) continue
                var leaving = 0.0
                for (component in circuit.components) {
                    val terminals = component.terminals
                    val result = solution.components[component.id] ?: continue
                    val nodeA = terminals.firstOrNull()?.let { topology.nodeOf(it) }
                    val nodeB = terminals.getOrNull(1)?.let { topology.nodeOf(it) }
                    // The current through the component runs from its first
                    // terminal to its second.
                    if (nodeA == node) leaving += result.current
                    if (nodeB == node) leaving -= result.current
                }
                assertClose(
                    "the current balances at the node $node",
                    0.0,
                    leaving,
                    1e-9,
                )
            }
        }
    }

    @Test fun thePowerIsConserved() {
        for (circuit in listOf(oneResistor(), series(), parallel(), mixed())) {
            val solution = solve(circuit)
            assertClose(
                "the power supplied equals the power taken",
                0.0,
                solution.powerBalance,
                1e-9,
            )
        }
    }
}
